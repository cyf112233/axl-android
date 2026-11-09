package red.ghs.axolotl

import android.os.Bundle
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
    webView.settings.useWideViewPort = true
    // Scale the 1280 px layout down to the screen width instead of leaving the
    // page scrollable sideways.
    webView.settings.loadWithOverviewMode = true
  }
}
