package eu.benni1123.vescbridge

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val latestVersionCode: Int,
    val downloadUrl: String
)

class AppUpdater(private val context: Context) {

    // Passen Sie diese URLs an Ihr GitHub-Repository an
    private val versionUrl = "https://github.com/Benni1123/VESCBLE-WiFiBridge-AndroidApp/releases/latest/download/version.txt"
    private val apkUrl = "https://github.com/Benni1123/VESCBLE-WiFiBridge-AndroidApp/releases/latest/download/app-release.apk"

    private fun getCurrentVersionCode(): Int {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.longVersionCode.toInt()
        } catch (_: Exception) {
            0
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        // Wir suchen ein Netzwerk mit Internet-Kapazität. Falls wir gerade an den
        // VescBridge-AP gebunden sind (der kein Internet hat), würde URL.openConnection()
        // fehlschlagen. network.openConnection(url) umgeht die Prozess-Bindung.
        @Suppress("DEPRECATION")
        val internetNetwork = cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        
        val urlObj = URL(url)
        val conn = if (internetNetwork != null) {
            internetNetwork.openConnection(urlObj)
        } else {
            urlObj.openConnection()
        }
        return conn as HttpURLConnection
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AppUpdater", "Checking for update at $versionUrl")
            val conn = openConnection(versionUrl)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                val versionText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                val latestCode = versionText.toIntOrNull() ?: 0
                val currentCode = getCurrentVersionCode()
                
                android.util.Log.d("AppUpdater", "Latest: $latestCode, Current: $currentCode")
                
                if (latestCode > currentCode) {
                    AppUpdateInfo(
                        latestVersionCode = latestCode,
                        downloadUrl = apkUrl
                    )
                } else null
            } else {
                android.util.Log.e("AppUpdater", "Version check failed: ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUpdater", "Error checking for update", e)
            null
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("AppUpdater", "Downloading update from $downloadUrl")
            val conn = openConnection(downloadUrl)
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            val totalSize = conn.contentLength
            val cacheDir = context.externalCacheDir ?: context.cacheDir
            val apkFile = File(cacheDir, "update.apk")
            
            if (apkFile.exists()) apkFile.delete()
            
            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloaded = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalSize > 0) {
                            onProgress(downloaded.toFloat() / totalSize)
                        }
                    }
                }
            }
            
            android.util.Log.d("AppUpdater", "Download complete, installing...")
            installApk(apkFile)
            true
        } catch (e: Exception) {
            android.util.Log.e("AppUpdater", "Error during download/install", e)
            false
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
