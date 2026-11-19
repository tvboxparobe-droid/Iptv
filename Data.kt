package br.conhecimentosrap.iptv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Channel(
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
    val vodId: String = "",
    val favUrl: String = ""
)

object PlayerState {
    var list: List<Channel> = emptyList()
    var index = 0
    var vod = false
}

object M3u {
    private val logoRe = Regex("tvg-logo=\"([^\"]*)\"")
    private val groupRe = Regex("group-title=\"([^\"]*)\"")

    fun parse(text: String): List<Channel> {
        val out = ArrayList<Channel>()
        var name: String? = null
        var logo = ""
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                val i = line.lastIndexOf("\",")
                name = (if (i >= 0) line.substring(i + 2) else line.substringAfterLast(",")).trim()
                logo = logoRe.find(line)?.groupValues?.get(1) ?: ""
                group = groupRe.find(line)?.groupValues?.get(1) ?: ""
            } else if (line.isNotEmpty() && !line.startsWith("#") && name != null) {
                out.add(Channel(name, line, logo, group.ifBlank { "Outros" }))
                name = null
            }
        }
        return out
    }

    fun fetch(ctx: Context, url: String, cacheName: String): String {
        val f = File(ctx.cacheDir, cacheName)
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 12 * 3600 * 1000L) {
            return f.readText()
        }
        return try {
            val t = Net.get(url)
            f.writeText(t)
            t
        } catch (e: Exception) {
            if (f.exists()) f.readText() else throw e
        }
    }
}

object Net {
    fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 40000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "IPTVLivre/1.0")
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}

// Filmes e series gratuitos sob demanda: Internet Archive (dominio publico / acervo livre)
object Archive {
    fun search(base: String, text: String): List<Channel> {
        val clean = text.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        var q = "($base) AND mediatype:movies"
        if (clean.isNotEmpty()) q += " AND title:($clean)"
        val url = "https://archive.org/advancedsearch.php?q=" + URLEncoder.encode(q, "UTF-8") +
            "&fl%5B%5D=identifier&fl%5B%5D=title&sort%5B%5D=downloads+desc&rows=300&page=1&output=json"
        val docs = JSONObject(Net.get(url)).getJSONObject("response").getJSONArray("docs")
        val out = ArrayList<Channel>()
        for (i in 0 until docs.length()) {
            val d = docs.getJSONObject(i)
            val id = d.optString("identifier")
            if (id.isEmpty()) continue
            val t = d.opt("title")
            val title = when (t) {
                is JSONArray -> t.optString(0)
                else -> t?.toString() ?: id
            }
            out.add(Channel(title.ifBlank { id }, "", "https://archive.org/services/img/$id", "", id))
        }
        return out
    }

    fun resolve(id: String): String? {
        val files = JSONObject(Net.get("https://archive.org/metadata/$id")).optJSONArray("files") ?: return null
        val preferred = setOf("h.264", "mpeg4", "512kb mpeg4", "h.264 hd")
        var best: String? = null
        var bestScore = -1L
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.optString("name")
            val lower = name.lowercase()
            if (!lower.endsWith(".mp4") && !lower.endsWith(".mkv")) continue
            if (lower.contains("sample") || lower.contains("trailer")) continue
            val size = f.optString("size", "0").toLongOrNull() ?: 0L
            val pri = if (preferred.contains(f.optString("format").lowercase())) 1L else 0L
            val score = pri * 1_000_000_000_000L + size
            if (score > bestScore) { bestScore = score; best = name }
        }
        val n = best ?: return null
        val path = n.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        return "https://archive.org/download/$id/$path"
    }
}

// ---------- YouTube ao vivo ----------
// Reproducao sempre pelo player oficial embutido (embed) do YouTube.
object YtLive {
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    private val canon = Regex("<link rel=\"canonical\" href=\"([^\"]+)\"")
    private val vid = Regex("watch\\?v=([\\w-]{11})")

