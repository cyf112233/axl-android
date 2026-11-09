package red.ghs.axolotl

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The launcher is laid out for the default desktop window (1280x800). A phone
 * reports ~360 CSS px, which collapses every desktop layout, so the WebView
 * layout viewport is widened to [DESKTOP_WIDTH] and the WebView scales the page
 * down to fit the screen width. Media queries, flex and grid then resolve to the
 * same values they do on the desktop window instead of the phone breakpoints.
 *
 * Nothing else about the UI is patched here; platform differences live in the
 * frontend behind explicit Android checks.
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

    // `width` in the viewport meta is ignored unless wide viewport support is
    // on, and overview mode is what turns "1280 px wide" into "scale to fit the
    // screen width" instead of leaving the page scrollable sideways.
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = true
    installDesktopViewport(webView)
  }

  private fun installDesktopViewport(webView: WebView) {
    val script =
      """
      (function () {
        if (window.__axolotlDesktopViewport) { return; }
        var head = document.head || document.getElementsByTagName('head')[0];
        if (!head) { return; }
        var meta = document.querySelector('meta[name="viewport"]');
        if (!meta) {
          meta = document.createElement('meta');
          meta.setAttribute('name', 'viewport');
          head.appendChild(meta);
        }
        // No initial-scale: the WebView derives it from the width, so the
        // 1280 px layout fits the screen width exactly.
        meta.setAttribute('content', 'width=$DESKTOP_WIDTH, user-scalable=yes');
        window.__axolotlDesktopViewport = true;
      })();
      """
        .trimIndent()

    // The document does not exist yet when the webview is created, so retry
    // until the head is there. The script is idempotent.
    for (delay in longArrayOf(0, 200, 600, 1200, 2500, 5000)) {
      webView.postDelayed({ runCatching { webView.evaluateJavascript(script, null) } }, delay)
    }
  }

  private companion object {
    /** Width of the default desktop window in `tauri.conf.json`. */
    const val DESKTOP_WIDTH = 1280
  }
}
