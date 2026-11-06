package com.muz.ztemodem.data

// Status sambungan dashboard
enum class ModemConnectionState {
    LIVE,
    NO_SERVICE,
    OFFLINE
}

// Model diagnostik signal radio frequency (RF)
data class SignalDiagnostics(
    val isReachable: Boolean = false,
    val provider: String = "Tiada Rangkaian",
    val networkType: String = "N/A",
    val signalBar: Int = 0,
    val rsrp: String = "-",
    val rsrq: String = "-",
    val rssi: String = "-",
    val sinr: String = "-",
    val cellId: String = "-",
    val wanIp: String = "-"
)

// Model statistik data dan kuota bulanan
data class TrafficStats(
    val monthlyTotalBytes: Long = 0L,
    val realtimeRxBytes: Long = 0L,
    val realtimeTxBytes: Long = 0L,
    val connectedTimeSeconds: Long = 0L
) {
    fun formatBytes(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format("%.2f GB", mb / 1024.0)
        } else {
            String.format("%.1f MB", mb)
        }
    }

    fun formatDuration(): String {
        val hours = connectedTimeSeconds / 3600
        val minutes = (connectedTimeSeconds % 3600) / 60
        val seconds = connectedTimeSeconds % 60
        return String.format("%02dj %02dm %02ds", hours, minutes, seconds)
    }
}

// Model peranti yang bersambung ke Wi-Fi
data class ConnectedClient(
    val ipAddress: String,
    val macAddress: String,
    val hostName: String
)

// Model mesej SMS
data class SmsMessage(
    val id: String,
    val sender: String,
    val content: String,
    val timestamp: String
)

// Model keputusan ujian ping latency
data class PingResult(
    val target: String,
    val latencyMs: Long,
    val isSuccess: Boolean
)
