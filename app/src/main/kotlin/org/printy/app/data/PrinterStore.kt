// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.printy.escp.EpsonModel
import java.net.InetAddress
import java.util.UUID

data class PrinterProfile(val id: String = UUID.randomUUID().toString(), val name: String,
    val host: String, val port: Int = 9100, val modelId: String = "l130", val lastUsed: Long = 0) {
    val endpoint get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

object ProfileValidation {
    fun error(name: String, host: String, port: String): String? = when {
        name.trim().isEmpty() -> "Give your printer a name."
        !isAddress(host.trim()) -> "Enter a valid printer IP address, such as 192.168.1.1."
        port.toIntOrNull() !in 1..65535 -> "The port must be a number from 1 to 65535."
        else -> null
    }
    // Accept numeric IPs only. This never initiates DNS discovery.
    fun isAddress(host: String): Boolean = if (':' in host) {
        host.matches(Regex("[0-9a-fA-F:]+")) && runCatching { InetAddress.getByName(host).address.size == 16 }.getOrDefault(false)
    } else {
        val parts = host.split('.')
        parts.size == 4 && parts.all { it.isNotEmpty() && it.length <= 3 && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }
}

class PrinterStore(context: Context) {
    private val prefs = context.getSharedPreferences("printers", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(load())
    val printers = mutable.asStateFlow()
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) { prefs.edit { putBoolean("onboarded", value) } }
    private fun load(): List<PrinterProfile> = runCatching {
        val json = JSONArray(prefs.getString("profiles", "[]"))
        (0 until json.length()).map { index ->
            val p = json.getJSONObject(index)
            PrinterProfile(p.getString("id"), p.getString("name"), p.getString("host"),
                p.getInt("port"), p.getString("model"), p.optLong("lastUsed"))
        }.filter { ProfileValidation.error(it.name, it.host, it.port.toString()) == null && EpsonModel.supported.any { m -> m.id == it.modelId } }
    }.getOrDefault(emptyList())
    @Synchronized fun save(profile: PrinterProfile) {
        require(ProfileValidation.error(profile.name, profile.host, profile.port.toString()) == null)
        EpsonModel.byId(profile.modelId)
        persist(mutable.value.filterNot { it.id == profile.id } + profile)
    }
    @Synchronized fun remove(id: String) = persist(mutable.value.filterNot { it.id == id })
    @Synchronized fun markUsed(id: String) = persist(mutable.value.map { if (it.id == id) it.copy(lastUsed = System.currentTimeMillis()) else it })
    private fun persist(list: List<PrinterProfile>) {
        val json = JSONArray()
        list.forEach { p -> json.put(JSONObject().put("id", p.id).put("name", p.name).put("host", p.host)
            .put("port", p.port).put("model", p.modelId).put("lastUsed", p.lastUsed)) }
        prefs.edit { putString("profiles", json.toString()) }
        mutable.value = list
    }
}
