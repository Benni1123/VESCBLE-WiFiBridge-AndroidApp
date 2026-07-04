package eu.benni1123.vescbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun checkForUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(versionUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                val versionText = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                val latestCode = versionText.toIntOrNull() ?: 0
                val currentCode = getCurrentVersionCode()
                
                if (latestCode > currentCode) {
                    AppUpdateInfo(
                        latestVersionCode = latestCode,
                        downloadUrl = apkUrl
                    )
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadAndInstall(downloadUrl: String, onProgress: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true // Wichtig für GitHub Releases
            val totalSize = conn.contentLength
            val apkFile = File(context.externalCacheDir, "update.apk")
            
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
            
            installApk(apkFile)
            true
        } catch (e: Exception) {
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
