package br.conhecimentosrap.iptv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private val bgColor = Color.parseColor("#0F1115")
    private val cardColor = Color.parseColor("#1B1F27")
    private val accent = Color.parseColor("#7C5CFF")

    private val base = "https://iptv-org.github.io/iptv/"
    private val liveUrls = arrayOf(
        base + "index.country.m3u",
        base + "categories/movies.m3u",
        base + "categories/series.m3u"
    )
    private val titles = arrayOf("TV ao vivo", "Filmes", "Séries", "Cinema TV", "Canais YT", "Ao vivo YT", "Filmes YT", "★ Favoritos")
    private val nTabs = 8

    // (rotulo, consulta no Internet Archive)
    private val filmes = listOf(
        "Populares" to "collection:feature_films",
        "Em português" to "collection:feature_films AND language:(Portuguese OR por)",
        "Terror" to "collection:feature_films AND subject:horror",
        "Ficção científica" to "collection:feature_films AND subject:(sci-fi OR \"science fiction\")",
        "Comédia" to "collection:feature_films AND subject:comedy",
        "Faroeste" to "collection:feature_films AND subject:western",
        "Animação" to "collection:animationandcartoons"
    )
    private val series = listOf(
        "Populares" to "collection:classic_tv",
        "Comédia" to "collection:classic_tv AND subject:comedy",
        "Desenhos animados" to "collection:animationandcartoons",
        "Documentários" to "collection:documentaries"
    )
    // (rotulo, "busca|pais|idioma|modo") - modo live = AO VIVO agora no YouTube
    private val discover = listOf(
        "Brasil · Tudo ao vivo" to "ao vivo|BR|pt-BR|live",
        "Brasil · Futebol" to "futebol ao vivo|BR|pt-BR|live",
        "Brasil · Jogos de hoje" to "jogo ao vivo hoje|BR|pt-BR|live",
        "Brasil · Rádio" to "rádio ao vivo|BR|pt-BR|live",
        "Brasil · Notícias" to "notícias ao vivo|BR|pt-BR|live",
        "Brasil · TV 24h" to "tv ao vivo 24 horas|BR|pt-BR|live",
        "Rio Grande do Sul" to "ao vivo rio grande do sul|BR|pt-BR|live",
        "Grêmio e Inter" to "grêmio inter ao vivo|BR|pt-BR|live",
        "Brasil · Música e shows" to "show ao vivo música|BR|pt-BR|live",
        "Argentina · En vivo" to "en vivo|AR|es-AR|live",
        "Argentina · Noticias" to "noticias en vivo|AR|es-AR|live",
        "Argentina · Fútbol" to "fútbol en vivo|AR|es-AR|live",
        "Chile · En vivo" to "en vivo|CL|es-CL|live",
        "Chile · Noticias" to "noticias en vivo|CL|es-CL|live",
        "Chile · Fútbol" to "fútbol en vivo|CL|es-CL|live",
        "Polônia · Na żywo" to "na żywo|PL|pl|live",
        "Polônia · Wiadomości" to "wiadomości na żywo|PL|pl|live",
        "Polônia · Sport" to "sport na żywo|PL|pl|live",
        "México · En vivo" to "en vivo|MX|es-MX|live",
        "México · Noticias" to "noticias en vivo|MX|es-MX|live",
        "Colômbia · En vivo" to "en vivo|CO|es-CO|live",
        "Peru · En vivo" to "en vivo|PE|es-PE|live",
        "Uruguai · En vivo" to "en vivo|UY|es-UY|live",
        "Paraguai · En vivo" to "en vivo|PY|es-PY|live",
        "Venezuela · En vivo" to "en vivo|VE|es-VE|live",
        "Equador · En vivo" to "en vivo|EC|es-EC|live",
        "Bolívia · En vivo" to "en vivo|BO|es-BO|live",
        "Espanha · En directo" to "en directo|ES|es-ES|live",
        "Portugal · Direto" to "direto|PT|pt-PT|live",
        "EUA · Live" to "live|US|en|live",
        "EUA · News" to "live news|US|en|live",
        "EUA · Sports" to "live sports|US|en|live",
        "Reino Unido · Live" to "live|GB|en-GB|live",
        "Canadá · Live" to "live|CA|en|live",
        "Austrália · Live" to "live|AU|en-AU|live",
        "França · En direct" to "en direct|FR|fr|live",
        "Alemanha · Live" to "live nachrichten|DE|de|live",
        "Itália · Diretta" to "diretta live|IT|it|live",
        "Holanda · Livestream" to "livestream|NL|nl|live",
        "Suécia · Livesändning" to "livesändning|SE|sv|live",
        "Grécia · Ζωντανά" to "ζωντανά|GR|el|live",
        "Romênia · În direct" to "în direct|RO|ro|live",
        "Hungria · Élő" to "élő adás|HU|hu|live",
        "Rep. Tcheca · Živě" to "živě|CZ|cs|live",
        "Turquia · Canlı" to "canlı yayın|TR|tr|live",
        "Rússia · Прямой эфир" to "прямой эфир|RU|ru|live",
        "Ucrânia · Пряма трансляція" to "пряма трансляція|UA|uk|live",
        "Índia · Live" to "live|IN|en-IN|live",
        "Índia · लाइव" to "लाइव|IN|hi|live",
        "Japão · ライブ" to "ライブ配信|JP|ja|live",
        "Coreia · 라이브" to "라이브|KR|ko|live",
        "Indonésia · Siaran langsung" to "siaran langsung|ID|id|live",
        "Tailândia · ถ่ายทอดสด" to "ถ่ายทอดสด|TH|th|live",
        "Vietnã · Trực tiếp" to "trực tiếp|VN|vi|live",
        "Filipinas · Live" to "live|PH|en-PH|live",
        "Egito · بث مباشر" to "بث مباشر|EG|ar|live",
        "Arábia Saudita · بث مباشر" to "بث مباشر|SA|ar|live",
        "Marrocos · مباشر" to "مباشر|MA|ar|live",
        "Israel · שידור חי" to "שידור חי|IL|he|live",
        "Nigéria · Live" to "live|NG|en|live",
        "África do Sul · Live" to "live|ZA|en-ZA|live",
        "Quênia · Live" to "live|KE|en|live",
        "24/7 streams" to "24/7 live|US|en|live",
        "Espaço" to "space launch live NASA|US|en|live",
        "Câmeras ao vivo" to "live cam|US|en|live",
        "Lofi e rádio 24/7" to "lofi radio live 24/7|US|en|live"
    )
    // modo cc = licenca Creative Commons (uso livre); long = videos longos (>20 min)
    private val movies = listOf(
        "Grátis legal (CC) · Filmes completos" to "filme completo|BR|pt-BR|cc",
        "Grátis legal (CC) · Full movies" to "full movie|US|en|cc",
        "Grátis legal (CC) · Películas completas" to "película completa|MX|es-MX|cc",
        "Grátis legal (CC) · Films complets" to "film complet|FR|fr|cc",
        "Grátis legal (CC) · Pełne filmy" to "pełny film|PL|pl|cc",
        "Grátis legal (CC) · Spielfilme" to "ganzer film|DE|de|cc",
        "Grátis legal (CC) · Documentários" to "documentário completo|BR|pt-BR|cc",
        "Grátis legal (CC) · Animação" to "animação completa|BR|pt-BR|cc",
        "Filmes completos dublados" to "filme completo dublado|BR|pt-BR|long",
        "Filmes completos legendados" to "filme completo legendado|BR|pt-BR|long",
        "Filmes nacionais completos" to "filme brasileiro completo|BR|pt-BR|long",
        "Ação completo" to "filme de ação completo dublado|BR|pt-BR|long",
        "Comédia completo" to "filme de comédia completo dublado|BR|pt-BR|long",
        "Terror completo" to "filme de terror completo dublado|BR|pt-BR|long",
        "Ficção científica completo" to "ficção científica filme completo dublado|BR|pt-BR|long",
        "Drama completo" to "filme drama completo dublado|BR|pt-BR|long",
        "Romance completo" to "filme romance completo dublado|BR|pt-BR|long",
        "Faroeste completo" to "faroeste filme completo dublado|BR|pt-BR|long",
        "Clássicos completos" to "filme clássico completo|BR|pt-BR|long",
        "Documentários completos" to "documentário completo|BR|pt-BR|long",
        "Full movie (English)" to "full movie|US|en|long",
        "Película completa (Español)" to "película completa en español|MX|es-MX|long",
        "Película completa (Argentina)" to "película completa|AR|es-AR|long",
        "Film complet (Français)" to "film complet en français|FR|fr|long",
        "Pełny film (Polski)" to "pełny film lektor pl|PL|pl|long",
        "Ganzer Film (Deutsch)" to "ganzer film deutsch|DE|de|long",
        "Film completo (Italiano)" to "film completo in italiano|IT|it|long",
        "Tam film (Türkçe)" to "tam film|TR|tr|long"
    )

    private val data = arrayOfNulls<List<Channel>>(nTabs)
    private val loading = BooleanArray(nTabs)
    private val reqId = IntArray(nTabs)
    private val loadedQ = arrayOfNulls<String>(nTabs)
    private val loadedAt = LongArray(nTabs)
    private var countries: List<Pair<String, String>> = emptyList()
    private val sel = arrayOf("Todos", filmes[0].second, series[0].second, "", "Ao vivo agora", discover[0].second, movies[0].second, "")
    private var tab = 0
    private var query = ""
    private val handler = Handler(Looper.getMainLooper())
    private val searchRun = Runnable { load(tab, true); refresh() }
    private val tick = object : Runnable {
        override fun run() {
            if (tab == 4) load(4, true) else if (tab == 5) load(5, true)
            handler.postDelayed(this, 4 * 60 * 1000L)
        }
    }

    // estado dos canais do YouTube: handle -> id do video ao vivo, "" (fora do ar) ou "#404"
    private val ytState = ConcurrentHashMap<String, String>()
    private var ytRunning = false
    private var liveCount = 0
    private var favKeys = HashSet<String>()

    private lateinit var tabViews: List<TextView>
    private lateinit var countryList: RecyclerView
    private lateinit var grid: RecyclerView
    private lateinit var status: TextView
    private val chAdapter = ChAdapter()
    private val coAdapter = CoAdapter()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun isVod(t: Int) = t == 1 || t == 2
    private fun serverSearch(t: Int) = t == 1 || t == 2 || t == 5 || t == 6

    private fun shape(fill: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(12).toFloat()
        if (stroke != 0) setStroke(dp(3), stroke)
    }

    private fun focusBg(): StateListDrawable = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_focused), shape(Color.parseColor("#2A2F3B"), accent))
        addState(intArrayOf(android.R.attr.state_selected), shape(Color.parseColor("#4A3A99")))
        addState(intArrayOf(), shape(cardColor))
    }

    private fun options(): List<Pair<String, String>> = when (tab) {
        0 -> countries
        1 -> filmes.map { it.second to it.first }
        2 -> series.map { it.second to it.first }
        4 -> listOf("Ao vivo agora" to "● Ao vivo agora ($liveCount)", "Todos" to "Todos os canais") +
            YouTubeLive.catalog.map { it.group }.distinct().map { it to it }
        5 -> discover.map { it.second to it.first }
        6 -> movies.map { it.second to it.first }
        else -> emptyList()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(32), dp(20), dp(32), dp(12))
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "IPTV"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(accent)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(20) })

        tabViews = titles.mapIndexed { i, t ->
            TextView(this).apply {
                text = t
                textSize = 14f
                setTextColor(Color.WHITE)
                setPadding(dp(9), dp(10), dp(9), dp(10))
                isFocusable = true
                isClickable = true
                background = focusBg()
                setOnClickListener { selectTab(i) }
            }
        }
        val tabsRow = LinearLayout(this)
        tabViews.forEach {
            tabsRow.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(4) })
        }
        header.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabsRow)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        val search = EditText(this).apply {
            hint = "Buscar"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = focusBg()
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    if (serverSearch(tab)) {
                        handler.removeCallbacks(searchRun)
                        handler.postDelayed(searchRun, 700)
                    } else refresh()
                }
            })
        }
        header.addView(search, LinearLayout.LayoutParams(dp(140), ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        countryList = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = coAdapter
        }
        body.addView(countryList, LinearLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = dp(12) })

        val span = maxOf(3, (resources.displayMetrics.widthPixels - dp(64 + 262)) / dp(170))
        grid = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = chAdapter
        }
        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        val frame = FrameLayout(this)
        frame.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(status, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        body.addView(frame, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        favKeys = Favs.load(this).map { Favs.keyOf(it) }.toHashSet()
        setContentView(root)
        selectTab(0)
        tabViews[0].requestFocus()
        handler.postDelayed(tick, 4 * 60 * 1000L)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun selectTab(i: Int) {
        tab = i
        tabViews.forEachIndexed { idx, v -> v.isSelected = idx == i }
        val old = System.currentTimeMillis() - loadedAt[i] > 3 * 60 * 1000L
        val stale = when (i) {
            1, 2, 6 -> data[i] != null && loadedQ[i] != query
            5 -> data[i] != null && (loadedQ[i] != query || old)
            4 -> old
            else -> false
        }
        load(i, stale)
        refresh()
    }

    private fun load(i: Int, force: Boolean = false) {
        if (i == 7) {
            if (Favs.load(this).any { it.url.startsWith("ytl:") }) checkYt(false)
            return
        }
        if (i == 4) { checkYt(force); return }
        if (!force && (data[i] != null || loading[i])) return
        val my = ++reqId[i]
        loading[i] = true
        if (force) data[i] = null
        val q = sel[i]
        val text = query
        Thread {
            var result: List<Channel>? = null
            var co: List<Pair<String, String>>? = null
            try {
                when (i) {
                    0 -> {
                        val list = M3u.parse(M3u.fetch(this, liveUrls[0], "pl0.m3u"))
                        val counts = list.groupingBy { it.group }.eachCount()
                        val sorted = counts.entries.sortedWith(
                            compareBy({ if (it.key == "Brazil") 0 else 1 }, { it.key })
                        )
                        co = listOf("Todos" to "Todos (${list.size})") +
                            sorted.map { it.key to "${it.key} (${it.value})" }
                        result = list
                    }
                    3 -> result = M3u.parse(M3u.fetch(this, liveUrls[1], "pl1.m3u")) +
                        M3u.parse(M3u.fetch(this, liveUrls[2], "pl2.m3u"))
                    5, 6 -> {
                        val p = q.split("|")
                        result = YtSearch.search(if (text.isBlank()) p[0] else text, p[1], p[2], p[3])
                    }
                    else -> result = Archive.search(q, text)
                }
            } catch (e: Exception) {
                // o texto de status explica a falha
            }
            runOnUiThread {
                if (my == reqId[i]) {
                    loading[i] = false
                    if (result != null) {
                        data[i] = result
                        loadedQ[i] = text
                        loadedAt[i] = System.currentTimeMillis()
                        if (co != null) countries = co
                    }
                    if (i == tab) refresh()
                }
            }
        }.start()
    }

    // Verifica quais canais do YouTube estao ao vivo agora
    private fun checkYt(force: Boolean) {
        if (ytRunning) return
        if (!force && data[4] != null) return
        ytRunning = true
        loading[4] = true
        val items = YouTubeLive.catalog
        val left = AtomicInteger(items.size)
        val pool = Executors.newFixedThreadPool(10)
        for (ch in items) {
            pool.execute {
                val h = ch.url.removePrefix("ytl:")
                val r = try { YtLive.check(h) } catch (e: Exception) { "" }
                ytState[h] = r ?: "#404"
                val remaining = left.decrementAndGet()
                if (remaining == 0 || remaining % 20 == 0) {
                    runOnUiThread {
                        if (remaining == 0) {
                            ytRunning = false
                            loading[4] = false
                            loadedAt[4] = System.currentTimeMillis()
                        }
                        rebuildYt()
                        if (tab == 4 || tab == 7) refresh()
                    }
                }
                if (remaining == 0) pool.shutdown()
            }
        }
    }

    private fun rebuildYt() {
        val seen = HashSet<String>()
        val live = ArrayList<Channel>()
        val off = ArrayList<Channel>()
        for (ch in YouTubeLive.catalog) {
            val h = ch.url.removePrefix("ytl:")
            val st = ytState[h] ?: continue
            if (st == "#404") continue
            if (st.isNotEmpty()) {
                if (!seen.add(st)) continue
                live.add(Channel("● " + ch.name, "ytv:$st", "https://i.ytimg.com/vi/$st/mqdefault.jpg", ch.group))
            } else {
                off.add(Channel(ch.name + " (offline)", ch.url, "", ch.group))
            }
        }
        liveCount = live.size
        data[4] = live + off
    }

    private fun refresh() {
        countryList.visibility = if (tab == 3 || tab == 7) View.GONE else View.VISIBLE
        coAdapter.items = options()
        coAdapter.notifyDataSetChanged()
        val all = if (tab == 7) buildFavs() else data[tab]
        if (all == null) {
            chAdapter.items = emptyList()
            chAdapter.notifyDataSetChanged()
            status.text = if (loading[tab]) {
                if (tab == 4) "Verificando canais ao vivo…" else "Carregando…"
            } else "Falha ao carregar. Verifique a internet e selecione a aba de novo."
            status.visibility = View.VISIBLE
            return
        }
        var l = all
        if (tab == 0 && sel[0] != "Todos") l = l.filter { it.group == sel[0] }
        if (tab == 4) {
            l = when (sel[4]) {
                "Todos" -> l
                "Ao vivo agora" -> l.filter { it.url.startsWith("ytv:") }
                else -> l.filter { it.group == sel[4] }
            }
        }
        if (!serverSearch(tab) && query.isNotBlank()) l = l.filter { it.name.contains(query, true) }
        chAdapter.items = l
        chAdapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        status.text = if (tab == 7) "Nenhum favorito ainda.\nSegure o botão OK sobre um canal ou título para favoritar."
        else if (loading[tab]) "Verificando…" else "Nada encontrado"
        status.visibility = if (l.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildFavs(): List<Channel> = Favs.load(this).map { f ->
        if (f.url.startsWith("ytl:")) {
            val st = ytState[f.url.removePrefix("ytl:")] ?: ""
            if (st.isNotEmpty() && st != "#404")
                Channel("● " + f.name, "ytv:$st", "https://i.ytimg.com/vi/$st/mqdefault.jpg", f.group, "", f.url)
            else f.copy(favUrl = f.url)
        } else f
    }

    private fun toggleFav(c: Channel) {
        val fc: Channel? = when {
            c.favUrl.isNotEmpty() -> Channel(c.name.removePrefix("● ").removeSuffix(" (offline)"), c.favUrl, "", c.group)
            c.url.startsWith("ytv:") && tab != 6 && tab != 7 -> null
            else -> c
        }
        if (fc == null) {
            Toast.makeText(this, "Esta live é temporária. Favorite o canal na aba Canais YT.", Toast.LENGTH_LONG).show()
            return
        }
        val added = Favs.toggle(this, fc)
        favKeys = Favs.load(this).map { Favs.keyOf(it) }.toHashSet()
        Toast.makeText(this, if (added) "★ Adicionado aos favoritos" else "Removido dos favoritos", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun play(pos: Int) {
        val c = chAdapter.items.getOrNull(pos) ?: return
        if (c.vodId.isNotEmpty()) {
            Toast.makeText(this, "Abrindo ${c.name}…", Toast.LENGTH_SHORT).show()
            Thread {
                val url = try { Archive.resolve(c.vodId) } catch (e: Exception) { null }
                runOnUiThread {
                    if (url == null) {
                        Toast.makeText(this, "Título indisponível", Toast.LENGTH_LONG).show()
                    } else {
                        PlayerState.list = listOf(Channel(c.name, url, "", ""))
                        PlayerState.index = 0
                        PlayerState.vod = true
                        startActivity(Intent(this, PlayerActivity::class.java))
                    }
                }
            }.start()
        } else if (c.url.startsWith("yt")) {
            PlayerState.list = chAdapter.items
            PlayerState.index = pos
            startActivity(Intent(this, WebPlayerActivity::class.java))
        } else {
            PlayerState.list = chAdapter.items
            PlayerState.index = pos
            PlayerState.vod = false
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }

    inner class ChAdapter : RecyclerView.Adapter<ChAdapter.VH>() {
        var items: List<Channel> = emptyList()

        inner class VH(val card: LinearLayout, val img: ImageView, val txt: TextView) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val card = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isFocusable = true
                isClickable = true
                background = focusBg()
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(6), dp(6), dp(6), dp(6))
                }
            }
            val img = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            card.addView(img, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(96)))
            val txt = TextView(this@MainActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 2
                minLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(txt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            return VH(card, img, txt)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val c = items[position]
            h.txt.text = (if (favKeys.contains(Favs.keyOf(c))) "★ " else "") + c.name
            if (c.logo.isBlank()) {
                h.img.dispose()
                h.img.setImageResource(android.R.drawable.ic_media_play)
            } else h.img.load(c.logo)
            h.card.setOnClickListener { play(h.adapterPosition) }
            h.card.setOnLongClickListener {
                items.getOrNull(h.adapterPosition)?.let { toggleFav(it) }
                true
            }
        }

        override fun getItemCount() = items.size
    }

    inner class CoAdapter : RecyclerView.Adapter<CoAdapter.VH>() {
        var items: List<Pair<String, String>> = emptyList()

        inner class VH(val txt: TextView) : RecyclerView.ViewHolder(txt)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val t = TextView(this@MainActivity).apply {
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(dp(16), dp(10), dp(16), dp(10))
                isFocusable = true
                isClickable = true
                background = focusBg()
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, dp(3), 0, dp(3))
                }
            }
            return VH(t)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val (key, label) = items[position]
            h.txt.text = label
            h.txt.isSelected = key == sel[tab]
            h.txt.setOnClickListener {
                sel[tab] = key
                if (serverSearch(tab)) load(tab, true)
                refresh()
            }
        }

        override fun getItemCount() = items.size
    }
}
