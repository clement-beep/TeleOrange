package fr.clement.teleorange

import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Pilote le décodeur Orange (UHD 86, TV 4, TV 5…) via son API HTTP locale sur le port 8080.
 *  - operation=10 : état du décodeur (sert aussi à le détecter)
 *  - operation=01 : appui de touche (mode 0 = appui court, 1 = appui long, 2 = relâché)
 */
object OrangeDecoder {

    const val PORT = 8080

    object Key {
        const val POWER = 116
        const val CH_UP = 402;   const val CH_DOWN = 403
        const val VOL_UP = 115;  const val VOL_DOWN = 114; const val MUTE = 113
        const val UP = 103;      const val DOWN = 108
        const val LEFT = 105;    const val RIGHT = 106;    const val OK = 352
        const val BACK = 158;    const val MENU = 139
        const val PLAY_PAUSE = 164
        const val REWIND = 168;  const val FORWARD = 159
        const val REC = 167;     const val VOD = 393
        fun digit(n: Int) = 512 + n          // 0 → 512 … 9 → 521
    }

    private fun httpGet(url: String, timeoutMs: Int): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs * 3
        c.useCaches = false
        try {
            if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
        } finally { c.disconnect() }
    } catch (_: Exception) { null }

    /** true si l'appareil à cette IP répond comme un décodeur Orange. */
    fun probe(ip: String, timeoutMs: Int = 600): Boolean {
        val body = httpGet("http://$ip:$PORT/remoteControl/cmd?operation=10", timeoutMs) ?: return false
        return body.contains("\"result\"") && body.contains("responseCode")
    }

    /** Envoie une touche. Retourne false si le décodeur n'a pas répondu. */
    fun sendKey(ip: String, key: Int, mode: Int = 0): Boolean =
        httpGet("http://$ip:$PORT/remoteControl/cmd?operation=01&key=$key&mode=$mode", 1500) != null

    /** IPv4 du téléphone sur le Wi-Fi (ex : 192.168.1.23). */
    fun localIpv4(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress }
            .firstOrNull { it != null && (it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.")) }
    } catch (_: Exception) { null }

    /** Balaye le sous-réseau /24 du téléphone et renvoie l'IP du premier décodeur trouvé. */
    fun scan(onProgress: (Int) -> Unit): String? {
        val me = localIpv4() ?: return null
        val prefix = me.substringBeforeLast('.')
        // On teste d'abord les IP les plus fréquentes chez Orange (Livebox = .1, décodeur souvent .10–.30)
        val order = (10..30) + (2..9) + (31..254)
        val found = AtomicReference<String?>(null)
        val pool = Executors.newFixedThreadPool(48)
        var done = 0
        val lock = Any()
        for (i in order) {
            pool.execute {
                if (found.get() == null) {
                    val ip = "$prefix.$i"
                    if (ip != me && probe(ip)) found.compareAndSet(null, ip)
                }
                synchronized(lock) { done++; onProgress(done * 100 / order.size) }
            }
        }
        pool.shutdown()
        while (!pool.awaitTermination(100, TimeUnit.MILLISECONDS)) {
            if (found.get() != null) { pool.shutdownNow(); break }
        }
        return found.get()
    }
}
