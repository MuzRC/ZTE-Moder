package com.muz.ztemodem.network

import android.util.Base64
import com.muz.ztemodem.data.ConnectedClient
import com.muz.ztemodem.data.PingResult
import com.muz.ztemodem.data.SignalDiagnostics
import com.muz.ztemodem.data.SmsMessage
import com.muz.ztemodem.data.TrafficStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ZteApiClient(var gatewayIp: String = "192.168.0.1") {

    private val fakeUserAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: ArrayList()
            }
        })
        .build()

    private val baseUrl: String get() = "http://$gatewayIp"
    private val setUrl: String get() = "$baseUrl/goform/goform_set_cmd_process"
    private val getUrl: String get() = "$baseUrl/goform/goform_get_cmd_process"

    suspend fun sendPost(formBody: FormBody): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(setUrl)
                .post(formBody)
                .header("User-Agent", fakeUserAgent)
                .header("Referer", "$baseUrl/index.html")
                .header("Origin", baseUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Connection", "close")
                .build()

            client.newCall(request).execute().body?.string().orEmpty()
        } catch (e: Exception) {
            "ERROR: ${e.localizedMessage}"
        }
    }

    suspend fun sendGet(query: String): String = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$getUrl?$query")
                .get()
                .header("User-Agent", fakeUserAgent)
                .header("Referer", "$baseUrl/index.html")
                .header("Origin", baseUrl)
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Connection", "close")
                .build()

            client.newCall(request).execute().body?.string().orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    // 1. LOGIN
    suspend fun login(plainPass: String): Boolean {
        val base64Pass = Base64.encodeToString(plainPass.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "LOGIN")
            .add("password", base64Pass)
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 2. DIAGNOSTIK SIGNAL RF LENGKAP
    suspend fun getSignalDiagnostics(): SignalDiagnostics {
        val query = "isTest=false&cmd=network_type,signalbar,network_provider,lte_rsrp,lte_rsrq,lte_rssi,lte_snr,cell_id,wan_ipaddr"
        val raw = sendGet(query)
        if (raw.isBlank() || raw.startsWith("ERROR")) {
            return SignalDiagnostics(isReachable = false)
        }
        return try {
            val j = JSONObject(raw)
            SignalDiagnostics(
                isReachable = true,
                provider = j.optString("network_provider", "Tiada Rangkaian"),
                networkType = j.optString("network_type", "Tiada"),
                signalBar = j.optString("signalbar", "0").toIntOrNull() ?: 0,
                rsrp = j.optString("lte_rsrp", "-").ifBlank { "-" },
                rsrq = j.optString("lte_rsrq", "-").ifBlank { "-" },
                rssi = j.optString("lte_rssi", "-").ifBlank { "-" },
                sinr = j.optString("lte_snr", "-").ifBlank { "-" },
                cellId = j.optString("cell_id", "-").ifBlank { "-" },
                wanIp = j.optString("wan_ipaddr", "-").ifBlank { "-" }
            )
        } catch (e: Exception) {
            SignalDiagnostics(isReachable = false)
        }
    }

    // 3. STATISTIK KUOTA & DATA
    suspend fun getTrafficStats(): TrafficStats {
        val query = "isTest=false&cmd=monthly_tx_bytes,monthly_rx_bytes,realtime_tx_bytes,realtime_rx_bytes,realtime_time"
        val raw = sendGet(query)
        return try {
            val j = JSONObject(raw)
            val rx = j.optLong("monthly_rx_bytes", 0L)
            val tx = j.optLong("monthly_tx_bytes", 0L)
            val rrx = j.optLong("realtime_rx_bytes", 0L)
            val rtx = j.optLong("realtime_tx_bytes", 0L)
            val time = j.optLong("realtime_time", 0L)

            TrafficStats(
                monthlyTotalBytes = rx + tx,
                realtimeRxBytes = rrx,
                realtimeTxBytes = rtx,
                connectedTimeSeconds = time
            )
        } catch (e: Exception) {
            TrafficStats()
        }
    }

    // 4. SUIS DATA SELULAR
    suspend fun setMobileDataState(connect: Boolean): Boolean {
        val cmd = if (connect) "CONNECT_NETWORK" else "DISCONNECT_NETWORK"
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", cmd)
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 5. KUNCI RANGKAIAN (4G ONLY / AUTO)
    suspend fun setNetworkMode(mode: String): Boolean {
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "SET_BEARER_PREFERENCE")
            .add("BearerPreference", mode)
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 6. SENARAI PERANTI HOTSPOT
    suspend fun getConnectedClients(): List<ConnectedClient> {
        val query = "isTest=false&cmd=station_list"
        val raw = sendGet(query)
        val list = mutableListOf<ConnectedClient>()
        try {
            val j = JSONObject(raw)
            val array = j.optJSONArray("station_list") ?: JSONArray()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    ConnectedClient(
                        ipAddress = item.optString("ip_addr", "-"),
                        macAddress = item.optString("mac_addr", "-"),
                        hostName = item.optString("host_name", "Peranti Tanpa Nama")
                    )
                )
            }
        } catch (e: Exception) {
            // Abaikan ralat huraian format
        }
        return list
    }

    // 7. PETI MASUK SMS
    suspend fun getSmsMessages(): List<SmsMessage> {
        val query = "isTest=false&cmd=sms_data_total,sms_page_data&page=0&data_per_page=10&mem_store=1&tags=10"
        val raw = sendGet(query)
        val list = mutableListOf<SmsMessage>()
        try {
            val j = JSONObject(raw)
            val messages = j.optJSONArray("messages") ?: JSONArray()
            for (i in 0 until messages.length()) {
                val m = messages.getJSONObject(i)
                val rawContent = m.optString("content", "")
                list.add(
                    SmsMessage(
                        id = m.optString("id", ""),
                        sender = m.optString("number", "Telco"),
                        content = decodeUcs2(rawContent),
                        timestamp = m.optString("date", "")
                    )
                )
            }
        } catch (e: Exception) {
            // Abaikan ralat SMS
        }
        return list
    }

    // 8. HANTAR SMS
    suspend fun sendSms(recipient: String, messageText: String): Boolean {
        val timeStamp = SimpleDateFormat("yy;MM;dd;HH;mm;ss;+32", Locale.US).format(Date())
        val encodedMsg = encodeUcs2(messageText)

        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "SEND_SMS")
            .add("Number", recipient)
            .add("sms_time", timeStamp)
            .add("MessageBody", encodedMsg)
            .add("ID", "-1")
            .add("encode_type", "UNICODE")
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 9. TETAPAN WI-FI (SSID & PASSWORD)
    suspend fun updateWifiConfig(newSsid: String, newKey: String): Boolean {
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "SET_WIFI_INFO")
            .add("wifi_enabled", "1")
            .add("ssid", newSsid)
            .add("AuthMode", "WPA2PSK")
            .add("EncrypType", "AES")
            .add("WpaKey", newKey)
            .add("hide_ssid", "0")
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 10. DAIL USSD
    suspend fun sendUssd(code: String): String {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "USSD_PROCESS")
            .add("USSD_operator", "USSD_send")
            .add("USSD_send_number", encodedCode)
            .build()

        sendPost(body)
        kotlinx.coroutines.delay(3500)
        val query = "isTest=false&cmd=ussd_data,ussd_process"
        val res = sendGet(query)
        return try {
            val j = JSONObject(res)
            val rawMsg = j.optString("ussd_data", "")
            if (rawMsg.isNotBlank()) decodeUcs2(rawMsg) else "Tiada respon dari Telco (Cuba sekali lagi)."
        } catch (e: Exception) {
            "Gagal menerima balasan USSD."
        }
    }

    // 11. UJIAN PING SOKET
    suspend fun pingHost(host: String = "8.8.8.8", port: Int = 53): PingResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var reachable = false
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2500)
                reachable = true
            }
        } catch (e: Exception) {
            reachable = false
        }
        val latency = System.currentTimeMillis() - start
        PingResult(host, if (reachable) latency else -1L, reachable)
    }

    // 12. REBOOT MODEM
    suspend fun reboot(): Boolean {
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "REBOOT_DEVICE")
            .build()

        return try {
            val res = sendPost(body)
            res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
        } catch (e: Exception) {
            true
        }
    }

    private fun encodeUcs2(src: String): String {
        val sb = StringBuilder()
        for (c in src) {
            sb.append(String.format("%04X", c.code))
        }
        return sb.toString()
    }

    private fun decodeUcs2(src: String): String {
        if (src.length % 4 != 0) return src
        return try {
            val sb = StringBuilder()
            var i = 0
            while (i < src.length) {
                val hexChar = src.substring(i, i + 4)
                sb.append(hexChar.toInt(16).toChar())
                i += 4
            }
            sb.toString()
        } catch (e: Exception) {
            src
        }
    }
}
