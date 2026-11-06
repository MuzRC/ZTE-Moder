package com.muz.ztemodem

import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var cardLogin: MaterialCardView
    private lateinit var layoutDashboard: View
    private lateinit var etGatewayIp: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnReboot: Button
    private lateinit var tvLogs: TextView

    // UI Widgets Dashboard
    private lateinit var tvTelcoName: TextView
    private lateinit var tvNetworkType: TextView
    private lateinit var tvSignalStatus: TextView
    private lateinit var pbSignal: ProgressBar
    private lateinit var tvBatteryPercent: TextView
    private lateinit var tvChargingState: TextView

    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private val fakeUserAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Bind Views
        cardLogin = findViewById(R.id.cardLogin)
        layoutDashboard = findViewById(R.id.layoutDashboard)
        etGatewayIp = findViewById(R.id.etGatewayIp)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnReboot = findViewById(R.id.btnReboot)
        tvLogs = findViewById(R.id.tvLogs)

        tvTelcoName = findViewById(R.id.tvTelcoName)
        tvNetworkType = findViewById(R.id.tvNetworkType)
        tvSignalStatus = findViewById(R.id.tvSignalStatus)
        pbSignal = findViewById(R.id.pbSignal)
        tvBatteryPercent = findViewById(R.id.tvBatteryPercent)
        tvChargingState = findViewById(R.id.tvChargingState)

        btnLogin.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            log("Menyambung ke $ip...")
            executeLogin(ip, pwd)
        }

        btnRefresh.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            fetchModemStatus(ip)
        }

        btnReboot.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            showRebootConfirmation(ip)
        }
    }

    private fun executeLogin(gatewayIp: String, plainPass: String) {
        lifecycleScope.launch {
            btnLogin.isEnabled = false
            val baseUrl = "http://$gatewayIp"
            val targetUrl = "$baseUrl/goform/goform_set_cmd_process"

            val success = withContext(Dispatchers.IO) {
                try {
                    val base64Pass = Base64.encodeToString(
                        plainPass.toByteArray(Charsets.UTF_8),
                        Base64.NO_WRAP
                    )

                    val body = FormBody.Builder()
                        .add("isTest", "false")
                        .add("goformId", "LOGIN")
                        .add("password", base64Pass)
                        .build()

                    val request = Request.Builder()
                        .url(targetUrl)
                        .post(body)
                        .header("User-Agent", fakeUserAgent)
                        .header("Referer", "$baseUrl/index.html")
                        .header("Origin", baseUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Connection", "close")
                        .build()

                    val response = client.newCall(request).execute()
                    val resultString = response.body?.string().orEmpty()
                    resultString.contains("\"result\":\"0\"") || resultString.contains("\"result\":0")
                } catch (e: Exception) {
                    log("Ralat: ${e.localizedMessage}")
                    false
                }
            }

            btnLogin.isEnabled = true
            if (success) {
                log("Login sukses! Menjana Dashboard...")
                cardLogin.visibility = View.GONE
                layoutDashboard.visibility = View.VISIBLE
                fetchModemStatus(gatewayIp)
            } else {
                log("Login gagal. Sila semak semula password.")
            }
        }
    }

    private fun fetchModemStatus(gatewayIp: String) {
        lifecycleScope.launch {
            val baseUrl = "http://$gatewayIp"
            val query = "isTest=false&cmd=network_type,signalbar,battery_charging,battery_vol_percent,battery_value,network_provider"
            val targetUrl = "$baseUrl/goform/goform_get_cmd_process?$query"

            val jsonString = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .get()
                        .header("User-Agent", fakeUserAgent)
                        .header("Referer", "$baseUrl/index.html")
                        .header("Origin", baseUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Connection", "close")
                        .build()

                    val response = client.newCall(request).execute()
                    response.body?.string().orEmpty()
                } catch (e: Exception) {
                    ""
                }
            }

            if (jsonString.isNotEmpty()) {
                parseAndDisplay(jsonString)
            }
        }
    }

    private fun parseAndDisplay(rawJson: String) {
        try {
            val json = JSONObject(rawJson)

            // 1. Telco & Network
            val provider = json.optString("network_provider", "Tiada Rangkaian")
            val netType = json.optString("network_type", "Tiada Servis")
            tvTelcoName.text = if (provider.isNotBlank()) provider else "Tiada Servis"
            tvNetworkType.text = netType

            // 2. Signal Bar
            val signal = json.optString("signalbar", "0").toIntOrNull() ?: 0
            pbSignal.progress = signal
            tvSignalStatus.text = "Bar $signal / 5"

            // 3. Status Bateri
            val charging = json.optString("battery_charging", "0")
            tvChargingState.text = if (charging == "1") "Sedang Dicas (Charging)" else "Guna Bateri"

            val battPct = json.optString("battery_vol_percent")
            val battVal = json.optString("battery_value")
            when {
                battPct.isNotBlank() -> tvBatteryPercent.text = "$battPct%"
                battVal.isNotBlank() -> tvBatteryPercent.text = "$battVal%"
                else -> tvBatteryPercent.text = "100% (Plugged)"
            }

            log("Status dikemaskini: $provider | $netType | Signal $signal")
        } catch (e: Exception) {
            log("Gagal hurai JSON: ${e.localizedMessage}")
        }
    }

    private fun showRebootConfirmation(gatewayIp: String) {
        AlertDialog.Builder(this)
            .setTitle("Reboot Modem")
            .setMessage("Adakah anda pasti mahu mulakan semula modem ZTE?")
            .setPositiveButton("Reboot") { _, _ -> executeReboot(gatewayIp) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun executeReboot(gatewayIp: String) {
        lifecycleScope.launch {
            val baseUrl = "http://$gatewayIp"
            val targetUrl = "$baseUrl/goform/goform_set_cmd_process"

            val success = withContext(Dispatchers.IO) {
                try {
                    val body = FormBody.Builder()
                        .add("isTest", "false")
                        .add("goformId", "REBOOT_DEVICE")
                        .build()

                    val request = Request.Builder()
                        .url(targetUrl)
                        .post(body)
                        .header("User-Agent", fakeUserAgent)
                        .header("Referer", "$baseUrl/index.html")
                        .header("Connection", "close")
                        .build()

                    val response = client.newCall(request).execute()
                    val res = response.body?.string().orEmpty()
                    res.contains("\"result\":\"0\"") || res.contains("\"result\":0")
                } catch (e: Exception) {
                    false
                }
            }

            if (success) {
                log("Modem sedang dimulakan semula (Rebooting)...")
                layoutDashboard.visibility = View.GONE
                cardLogin.visibility = View.VISIBLE
            } else {
                log("Gagal menghantar arahan Reboot.")
            }
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLogs.append("\n$msg")
        }
    }
}
