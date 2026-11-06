package com.muz.ztemodem

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.muz.ztemodem.network.ZteApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val api = ZteApiClient()
    private var signalHunterJob: Job? = null

    // Views
    private lateinit var cardLogin: MaterialCardView
    private lateinit var layoutDashboard: View
    private lateinit var etGatewayIp: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnReboot: Button
    private lateinit var tvLogs: TextView

    // Signal Views
    private lateinit var tvTelcoName: TextView
    private lateinit var tvNetworkType: TextView
    private lateinit var pbSignal: ProgressBar
    private lateinit var tvRsrp: TextView
    private lateinit var tvRsrq: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvSinr: TextView
    private lateinit var tvCellId: TextView
    private lateinit var tvWanIp: TextView
    private lateinit var btnSignalHunter: Button

    // Data Switch
    private lateinit var btnDataOn: Button
    private lateinit var btnDataOff: Button
    private lateinit var btnResetIp: Button

    // Traffic Views
    private lateinit var tvMonthlyData: TextView
    private lateinit var tvLiveTraffic: TextView
    private lateinit var tvUpTime: TextView

    // Toolbox Buttons
    private lateinit var btnLock4G: Button
    private lateinit var btnLockAuto: Button
    private lateinit var btnClients: Button
    private lateinit var btnSms: Button
    private lateinit var btnUssd: Button
    private lateinit var btnSendSms: Button
    private lateinit var btnWifiSettings: Button
    private lateinit var btnPing: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupListeners()
    }

    private fun initViews() {
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
        pbSignal = findViewById(R.id.pbSignal)
        tvRsrp = findViewById(R.id.tvRsrp)
        tvRsrq = findViewById(R.id.tvRsrq)
        tvRssi = findViewById(R.id.tvRssi)
        tvSinr = findViewById(R.id.tvSinr)
        tvCellId = findViewById(R.id.tvCellId)
        tvWanIp = findViewById(R.id.tvWanIp)
        btnSignalHunter = findViewById(R.id.btnSignalHunter)

        btnDataOn = findViewById(R.id.btnDataOn)
        btnDataOff = findViewById(R.id.btnDataOff)
        btnResetIp = findViewById(R.id.btnResetIp)

        tvMonthlyData = findViewById(R.id.tvMonthlyData)
        tvLiveTraffic = findViewById(R.id.tvLiveTraffic)
        tvUpTime = findViewById(R.id.tvUpTime)

        btnLock4G = findViewById(R.id.btnLock4G)
        btnLockAuto = findViewById(R.id.btnLockAuto)
        btnClients = findViewById(R.id.btnClients)
        btnSms = findViewById(R.id.btnSms)
        btnUssd = findViewById(R.id.btnUssd)
        btnSendSms = findViewById(R.id.btnSendSms)
        btnWifiSettings = findViewById(R.id.btnWifiSettings)
        btnPing = findViewById(R.id.btnPing)
    }

    private fun setupListeners() {
        btnLogin.setOnClickListener {
            val ip = etGatewayIp.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            api.gatewayIp = ip
            log("Menghubungi modem di $ip...")

            lifecycleScope.launch {
                btnLogin.isEnabled = false
                val success = api.login(pass)
                btnLogin.isEnabled = true
                if (success) {
                    log("Login Berjaya! Membuka Dashboard...")
                    cardLogin.visibility = View.GONE
                    layoutDashboard.visibility = View.VISIBLE
                    refreshAllData()
                } else {
                    log("Login Gagal. Sila semak kata laluan.")
                }
            }
        }

        btnRefresh.setOnClickListener { refreshAllData() }

        // Suis Data ON
        btnDataOn.setOnClickListener {
            lifecycleScope.launch {
                log("Menyambungkan talian data selular...")
                val ok = api.setMobileDataState(true)
                log(if (ok) "Data selular disambungkan." else "Gagal menyambung data.")
                refreshAllData()
            }
        }

        // Suis Data OFF
        btnDataOff.setOnClickListener {
            lifecycleScope.launch {
                log("Memutuskan data selular...")
                val ok = api.setMobileDataState(false)
                log(if (ok) "Data selular dimatikan." else "Gagal mematikan data.")
                refreshAllData()
            }
        }

        // Reset WAN IP Pantas
        btnResetIp.setOnClickListener {
            lifecycleScope.launch {
                log("Menjalankan kitaran WAN IP...")
                api.setMobileDataState(false)
                delay(2000)
                api.setMobileDataState(true)
                delay(1500)
                refreshAllData()
                log("IP baru sedang diperoleh.")
            }
        }

        // Mod Signal Hunter (Live Polling setiap 2 saat)
        btnSignalHunter.setOnClickListener {
            if (signalHunterJob?.isActive == true) {
                signalHunterJob?.cancel()
                btnSignalHunter.text = "Mula Signal Hunter (Live)"
                btnSignalHunter.setTextColor(0xFF3FB950.toInt())
                log("Signal Hunter ditamatkan.")
            } else {
                btnSignalHunter.text = "Berhenti Signal Hunter"
                btnSignalHunter.setTextColor(0xFFDA3633.toInt())
                log("Signal Hunter aktif! Gerakkan modem untuk bacaan terbaik.")
                signalHunterJob = lifecycleScope.launch {
                    while (isActive) {
                        val sig = api.getSignalDiagnostics()
                        tvTelcoName.text = sig.provider
                        tvNetworkType.text = sig.networkType
                        pbSignal.progress = sig.signalBar
                        tvRsrp.text = "RSRP: ${sig.rsrp} dBm"
                        tvRsrq.text = "RSRQ: ${sig.rsrq} dB"
                        tvRssi.text = "RSSI: ${sig.rssi} dBm"
                        tvSinr.text = "SINR: ${sig.sinr} dB"
                        tvCellId.text = "Cell ID: ${sig.cellId}"
                        tvWanIp.text = "WAN: ${sig.wanIp}"
                        delay(2000)
                    }
                }
            }
        }

        // Kunci 4G Only
        btnLock4G.setOnClickListener {
            lifecycleScope.launch {
                log("Mengunci modem ke mod 4G Only...")
                val ok = api.setNetworkMode("Only_LTE")
                log(if (ok) "Berjaya dikunci ke 4G Only." else "Gagal menukar mod.")
                refreshAllData()
            }
        }

        // Kunci Mod Auto
        btnLockAuto.setOnClickListener {
            lifecycleScope.launch {
                log("Menukar ke mod Auto (4G/3G)...")
                val ok = api.setNetworkMode("NETWORK_auto")
                log(if (ok) "Mod auto diaktifkan." else "Gagal menukar mod.")
                refreshAllData()
            }
        }

        // Papar Klien Bersambung
        btnClients.setOnClickListener {
            lifecycleScope.launch {
                val clients = api.getConnectedClients()
                val msg = if (clients.isEmpty()) {
                    "Tiada peranti lain dikesan."
                } else {
                    clients.joinToString("\n\n") { "Nama: ${it.hostName}\nIP: ${it.ipAddress}\nMAC: ${it.macAddress}" }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Peranti Bersambung (${clients.size})")
                    .setMessage(msg)
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }

        // Peti Masuk SMS
        btnSms.setOnClickListener {
            lifecycleScope.launch {
                log("Memuat turun senarai SMS...")
                val messages = api.getSmsMessages()
                val msg = if (messages.isEmpty()) {
                    "Peti masuk SMS kosong."
                } else {
                    messages.joinToString("\n----------------\n") { "Dari: ${it.sender} (${it.timestamp})\n${it.content}" }
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Peti Masuk SMS")
                    .setMessage(msg)
                    .setPositiveButton("Tutup", null)
                    .show()
            }
        }

        // Kirim SMS Keluar
        btnSendSms.setOnClickListener {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 10)
            }
            val etNum = EditText(this).apply { hint = "Nombor (cth: 28882)" }
            val etMsg = EditText(this).apply { hint = "Mesej SMS" }
            layout.addView(etNum)
            layout.addView(etMsg)

            AlertDialog.Builder(this)
                .setTitle("Kirim SMS Telco")
                .setView(layout)
                .setPositiveButton("Kirim") { _, _ ->
                    val num = etNum.text.toString().trim()
                    val msg = etMsg.text.toString().trim()
                    if (num.isNotEmpty() && msg.isNotEmpty()) {
                        lifecycleScope.launch {
                            log("Menghantar SMS ke $num...")
                            val ok = api.sendSms(num, msg)
                            log(if (ok) "SMS berjaya dihantar!" else "Gagal menghantar SMS.")
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Tukar Tetapan Wi-Fi
        btnWifiSettings.setOnClickListener {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 20, 50, 10)
            }
            val etSsid = EditText(this).apply { hint = "Nama SSID Baru" }
            val etKey = EditText(this).apply { hint = "Kata Laluan Wi-Fi Baru (min 8 char)" }
            layout.addView(etSsid)
            layout.addView(etKey)

            AlertDialog.Builder(this)
                .setTitle("Konfigurasi Wi-Fi Modem")
                .setView(layout)
                .setPositiveButton("Simpan") { _, _ ->
                    val ssid = etSsid.text.toString().trim()
                    val key = etKey.text.toString().trim()
                    if (ssid.isNotEmpty() && key.length >= 8) {
                        lifecycleScope.launch {
                            log("Mengemas kini tetapan Wi-Fi...")
                            val ok = api.updateWifiConfig(ssid, key)
                            log(if (ok) "Wi-Fi dikemaskini. Sila sambung semula telefon ke Wi-Fi baru." else "Gagal mengemas kini Wi-Fi.")
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Ujian Ping Latency
        btnPing.setOnClickListener {
            lifecycleScope.launch {
                log("Menguji latency DNS ke 8.8.8.8...")
                val res = api.pingHost("8.8.8.8")
                val msg = if (res.isSuccess) "Latency: ${res.latencyMs} ms" else "Ping Gagal (Tiada Talian)"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Keputusan Ping (8.8.8.8)")
                    .setMessage(msg)
                    .setPositiveButton("OK", null)
                    .show()
                log("Ujian ping: $msg")
            }
        }

        // Dail USSD
        btnUssd.setOnClickListener {
            val input = EditText(this).apply { hint = "*128#" }
            AlertDialog.Builder(this)
                .setTitle("Dail USSD Telco")
                .setMessage("Masukkan kod USSD:")
                .setView(input)
                .setPositiveButton("Hantar") { _, _ ->
                    val code = input.text.toString().trim()
                    if (code.isNotEmpty()) {
                        lifecycleScope.launch {
                            log("Menghantar kod USSD $code...")
                            val reply = api.sendUssd(code)
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Balasan USSD")
                                .setMessage(reply)
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // Reboot Modem
        btnReboot.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reboot Modem")
                .setMessage("Adakah anda pasti mahu memulakan semula modem ZTE?")
                .setPositiveButton("Reboot") { _, _ ->
                    lifecycleScope.launch {
                        signalHunterJob?.cancel()
                        log("Menghantar isyarat reboot...")
                        api.reboot()
                        log(">> MODEM SEDANG REBOOT. Wi-Fi akan terputus.")
                        layoutDashboard.visibility = View.GONE
                        cardLogin.visibility = View.VISIBLE
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun refreshAllData() {
        lifecycleScope.launch {
            val sig = api.getSignalDiagnostics()
            tvTelcoName.text = sig.provider
            tvNetworkType.text = sig.networkType
            pbSignal.progress = sig.signalBar
            tvRsrp.text = "RSRP: ${sig.rsrp} dBm"
            tvRsrq.text = "RSRQ: ${sig.rsrq} dB"
            tvRssi.text = "RSSI: ${sig.rssi} dBm"
            tvSinr.text = "SINR: ${sig.sinr} dB"
            tvCellId.text = "Cell ID: ${sig.cellId}"
            tvWanIp.text = "WAN: ${sig.wanIp}"

            val traffic = api.getTrafficStats()
            tvMonthlyData.text = traffic.formatBytes(traffic.monthlyTotalBytes)
            tvLiveTraffic.text = "Sesi ini: DL ${traffic.formatBytes(traffic.realtimeRxBytes)} | UL ${traffic.formatBytes(traffic.realtimeTxBytes)}"
            tvUpTime.text = "Masa: ${traffic.formatDuration()}"

            log("Data modem berjaya dikemaskini.")
        }
    }

    private fun log(text: String) {
        runOnUiThread { tvLogs.append("\n$text") }
    }
}
