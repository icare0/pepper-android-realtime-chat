package ch.fhnw.pepper_realtime.network

import android.util.Log
import java.io.*
import java.net.*
import java.security.SecureRandom
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Lightweight HTTP server serving the captured photobooth image locally via Wi-Fi.
 * Enables users to scan a QR code with their phone to view and download the photo.
 */
class PhotoboothServer(private val port: Int = 8088) {

    companion object {
        private const val TAG = "PhotoboothServer"
        // Server shuts itself down after this delay even if the overlay is never closed
        private const val MAX_LIFETIME_MS = 10 * 60 * 1000L
        private const val ACCEPT_TIMEOUT_MS = 1000
    }

    private var serverSocket: ServerSocket? = null
    @Volatile private var isRunning = false
    private var serverThread: Thread? = null
    private var clientExecutor: ExecutorService? = null
    private var currentPhotoFile: File? = null
    // Random path segment so only people who scanned the QR code can fetch the photo
    @Volatile private var accessToken: String = ""

    private fun generateToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Start the HTTP server serving the given photo file.
     * @param photoFile The JPEG file to serve
     * @return The local URL for smartphones, or null if IP could not be resolved
     */
    @Synchronized
    fun start(photoFile: File): String? {
        stop()

        currentPhotoFile = photoFile
        val ipAddress = getLocalIpAddress()
        if (ipAddress == null) {
            Log.w(TAG, "No suitable local Wi-Fi IP address found for Photobooth")
            return null
        }

        try {
            accessToken = generateToken()
            clientExecutor = Executors.newFixedThreadPool(2)
            val socket = ServerSocket().apply {
                reuseAddress = true
                soTimeout = ACCEPT_TIMEOUT_MS
                bind(InetSocketAddress(port))
            }
            serverSocket = socket
            isRunning = true
            val deadline = System.currentTimeMillis() + MAX_LIFETIME_MS

            serverThread = Thread({
                Log.i(TAG, "Photobooth HTTP server started on port $port")
                while (isRunning) {
                    if (System.currentTimeMillis() > deadline) {
                        Log.i(TAG, "Photobooth HTTP server lifetime expired, closing socket")
                        try { socket.close() } catch (ignored: Exception) {}
                        break
                    }
                    try {
                        val clientSocket = socket.accept()
                        handleClient(clientSocket)
                    } catch (e: SocketTimeoutException) {
                        // Periodic wake-up to check the deadline
                    } catch (e: SocketException) {
                        // Normal when serverSocket is closed
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error accepting client connection", e)
                    }
                }
            }, "PhotoboothHttpServer").apply {
                isDaemon = true
                start()
            }

            val url = "http://$ipAddress:$port/$accessToken/"
            Log.i(TAG, "Photobooth URL generated for $ipAddress:$port")
            return url
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Photobooth HTTP server", e)
            return null
        }
    }

