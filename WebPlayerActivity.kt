package br.conhecimentosrap.iptv

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

class WebPlayerActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var label: TextView
    private var idx = 0
    private var watchPage = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()
        }
        label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            setBackgroundColor(Color.parseColor("#B0000000"))
            setPadding(32, 16, 32, 16)
        }
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            setMargins(48, 48, 0, 0)
        })
        setContentView(root)
        idx = PlayerState.index
        loadCurrent()
    }

    private fun urlFor(u: String): String = when {
        u.startsWith("ytv:") -> {
            val id = u.removePrefix("ytv:")
            if (watchPage) "https://www.youtube.com/watch?v=$id"
            else "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&rel=0"
        }
        u.startsWith("yt:") ->
            "https://www.youtube.com/embed/live_stream?channel=" + u.removePrefix("yt:") + "&autoplay=1&playsinline=1&rel=0"
        else -> {
            val p = u.removePrefix("ytl:")
            "https://www.youtube.com/" + (if (p.startsWith("UC")) "channel/$p" else p) + "/live"
        }
    }

    private fun loadCurrent() {
        val list = PlayerState.list
        if (list.isEmpty()) { finish(); return }
        val ch = list[idx]
        web.loadUrl(urlFor(ch.url), mapOf("Referer" to "https://br.conhecimentosrap.iptv"))
        label.text = "${idx + 1}/${list.size}  ${ch.name}   (↑↓ troca · MENU alterna player/página)"
        label.visibility = View.VISIBLE
        handler.removeCallbacks(hideLabel)
        handler.postDelayed(hideLabel, 4000)
    }

    private fun zap(step: Int) {
        val n = PlayerState.list.size
        if (n == 0) return
        idx = (idx + step + n) % n
        watchPage = false
        loadCurrent()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_PROG_RED -> {
                    watchPage = !watchPage
                    loadCurrent()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() {
        handler.removeCallbacks(hideLabel)
        web.loadUrl("about:blank")
        web.destroy()
        super.onDestroy()
    }
}
