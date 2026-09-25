package com.mynd.watch.data

import android.content.Context
import android.content.SharedPreferences

/** Controla quais apps têm notificações repassadas ao relógio. Thread-safe via SharedPreferences. */
object NotifFilter {
    private var prefs: SharedPreferences? = null

    fun init(ctx: Context) {
        if (prefs != null) return
        prefs = ctx.applicationContext.getSharedPreferences("notif_filter", Context.MODE_PRIVATE)
    }

    fun seenApps(): List<String> =
        prefs?.getString("seen", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

    fun blockedApps(): Set<String> =
        prefs?.getStringSet("blocked", emptySet())?.toSet() ?: emptySet()

    fun addSeen(pkg: String) {
        val p = prefs ?: return
        val seen = seenApps().toMutableList()
        seen.remove(pkg); seen.add(0, pkg)
        while (seen.size > 60) seen.removeLast()
        p.edit().putString("seen", seen.joinToString(",")).apply()
    }

    fun setBlocked(pkg: String, blocked: Boolean) {
        val p = prefs ?: return
        val set = blockedApps().toMutableSet()
        if (blocked) set.add(pkg) else set.remove(pkg)
        p.edit().putStringSet("blocked", set).apply()
    }

    fun isBlocked(pkg: String) = pkg in blockedApps()
}
