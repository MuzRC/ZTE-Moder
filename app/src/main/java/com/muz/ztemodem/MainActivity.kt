package com.muz.ztemodem

import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etGatewayIp: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnCheckStatus: Button
    private lateinit var tvLogs: TextView

    // Simpan cookie session dalam RAM supaya token login kekal hidup
    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
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

        etGatewayIp = findViewById(R.id.etGatewayIp)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnCheckStatus = findViewById(R.id.btnCheckStatus)
        tvLogs = findViewById(R.id.tvLogs)

        btnLogin.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            val pwd = etPassword.text.toString().trim()
            logMessage("Menghubungi modem di http://$ip ...")
            executeLogin(ip, pwd)
        }

        btnCheckStatus.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            fetchModemStatus(ip)
        }
    }

    private fun executeLogin(gatewayIp: String, plainPass: String) {
        lifecycleScope.launch {
            btnLogin.isEnabled = false
            val baseUrl = "http://$gatewayIp"
            val targetUrl = "$baseUrl/goform/goform_set_cmd_process"

            val success = withContext(Dispatchers.IO) {
                try {
                    // Encode password guna Base64 NO_WRAP (tiada line break \n)
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
                        .header("Referer", "$baseUrl/index.html")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val response = client.newCall(request).execute()
                    val resultString = response.body?.string().orEmpty()
                    logMessage("Jawapan Server: $resultString")

                    // Modem ZTE pulangkan {"result":"0"} kalau login sah
                    resultString.contains("\"result\":\"0\"") || resultString.contains("\"result\":0")
                } catch (e: Exception) {
                    logMessage("Ralat Sambungan: ${e.localizedMessage}")
                    false
                }
            }

            btnLogin.isEnabled = true
            if (success) {
                logMessage(">> LOGIN BERJAYA. Butang status aktif.")
                btnCheckStatus.isEnabled = true
            } else {
                logMessage(">> LOGIN GAGAL. Semak password atau pastikan tiada peranti lain tengah buka web UI modem.")
            }
        }
    }

    private fun fetchModemStatus(gatewayIp: String) {
        lifecycleScope.launch {
            val baseUrl = "http://$gatewayIp"
            // Ambil info network, signal bar, status cas & peratus bateri
            val query = "isTest=false&cmd=network_type,signalbar,battery_charging,battery_vol_percent,network_provider"
            val targetUrl = "$baseUrl/goform/goform_get_cmd_process?$query"

            val result = withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder()
                        .url(targetUrl)
                        .get()
                        .header("Referer", "$baseUrl/index.html")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val response = client.newCall(request).execute()
                    response.body?.string().orEmpty()
                } catch (e: Exception) {
                    "Gagal ambil data: ${e.localizedMessage}"
                }
            }

            logMessage(">> DATA MODEM DITERIMA:\n$result")
        }
    }

    private fun logMessage(msg: String) {
        runOnUiThread {
            tvLogs.append("\n$msg")
        }
    }
}
