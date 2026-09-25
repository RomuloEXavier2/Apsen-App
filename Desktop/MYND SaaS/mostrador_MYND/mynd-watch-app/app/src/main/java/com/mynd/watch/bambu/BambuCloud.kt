package com.mynd.watch.bambu

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/**
 * Acesso à NUVEM da Bambu (funciona na rua, só com o celular). API NÃO oficial.
 * Referência para conferir/ajustar endpoints: projeto ha-bambulab (pybambu/bambu_cloud.py) no GitHub.
 *
 * Fluxo:
 *  1) login(email, senha) -> accessToken  |  ou "verifyCode": pedir código por e-mail -> loginWithCode()
 *  2) uid() -> usuário MQTT = "u_<uid>", senha MQTT = accessToken
 *  3) MQTT TLS us.mqtt.bambulab.com:8883, assina device/<serial>/report, publica pushall em device/<serial>/request
 * Guardar SÓ o token (DataStore), nunca a senha.
 */
class BambuCloud(private val region: String = "us") {

    private val tag = "MYND/BambuCloud"
    private val http = OkHttpClient()
    private val api = "https://api.bambulab.com"
    private val json = "application/json".toMediaType()

    sealed class LoginResult {
        data class Token(val accessToken: String) : LoginResult()
        object NeedsEmailCode : LoginResult()
        data class Error(val msg: String) : LoginResult()
    }

    private suspend fun post(path: String, body: JSONObject, token: String? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val rb = Request.Builder().url(api + path).post(body.toString().toRequestBody(json))
                .header("User-Agent", "bambu_network_agent/01.09.05.01")
            token?.let { rb.header("Authorization", "Bearer $it") }
            http.newCall(rb.build()).execute().use { it.code to (it.body?.string() ?: "") }
        }

    suspend fun login(email: String, password: String): LoginResult {
        val (code, txt) = post("/v1/user-service/user/login", JSONObject().put("account", email).put("password", password))
        Log.i(tag, "login http=$code")
        val j = runCatching { JSONObject(txt) }.getOrNull() ?: return LoginResult.Error("HTTP $code: $txt")
        val tok = j.optString("accessToken")
        return when {
            tok.isNotEmpty() -> LoginResult.Token(tok)
            j.optString("loginType") == "verifyCode" -> {
                post("/v1/user-service/user/sendemail/code", JSONObject().put("email", email).put("type", "codeLogin"))
                LoginResult.NeedsEmailCode
            }
            else -> LoginResult.Error(txt.take(200))   // TODO(agente): tratar "tfa" (2FA) se aparecer
        }
    }

    suspend fun loginWithCode(email: String, code: String): LoginResult {
        val (http, txt) = post("/v1/user-service/user/login", JSONObject().put("account", email).put("code", code))
        val tok = runCatching { JSONObject(txt).optString("accessToken") }.getOrNull().orEmpty()
        return if (tok.isNotEmpty()) LoginResult.Token(tok) else LoginResult.Error("HTTP $http: ${txt.take(200)}")
    }

    suspend fun uid(token: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$api/v1/design-user-service/my/preference")
            .header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { JSONObject(it.body!!.string()).get("uid").toString() }
    }

    // ---------------- MQTT ----------------
    private var client: Mqtt3AsyncClient? = null

    /** Conecta e chama onReport(jsonString) para cada mensagem da impressora. */
    fun connect(uid: String, token: String, serial: String, onReport: (String) -> Unit, onState: (String) -> Unit) {
        val c = MqttClient.builder().useMqttVersion3()
            .identifier("mynd-" + UUID.randomUUID().toString().take(8))
            .serverHost("$region.mqtt.bambulab.com").serverPort(8883).sslWithDefaultConfig()
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener { onState("conectado"); pushall(serial) }
            .addDisconnectedListener { onState("desconectado: ${it.cause.message}") }
            .buildAsync()
        client = c
        c.connectWith().simpleAuth().username("u_$uid").password(token.toByteArray()).applySimpleAuth()
            .send().whenComplete { _, e ->
                if (e != null) { onState("falha MQTT: ${e.message}"); return@whenComplete }
                c.subscribeWith().topicFilter("device/$serial/report").qos(MqttQos.AT_MOST_ONCE)
                    .callback { onReport(String(it.payloadAsBytes)) }.send()
                pushall(serial)
            }
    }

    fun pushall(serial: String) {
        client?.publishWith()?.topic("device/$serial/request")
            ?.payload("""{"pushing":{"sequence_id":"0","command":"pushall"}}""".toByteArray())?.send()
    }

    fun disconnect() { client?.disconnect(); client = null }
}
