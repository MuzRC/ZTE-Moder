package com.muz.ztemodem

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.muz.ztemodem.network.ZteApiClient
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val api = ZteApiClient()

    // Views
    private lateinit var cardLogin: MaterialCardView
    private lateinit var layoutDashboard: View
    private lateinit var etGatewayIp: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnReboot: Button
    private lateinit var tvLogs: TextView

    // RF Diagnostics Views
    private lateinit var tvTelcoName: TextView
    private lateinit var tvNetworkType: TextView
    private lateinit var pbSignal: ProgressBar
    private lateinit var tvRsrp: TextView
    private lateinit var tvRsrq: TextView
    private lateinit var tvRssi: TextView
    private lateinit var tvSinr: TextView
    private lateinit var tvCellId: TextView
    private lateinit var tvWanIp: TextView

    // Traffic Views
    private lateinit var tvMonthlyData: TextView
    private lateinit var tvLiveTraffic: TextView
    private lateinit var tvUpTime: TextView

    // Actions
    private lateinit var btnLock4G: Button
    private lateinit var btnLockAuto: Button
    private lateinit var btnClients: Button
    private lateinit var btnSms: Button
    private lateinit var btnUssd: Button

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

        tvMonthlyData = findViewById(R.id.tvMonthlyData)
        tvLiveTraffic = findViewById(R.id.tvLiveTraffic)
        tvUpTime = findViewById(R.id.tvUpTime)

        btnLock4G = findViewById(R.id.btnLock4G)
        btnLockAuto = findViewById(R.id.btnLockAuto)
        btnClients = findViewById(R.id.btnClients)
        btnSms = findViewById(R.id.btnSms)
        btnUssd = findViewById(R.id.btnUssd)
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
                    log("Login Berjaya! Memuatkan Dashboard...")
                    cardLogin.visibility = View.GONE
                    layoutDashboard.visibility = View.VISIBLE
                    refreshAllData()
                } else {
                    log("Login Gagal. Semak semula kata laluan.")
                }
            }
        }

        btnRefresh.setOnClickListener { refreshAllData() }

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

        // Papar Peti Masuk SMS
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

        // Dail USSD (Cth: *128#)
        btnUssd.setOnClickListener {
            val input = EditText(this)
            input.hint = "*128#"
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
            // Tarik bacaan Signal RF
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

            // Tarik Kuota & Masa Bersambung
            val traffic = api.getTrafficStats()
            tvMonthlyData.text = traffic.formatBytes(traffic.monthlyTotalBytes)
            tvLiveTraffic.text = "Sesi ini: DL ${traffic.formatBytes(traffic.realtimeRxBytes)} | UL ${traffic.formatBytes(traffic.realtimeTxBytes)}"
            tvUpTime.text = "Masa: ${traffic.formatDuration()}"

            log("Semua data modem berjaya dikemaskini.")
        }
    }

    private fun log(text: String) {
        runOnUiThread { tvLogs.append("\n$text") }
    }
}