    fun read(url: String, cap: Int, stop: Regex?): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 15000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
        if (c.responseCode == 404) return null
        val sb = StringBuilder()
        c.inputStream.bufferedReader().use { r ->
            val buf = CharArray(8192)
            while (sb.length < cap) {
                val n = r.read(buf)
                if (n < 0) break
                sb.append(buf, 0, n)
                if (stop != null && stop.containsMatchIn(sb)) break
            }
        }
        return sb.toString()
    }

    // null = canal nao existe; "" = fora do ar; senao = id do video ao vivo agora
    fun check(handleOrId: String): String? {
        val p = if (handleOrId.startsWith("UC")) "channel/$handleOrId" else handleOrId
        val html = read("https://www.youtube.com/$p/live", 250_000, canon) ?: return null
        val href = canon.find(html)?.groupValues?.get(1) ?: return ""
        return vid.find(href)?.groupValues?.get(1) ?: ""
    }
}

object YtSearch {
    private val titleRe = Regex("\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")
    private val ownerRe = Regex("\"(?:ownerText|longBylineText)\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"")

    private fun clean(s: String) = s.replace("\\u0026", "&").replace("\\\"", "\"").replace("\\\\", "\\")

    // Transmissoes AO VIVO neste momento (filtro "Ao vivo" da busca do YouTube)
    fun search(q: String, gl: String, hl: String, mode: String): List<Channel> {
        val sp = when (mode) { "cc" -> "EgQYAjAB"; "long" -> "EgIYAg%253D%253D"; else -> "EgJAAQ%253D%253D" }
        val url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode(q, "UTF-8") +
            "&sp=" + sp + "&gl=$gl&hl=$hl"
        val html = YtLive.read(url, 2_500_000, null) ?: return emptyList()
        val out = ArrayList<Channel>()
        val seen = HashSet<String>()
        val parts = html.split("\"videoRenderer\":{\"videoId\":\"")
        for (k in 1 until parts.size) {
            val p = parts[k]
            if (p.length < 11) continue
            val id = p.substring(0, 11)
            val body = p.take(6000)
            if (body.contains("upcomingEventData")) continue
            if (!seen.add(id)) continue
            val title = clean(titleRe.find(body)?.groupValues?.get(1) ?: continue)
            val owner = clean(ownerRe.find(body)?.groupValues?.get(1) ?: "")
            val name = if (owner.isNotEmpty()) "$owner: $title" else title
            out.add(Channel((if (mode == "live") "● " else "") + name, "ytv:$id", "https://i.ytimg.com/vi/$id/mqdefault.jpg", owner))
        }
        return out
    }
}

// Catalogo: Nome|@handle (ou ID UC...)|Grupo. Canais inexistentes sao ocultados automaticamente.
object YouTubeLive {
    private val raw = """
CazéTV|@CazeTV|Brasil · Esportes
ge|@ge|Brasil · Esportes
ge globo|@geglobo|Brasil · Esportes
GOAT|@goat|Brasil · Esportes
GOAT Brasil|@goatbr|Brasil · Esportes
ESPN Brasil|@ESPNBrasil|Brasil · Esportes
XSports|@xsports|Brasil · Esportes
XSports Brasil|@XSportsBR|Brasil · Esportes
SporTV|@sportv|Brasil · Esportes
TNT Sports Brasil|@TNTSportsBR|Brasil · Esportes
Band Sports|@BandSports|Brasil · Esportes
UOL Esporte|@uolesporte|Brasil · Esportes
Lance!|@lance|Brasil · Esportes
Jovem Pan Esportes|@JovemPanEsportes|Brasil · Esportes
Gaúcha Esportes|@gauchaesportes|Brasil · Esportes
Grêmio|@gremio|Brasil · Esportes
Internacional|@SCInternacional|Brasil · Esportes
Flamengo|@flamengo|Brasil · Esportes
Corinthians|@corinthians|Brasil · Esportes
Palmeiras|@palmeiras|Brasil · Esportes
São Paulo FC|@saopaulofc|Brasil · Esportes
Santos FC|@santosfc|Brasil · Esportes
Fluminense|@fluminensefc|Brasil · Esportes
Vasco|@vasco|Brasil · Esportes
Botafogo|@botafogo|Brasil · Esportes
Cruzeiro|@cruzeiro|Brasil · Esportes
Atlético-MG|@atletico|Brasil · Esportes
CBF|@cbf|Brasil · Esportes
Rádio Gaúcha|@radiogaucha|Brasil · Rádio
Gaúcha|@gaucha|Brasil · Rádio
Rádio Gaúcha ZH|@RadioGauchaZH|Brasil · Rádio
GZH|@gzh|Brasil · Rádio
GZH Gaúcha ZH|@gauchazh|Brasil · Rádio
Rádio Atlântida|@radioatlantida|Brasil · Rádio
Rádio Jovem Pan|@jovempan|Brasil · Rádio
CBN|@cbn|Brasil · Rádio
BandNews FM|@bandnewsfm|Brasil · Rádio
Rádio MEC|@radiomec|Brasil · Rádio
Rádio Nacional|@radionacional|Brasil · Rádio
Rádio Itatiaia|@radioitatiaia|Brasil · Rádio
Rádio Globo|@radioglobo|Brasil · Rádio
Rádio Transamérica|@transamerica|Brasil · Rádio
Rádio Mix FM|@mixfm|Brasil · Rádio
Rádio Kiss FM|@kissfm|Brasil · Rádio
Rádio FM O Dia|@fmodia|Brasil · Rádio
CNN Brasil|UCvdwhh_fDyWccR42-rReZLw|Brasil · Notícias e TV
CNN Brasil (handle)|@CNNBrasil|Brasil · Notícias e TV
Jovem Pan News|@JovemPanNews|Brasil · Notícias e TV
SBT News|@SBTNews|Brasil · Notícias e TV
SBT|@sbt|Brasil · Notícias e TV
Band Jornalismo|@bandjornalismo|Brasil · Notícias e TV
Band|@band|Brasil · Notícias e TV
BandNews TV|@bandnewstv|Brasil · Notícias e TV
Record News|@recordnews|Brasil · Notícias e TV
Record|@recordtv|Brasil · Notícias e TV
RedeTV!|@redetv|Brasil · Notícias e TV
TV Globo|@tvglobo|Brasil · Notícias e TV
GloboNews|@globonews|Brasil · Notícias e TV
TV Cultura|@tvcultura|Brasil · Notícias e TV
TV Brasil|@tvbrasil|Brasil · Notícias e TV
Canal Gov|@canalgov|Brasil · Notícias e TV
Metrópoles|@metropoles|Brasil · Notícias e TV
UOL|@uol|Brasil · Notícias e TV
Terra Brasil|@terrabrasil|Brasil · Notícias e TV
Poder360|@poder360|Brasil · Notícias e TV
Revista Oeste|@revistaoeste|Brasil · Notícias e TV
Canal Rural|@canalrural|Brasil · Notícias e TV
BBC News Brasil|@bbcnewsbrasil|Brasil · Notícias e TV
DW Brasil|@dwbrasil|Brasil · Notícias e TV
TV Pampa|@tvpampa|Brasil · Notícias e TV
RBS TV|@rbstv|Brasil · Notícias e TV
TVE RS|@tvers|Brasil · Notícias e TV
Assembleia Legislativa RS|@assembleiars|Brasil · Governo
TV Senado|@tvsenado|Brasil · Governo
Câmara dos Deputados|@camaradosdeputados|Brasil · Governo
TV Justiça|@tvjustica|Brasil · Governo
STF|@stf|Brasil · Governo
TSE|@tsejusbr|Brasil · Governo
Planalto|@planalto|Brasil · Governo
TV Assembleia SP|@tvalesp|Brasil · Governo
ESPN|@espn|Mundo · Esportes
ESPN Deportes|@espndeportes|Mundo · Esportes
ESPN Argentina|@espnargentina|Mundo · Esportes
Fox Sports|@foxsports|Mundo · Esportes
DAZN|@dazn|Mundo · Esportes
beIN SPORTS|@beinsports|Mundo · Esportes
CONMEBOL|@conmebol|Mundo · Esportes
FIFA|@fifa|Mundo · Esportes
UEFA|@uefa|Mundo · Esportes
Olympics|@olympics|Mundo · Esportes
Formula 1|@formula1|Mundo · Esportes
NBA|@nba|Mundo · Esportes
NFL|@nfl|Mundo · Esportes
MLB|@mlb|Mundo · Esportes
NHL|@nhl|Mundo · Esportes
UFC|@ufc|Mundo · Esportes
WWE|@wwe|Mundo · Esportes
LaLiga|@laliga|Mundo · Esportes
Bundesliga|@bundesliga|Mundo · Esportes
Premier League|@premierleague|Mundo · Esportes
Serie A|@seriea|Mundo · Esportes
OneFootball|@onefootball|Mundo · Esportes
Sky Sports|@skysports|Mundo · Esportes
Al Jazeera English|UCNye-wNBqNL5ZzHSJj3l8Bg|Mundo · Notícias (EN)
DW News|UCknLrEdhRCp1aegoMqRaCZg|Mundo · Notícias (EN)
France 24 English|UCQfwfsi5VrQ8yKZ-UWmAEFg|Mundo · Notícias (EN)
Sky News|UCoMdktPbSTixAyNGwb-UYkQ|Mundo · Notícias (EN)
Euronews English|UCSrZ3UV4jOidv8ppoVuvW9Q|Mundo · Notícias (EN)
Bloomberg Television|UCIALMKvObZNtJ6AmdCLP7Lg|Mundo · Notícias (EN)
ABC News|UCBi2mrWuNuyYy4gbM6fU18Q|Mundo · Notícias (EN)
NBC News|@nbcnews|Mundo · Notícias (EN)
CBS News|@cbsnews|Mundo · Notícias (EN)
LiveNOW from FOX|@livenowfox|Mundo · Notícias (EN)
Fox News|@foxnews|Mundo · Notícias (EN)
CNN|@cnn|Mundo · Notícias (EN)
BBC News|@bbcnews|Mundo · Notícias (EN)
GB News|@gbnews|Mundo · Notícias (EN)
Reuters|@reuters|Mundo · Notícias (EN)
Associated Press|@associatedpress|Mundo · Notícias (EN)
NewsNation|@newsnation|Mundo · Notícias (EN)
Newsmax|@newsmax|Mundo · Notícias (EN)
WION|@wion|Mundo · Notícias (EN)
TRT World|@trtworld|Mundo · Notícias (EN)
CGTN|@cgtn|Mundo · Notícias (EN)
NHK WORLD-JAPAN|@nhkworldjapan|Mundo · Notícias (EN)
Arirang|@arirangtv|Mundo · Notícias (EN)
i24NEWS English|@i24news_en|Mundo · Notícias (EN)
Africanews|@africanews|Mundo · Notícias (EN)
CBC News|@cbcnews|Mundo · Notícias (EN)
Global News|@globalnews|Mundo · Notícias (EN)
ABC News Australia|@abcnewsaustralia|Mundo · Notícias (EN)
Sky News Australia|@skynewsaustralia|Mundo · Notícias (EN)
DD News|@ddnewslive|Mundo · Notícias (EN)
NDTV|@ndtv|Mundo · Notícias (EN)
India Today|@indiatoday|Mundo · Notícias (EN)
Aaj Tak|@aajtak|Mundo · Notícias (EN)
Euronews Português|@euronewspt|Mundo · Notícias (outros idiomas)
SIC Notícias|@sicnoticias|Mundo · Notícias (outros idiomas)
CNN Portugal|@cnnportugal|Mundo · Notícias (outros idiomas)
RTP|@rtp|Mundo · Notícias (outros idiomas)
RTVE|@rtve|Mundo · Notícias (outros idiomas)
DW Español|@dwespanol|Mundo · Notícias (outros idiomas)
France 24 Español|@france24_es|Mundo · Notícias (outros idiomas)
CNN en Español|@cnnespanol|Mundo · Notícias (outros idiomas)
teleSUR|@telesurtv|Mundo · Notícias (outros idiomas)
NTN24|@ntn24|Mundo · Notícias (outros idiomas)
Infobae|@infobae|Mundo · Notícias (outros idiomas)
TN Todo Noticias|@todonoticias|Mundo · Notícias (outros idiomas)
C5N|@c5n|Mundo · Notícias (outros idiomas)
N+ México|@nmas|Mundo · Notícias (outros idiomas)
Milenio|@milenio|Mundo · Notícias (outros idiomas)
France 24 Français|@france24|Mundo · Notícias (outros idiomas)
franceinfo|@franceinfo|Mundo · Notícias (outros idiomas)
BFMTV|@bfmtv|Mundo · Notícias (outros idiomas)
LCI|@lci|Mundo · Notícias (outros idiomas)
DW Deutsch|@dwdeutsch|Mundo · Notícias (outros idiomas)
WELT|@weltnachrichtensender|Mundo · Notícias (outros idiomas)
tagesschau|@tagesschau|Mundo · Notícias (outros idiomas)
Sky TG24|@skytg24|Mundo · Notícias (outros idiomas)
Rai News|@rainews|Mundo · Notícias (outros idiomas)
ANN News (Japão)|@annnewsch|Mundo · Notícias (outros idiomas)
TBS NEWS DIG|@tbsnewsdig|Mundo · Notícias (outros idiomas)
YTN (Coreia)|@ytnnews|Mundo · Notícias (outros idiomas)
Al Arabiya|@alarabiya|Mundo · Notícias (outros idiomas)
Sky News Arabia|@skynewsarabia|Mundo · Notícias (outros idiomas)
NASA|UCLA_DiR1FfKNvjuUpBHmylQ|Espaço e ciência
NASA (handle)|@nasa|Espaço e ciência
NASASpaceflight|@nasaspaceflight|Espaço e ciência
SpaceX|@spacex|Espaço e ciência
ESA|@esa|Espaço e ciência
Blue Origin|@blueorigin|Espaço e ciência
Rocket Lab|@rocketlab|Espaço e ciência
LabPadre|@labpadre|Espaço e ciência
Spaceflight Now|@spaceflightnow|Espaço e ciência
Explore.org|@exploreorg|Natureza e câmeras
Monterey Bay Aquarium|@montereybayaquarium|Natureza e câmeras
Shedd Aquarium|@sheddaquarium|Natureza e câmeras
San Diego Zoo|@sandiegozoo|Natureza e câmeras
Africam|@africam|Natureza e câmeras
EarthCam|@earthcam|Natureza e câmeras
SkylineWebcams|@skylinewebcams|Natureza e câmeras
Cornell Lab Birds|@cornelllabbirds|Natureza e câmeras
Lofi Girl|UCSJ4gkVC6NrvII8umztf0Ow|Música e relax
Chillhop Music|@chillhopmusic|Música e relax
The Jazz Hop Café|@thejazzhopcafe|Música e relax
Cercle|@cercle|Música e relax
Boiler Room|@boilerroom|Música e relax
Café Music BGM|@cafemusicbgmchannel|Música e relax
Vatican News|@vaticannews|Religião
PlutoTV|@plutotv|Entretenimento
"""
    val catalog: List<Channel> = raw.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size < 3) null else Channel(p[0].trim(), "ytl:" + p[1].trim(), "", p[2].trim())
    }
}