    /**
     * Stop the server and release socket resources.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket", e)
        }
        serverSocket = null
        serverThread?.interrupt()
        serverThread = null
        try {
            clientExecutor?.shutdownNow()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down client executor", e)
        }
        clientExecutor = null
        currentPhotoFile = null
        accessToken = ""
    }

    /**
     * Handle incoming HTTP client request using thread pool.
     */
    private fun handleClient(socket: Socket) {
        val executor = clientExecutor ?: run {
            try { socket.close() } catch (ignored: Exception) {}
            return
        }
        try {
            executor.execute {
                try {
                    socket.soTimeout = 10000 // 10s timeout
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val out = BufferedOutputStream(socket.getOutputStream())

                    val requestLine = reader.readLine() ?: return@execute
                    val parts = requestLine.split(" ")
                    if (parts.size < 2) return@execute

                    val path = parts[1].substringBefore('?')
                    val file = currentPhotoFile
                    val prefix = "/$accessToken/"

                    when {
                        accessToken.isEmpty() || !path.startsWith(prefix) -> serveNotFound(out)
                        path == "${prefix}photo.jpg" -> servePhoto(file, out)
                        path == prefix -> serveHtmlPage(file, out)
                        else -> serveNotFound(out)
                    }
                    out.flush()
                } catch (e: Exception) {
                    Log.w(TAG, "Error serving client: ${e.message}")
                } finally {
                    try {
                        socket.close()
                    } catch (ignored: Exception) {}
                }
            }
        } catch (e: Exception) {
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    private fun serveNotFound(out: OutputStream) {
        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
    }

    private fun servePhoto(file: File?, out: OutputStream) {
        if (file == null || !file.exists()) {
            serveNotFound(out)
            return
        }

        val length = file.length()
        val headers = ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: image/jpeg\r\n" +
                "Content-Length: $length\r\n" +
                "Content-Disposition: attachment; filename=\"conserto_photobooth.jpg\"\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n").toByteArray()
        out.write(headers)

        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                out.write(buffer, 0, bytesRead)
            }
        }
    }

    private fun serveHtmlPage(file: File?, out: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Conserto - Pepper Photobooth</title>
                <style>
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: #0B0F19;
                        color: #F8FAFC;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        min-height: 100vh;
                        padding: 20px;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 24px;
                    }
                    .logo {
                        font-size: 28px;
                        font-weight: 800;
                        letter-spacing: 2px;
                        color: #06B6D4;
                        margin-bottom: 4px;
                    }
                    .subtitle {
                        font-size: 14px;
                        color: #94A3B8;
                    }
                    .card {
                        background: #1E293B;
                        border-radius: 16px;
                        padding: 16px;
                        max-width: 520px;
                        width: 100%;
                        box-shadow: 0 10px 30px rgba(0,0,0,0.5);
                        border: 1px solid #334155;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                    }
                    .photo-container {
                        width: 100%;
                        border-radius: 12px;
                        overflow: hidden;
                        margin-bottom: 20px;
                        box-shadow: 0 4px 15px rgba(0,0,0,0.3);
                    }
                    .photo-container img {
                        width: 100%;
                        height: auto;
                        display: block;
                    }
                    .btn-download {
                        background: linear-gradient(135deg, #06B6D4 0%, #3B82F6 100%);
                        color: white;
                        text-decoration: none;
                        font-size: 16px;
                        font-weight: 600;
                        padding: 14px 28px;
                        border-radius: 30px;
                        display: inline-block;
                        transition: transform 0.2s, opacity 0.2s;
                        box-shadow: 0 4px 14px rgba(6, 182, 212, 0.4);
                        text-align: center;
                        width: 90%;
                    }
                    .btn-download:hover {
                        opacity: 0.95;
                        transform: translateY(-2px);
                    }
                    .footer {
                        margin-top: 30px;
                        font-size: 12px;
                        color: #64748B;
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <div class="logo">CONSERTO</div>
                    <div class="subtitle">Pepper Photobooth Souvenir 🤖</div>
                </div>
                <div class="card">
                    <div class="photo-container">
                        <img src="photo.jpg" alt="Photo Conserto Photobooth">
                    </div>
                    <a href="photo.jpg" download="conserto_photobooth.jpg" class="btn-download">
                        📥 Télécharger la photo
                    </a>
                </div>
                <div class="footer">
                    Propulsé par Pepper Robot & Conserto
                </div>
            </body>
            </html>
        """.trimIndent()

        val htmlBytes = html.toByteArray(Charsets.UTF_8)
        val headers = ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${htmlBytes.size}\r\n" +
                "Connection: close\r\n\r\n").toByteArray()
        out.write(headers)
        out.write(htmlBytes)
    }

    /**
     * Resolves the Wi-Fi/local IPv4 address of the Android tablet.
     * Filters out loopback (127.0.0.1) and Pepper NAOqi internal USB bridge (198.18.0.x).
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            var candidateIp: String? = null

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        // Exclude Pepper's internal USB bridge to robot head/torso (198.18.0.2)
                        if (host.startsWith("198.18.0.")) continue

                        // Prefer Wi-Fi interface (wlan0) or standard private subnets
                        if (networkInterface.name.contains("wlan", ignoreCase = true) ||
                            host.startsWith("192.168.") ||
                            host.startsWith("10.") ||
                            host.startsWith("172.")) {
                            return host
                        }
                        candidateIp = host
                    }
                }
            }
            return candidateIp
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP address", e)
            return null
        }
    }
}
