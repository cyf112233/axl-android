package red.ghs.axolotl

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Chrome's page zoom reflows the page: the layout viewport itself gets wider,
 * so components re-arrange instead of just being scaled down. The launcher is
 * built for a desktop-width viewport, so on a phone we reproduce that by
 * widening the viewport meta to `innerWidth / PAGE_ZOOM` and scaling the
 * visual result by the same factor.
 *
 * Everything else that differs on a phone is handled in the frontend, behind
 * platform checks, so this class stays limited to the window itself.
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
    // Without this the WebView ignores the width in the viewport meta and
    // always lays out at device-width.
    webView.settings.useWideViewPort = true
    webView.settings.loadWithOverviewMode = false
    installPageZoom(webView)
  }

  private fun installPageZoom(webView: WebView) {
    val script =
      """
      (function () {
        if (window.__axolotlPageZoom) { return; }
        var head = document.head || document.getElementsByTagName('head')[0];
        if (!head) { return; }
        var zoom = $PAGE_ZOOM;
        var width = Math.round(window.innerWidth / zoom);
        var meta = document.querySelector('meta[name="viewport"]');
        if (!meta) {
          meta = document.createElement('meta');
          meta.setAttribute('name', 'viewport');
          head.appendChild(meta);
        }
        meta.setAttribute('content', 'width=' + width + ', initial-scale=' + zoom);
        window.__axolotlPageZoom = true;
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
    /** 70%: a 360 dp screen lays out at roughly 514 CSS px. */
    const val PAGE_ZOOM = 0.7
  }
}
