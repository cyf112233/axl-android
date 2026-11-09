package red.ghs.axolotl

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The launcher is laid out for the default desktop window (1280x800) and the
 * activity is locked to landscape, so the WebView only has to report that width
 * to the page. Wide viewport support is what makes the `width` in the viewport
 * meta take effect; without it the page would always lay out at device width
 * (~360 CSS px) and every desktop layout would collapse.
 *
 * The page itself widens the viewport to 1280 and letterboxes a 1280x800 canvas
 * (see `apps/app-frontend/index.html`), so nothing else is patched here.
 */
class MainActivity : TauriActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    // A launcher is a full-screen surface: the status bar and the navigation
    // buttons would only eat into the layout. They stay reachable with a swipe
    // from the edge (transient bars).
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
      hide(WindowInsetsCompat.Type.systemBars())
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
  }

  override fun onWebViewCreate(webView: WebView) {
    super.onWebViewCreate(webView)

    webView.settings.apply {
      useWideViewPort = true
      // Scale the 1280 px layout down to the screen width instead of leaving
      // the page scrollable sideways.
      loadWithOverviewMode = true

      // The launcher is an application, not a document: pinch zoom, the zoom
      // buttons overlay and double-tap zoom all fight with the app's own
      // layout, which is already scaled by the canvas in the frontend.
      setSupportZoom(false)
      builtInZoomControls = false
      displayZoomControls = false

      // Avatar and skin textures are served over plain http by Mojang while
      // the app itself runs on https, which WebView blocks by default.
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    }

    // Long press would open the text selection handles and the "copy / open
    // link" context menu; dragging would ghost a screenshot of the page.
    // Both are browser affordances that a desktop app does not have.
    webView.isLongClickable = false
    webView.isHapticFeedbackEnabled = false
    webView.overScrollMode = View.OVER_SCROLL_NEVER
    webView.setOnLongClickListener { true }
    webView.setOnDragListener { _, _ -> true }
  }
}
