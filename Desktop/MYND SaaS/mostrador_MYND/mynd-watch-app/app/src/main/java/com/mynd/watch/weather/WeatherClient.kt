package com.mynd.watch.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Temperatura externa atual pela Open-Meteo (gratis, sem chave de API).
 * A posicao vem da ultima localizacao conhecida do celular (permissao de localizacao aproximada);
 * sem permissao/posicao usa a ultima salva e, na falta dela, Sao Paulo.
 */
object WeatherClient {

    const val DEFAULT_LAT = -23.5505      // Sao Paulo
    const val DEFAULT_LON = -46.6333

    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Temperatura em graus C, ou null se falhar. Chamar fora da thread principal. */
    fun currentTemp(lat: Double, lon: Double): Double? {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m",
            lat, lon
        )
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val body = r.body?.string() ?: return null
            val cur = JSONObject(body).optJSONObject("current") ?: return null
            return if (cur.has("temperature_2m")) cur.getDouble("temperature_2m") else null
        }
    }

    /** Ultima localizacao conhecida (qualquer provedor), ou null sem permissao/posicao. */
    fun lastKnown(ctx: Context): Pair<Double, Double>? {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val loc = runCatching {
            lm.getProviders(true).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        return loc.latitude to loc.longitude
    }
}
