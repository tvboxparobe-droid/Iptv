package br.conhecimentosrap.iptv

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
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var label: TextView
    private var idx = 0
    private val handler = Handler(Looper.getMainLooper())
    private val hideLabel = Runnable { label.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val vod = PlayerState.vod
        val view = PlayerView(this).apply {
            useController = vod
            isFocusable = true
        }
        label = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            setBackgroundColor(Color.parseColor("#B0000000"))
            setPadding(32, 16, 32, 16)
        }
        root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START).apply {
            setMargins(48, 0, 0, 48)
        })
        setContentView(root)

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Linux; Android 11) IPTVLivre/1.0")
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                show(if (PlayerState.vod) "Não foi possível reproduzir este título." else "Canal indisponível. Use ↑ / ↓ para trocar de canal.", false)
            }
        })
        view.player = p
        player = p
        view.requestFocus()
        idx = PlayerState.index
        playCurrent()
    }

    private fun playCurrent() {
        val list = PlayerState.list
        if (list.isEmpty()) { finish(); return }
        val ch = list[idx]
        player?.apply {
            setMediaItem(MediaItem.fromUri(ch.url))
            prepare()
            playWhenReady = true
        }
        show(if (PlayerState.vod) ch.name else "${idx + 1}/${list.size}  ${ch.name}", true)
    }

    private fun show(text: String, autoHide: Boolean) {
        label.text = text
        label.visibility = View.VISIBLE
        handler.removeCallbacks(hideLabel)
        if (autoHide) handler.postDelayed(hideLabel, 3000)
    }

    private fun zap(step: Int) {
        val n = PlayerState.list.size
        if (n == 0) return
        idx = (idx + step + n) % n
        playCurrent()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!PlayerState.vod && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(1); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    show(PlayerState.list[idx].name, true); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() { super.onStart(); player?.play() }
    override fun onStop() { super.onStop(); player?.pause() }
    override fun onDestroy() {
        handler.removeCallbacks(hideLabel)
        player?.release()
        player = null
        super.onDestroy()
    }
}
