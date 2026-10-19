package io.github.autoskip

/**
 * The apps AutoSkip works in. Both use YouTube's video player, so the same detection rules
 * apply to each. Adding an app here also needs its package in res/xml/autoskip_service.xml
 * (so Android sends its events) and in the <queries> block of AndroidManifest.xml.
 */
enum class WatchedApp(val packageName: String, val key: String) {
    YOUTUBE("com.google.android.youtube", "youtube"),
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music", "youtube_music");

    companion object {
        fun of(packageName: CharSequence?): WatchedApp? {
            if (packageName == null) return null
            val name = packageName.toString()
            return entries.firstOrNull { it.packageName == name }
        }
    }
}
