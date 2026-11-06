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

    // Simpan cookie session dalam RAM
    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    // User-agent browser telefon standard supaya tidak di-reject oleh modem ZTE
    private val fakeUserAgent = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
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
                    // Encode password guna Base64 NO_WRAP (tiada baris baru \n)
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
                        // Header wajib penyamaran browser dan elak keep-alive socket hang
                        .header("User-Agent", fakeUserAgent)
                        .header("Referer", "$baseUrl/index.html")
                        .header("Origin", baseUrl)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .header("Connection", "close") 
                        .build()

                    val response = client.newCall(request).execute()
                    val resultString = response.body?.string().orEmpty()
                    logMessage("Jawapan Server: $resultString")

                    // Modem ZTE memulangkan result:0 jika berjaya
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
                logMessage(">> LOGIN GAGAL. Semak password atau pastikan tiada browser lain aktif.")
            }
        }
    }

    private fun fetchModemStatus(gatewayIp: String) {
        lifecycleScope.launch {
            val baseUrl = "http://$gatewayIp"
            val query = "isTest=false&cmd=network_type,signalbar,battery_charging,battery_vol_percent,network_provider"
            val targetUrl = "$baseUrl/goform/goform_get_cmd_process?$query"

            val result = withContext(Dispatchers.IO) {
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
