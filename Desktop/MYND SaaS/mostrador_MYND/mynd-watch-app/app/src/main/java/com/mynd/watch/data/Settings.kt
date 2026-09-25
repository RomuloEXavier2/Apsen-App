package com.mynd.watch.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("mynd_settings")

/** Configurações persistentes. Guarda só o token da Bambu (nunca a senha). */
class Settings(private val ctx: Context) {
    companion object {
        val WATCH_MAC = stringPreferencesKey("watch_mac")
        val BAMBU_TOKEN = stringPreferencesKey("bambu_token")
        val BAMBU_UID = stringPreferencesKey("bambu_uid")
        val BAMBU_SERIAL = stringPreferencesKey("bambu_serial")
        val BAMBU_ENABLED = booleanPreferencesKey("bambu_enabled")
        val FORWARD_NOTIFS = booleanPreferencesKey("forward_notifs")
    }

    suspend fun get(k: androidx.datastore.preferences.core.Preferences.Key<String>): String? =
        ctx.store.data.map { it[k] }.first()

    suspend fun getBool(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>, def: Boolean = false): Boolean =
        ctx.store.data.map { it[k] ?: def }.first()

    suspend fun set(k: androidx.datastore.preferences.core.Preferences.Key<String>, v: String?) {
        ctx.store.edit { if (v == null) it.remove(k) else it[k] = v }
    }

    suspend fun setBool(k: androidx.datastore.preferences.core.Preferences.Key<Boolean>, v: Boolean) {
        ctx.store.edit { it[k] = v }
    }
}

/** Log em memória para a tela "Logs" do app (além do Logcat). */
object AppLog {
    private val lines = ArrayDeque<String>()
    val listeners = mutableListOf<(List<String>) -> Unit>()
    @Synchronized fun add(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(System.currentTimeMillis())
        lines.addFirst("$t  $msg"); while (lines.size > 300) lines.removeLast()
        android.util.Log.i("MYND", msg)
        val snap = lines.toList(); listeners.forEach { it(snap) }
    }
    @Synchronized fun snapshot() = lines.toList()
}