// Favoritos (salvos no aparelho)
object Favs {
    private fun prefs(c: Context) = c.getSharedPreferences("fav", Context.MODE_PRIVATE)
    fun keyOf(c: Channel) = when {
        c.favUrl.isNotEmpty() -> c.favUrl
        c.vodId.isNotEmpty() -> "v:" + c.vodId
        else -> c.url
    }
    private fun clean(s: String) = s.replace("\t", " ").replace("\n", " ")
    fun load(ctx: Context): List<Channel> =
        (prefs(ctx).getString("list", "") ?: "").lines().filter { it.isNotBlank() }.mapNotNull {
            val p = it.split("\t")
            if (p.size < 5) null else Channel(p[0], p[1], p[2], p[3], p[4])
        }
    private fun save(ctx: Context, l: List<Channel>) {
        prefs(ctx).edit().putString("list", l.joinToString("\n") {
            listOf(it.name, it.url, it.logo, it.group, it.vodId).joinToString("\t") { s -> clean(s) }
        }).apply()
    }
    // true = adicionado, false = removido
    fun toggle(ctx: Context, ch: Channel): Boolean {
        val l = load(ctx).toMutableList()
        val k = keyOf(ch)
        val removed = l.removeAll { keyOf(it) == k }
        if (!removed) l.add(ch)
        save(ctx, l)
        return !removed
    }
}
