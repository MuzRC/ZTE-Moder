package com.muz.ztemodem.network

import android.util.Base64
import com.muz.ztemodem.data.ConnectedClient
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ZteApiClient(var gatewayIp: String = "192.168.0.1") {

    private val fakeUserAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
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

    // Helper POST Form ke modem
    private suspend fun sendPost(formBody: FormBody): String = withContext(Dispatchers.IO) {
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

    // Helper GET dari modem
    private suspend fun sendGet(query: String): String = withContext(Dispatchers.IO) {
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
        return try {
            val j = JSONObject(raw)
            SignalDiagnostics(
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
            SignalDiagnostics()
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

    // 4. KUNCI RANGKAIAN (4G ONLY ATAU AUTO)
    suspend fun setNetworkMode(mode: String): Boolean {
        // mode: "Only_LTE" atau "NETWORK_auto"
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "SET_BEARER_PREFERENCE")
            .add("BearerPreference", mode)
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 5. SENARAI PERANTI HOTSPOT
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
            // firmware sesetengah simpan format berbeza
        }
        return list
    }

    // 6. PETI MASUK SMS
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
                val decoded = decodeUcs2(rawContent)

                list.add(
                    SmsMessage(
                        id = m.optString("id", ""),
                        sender = m.optString("number", "Telco"),
                        content = decoded,
                        timestamp = m.optString("date", "")
                    )
                )
            }
        } catch (e: Exception) {
            // SMS parsing exception fallback
        }
        return list
    }

    // 7. DAIL USSD
    suspend fun sendUssd(code: String): String {
        val encodedCode = URLEncoder.encode(code, "UTF-8")
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "USSD_PROCESS")
            .add("USSD_operator", "USSD_send")
            .add("USSD_send_number", encodedCode)
            .build()

        sendPost(body)
        // Tunggu modem proses arahan rangkaian telco
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

    // 8. KAWALAN SLEEP MODE WI-FI (0 = Sentiasa On)
    suspend fun setWifiSleepMode(keepAlwaysOn: Boolean): Boolean {
        val sleepVal = if (keepAlwaysOn) "0" else "10"
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "SET_WIFI_SLEEP")
            .add("sleep_time", sleepVal)
            .build()

        val res = sendPost(body)
        return res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
    }

    // 9. REBOOT MODEM
    suspend fun reboot(): Boolean {
        val body = FormBody.Builder()
            .add("isTest", "false")
            .add("goformId", "REBOOT_DEVICE")
            .build()

        return try {
            val res = sendPost(body)
            res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
        } catch (e: Exception) {
            // Socket drop tanda modem sah mati restart
            true
        }
    }

    // Helper decode UCS2 Hex yang biasa dihantar oleh pelayan modem ZTE
    private fun decodeUcs2(src: String): String {
        if (src.length % 4 != 0) return src
        return try {
            val sb = java.lang.StringBuilder()
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
