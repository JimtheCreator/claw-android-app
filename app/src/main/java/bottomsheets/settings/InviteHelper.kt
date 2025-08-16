package bottomsheets.settings

import android.content.Context
import android.content.Intent
import android.util.Log
import com.claw.ai.R
import java.io.File

object InviteHelper {
    private const val TAG = "InviteHelper"

    interface ShareCallback {
        fun onShareStarted()
        fun onShareCompleted()
        fun onShareFailed(error: String)
    }

    fun shareInvite(context: Context, callback: ShareCallback? = null) {
        callback?.onShareStarted()

        try {
            val inviteMessage = generateInviteMessage(context)
            shareTextOnly(context, inviteMessage)
            callback?.onShareCompleted()
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing invite", e)
            callback?.onShareFailed("Unable to open share dialog")
        }
    }


    private fun generateInviteMessage(context: Context): String {
        return context.getString(R.string.invite_message)
    }

    private fun shareTextOnly(context: Context, message: String) {
        Log.d(TAG, "Sharing text only")

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share Watchers")
        context.startActivity(chooserIntent)
    }

    // Cleanup old cached images (call this periodically)
    fun cleanupCachedImages(context: Context) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            if (cachePath.exists()) {
                val files = cachePath.listFiles()
                files?.forEach { file ->
                    if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) { // 24 hours
                        file.delete()
                        Log.d(TAG, "Deleted old cached image: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up cached images", e)
        }
    }
}