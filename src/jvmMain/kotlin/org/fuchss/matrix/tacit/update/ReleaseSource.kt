package org.fuchss.matrix.tacit.update

import kotlinx.serialization.json.*
import org.fuchss.matrix.tacit.BuildConfig
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

internal const val DEFAULT_RELEASE_API_URL = "https://api.github.com/repos/dfuchss/tacit/releases/latest"
internal const val RELEASES_PAGE = "https://github.com/dfuchss/tacit/releases/latest"

/** A field's text, or `null` when it is missing, `null` or not a primitive (so one odd field never fails a parse). */
internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

/** The release as in the GitHub JSON (`releases/latest`); also the shape of `cachedRelease` in `tacit-updater.json`. */
data class TacitRelease(
    val tag: String,
    val version: TacitVersion,
    val htmlUrl: String,
    val notes: String?,
    /** Asset name to download URL. */
    val assets: Map<String, String>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("tag_name", tag)
        put("html_url", htmlUrl)
        notes?.let { put("body", it) }
        putJsonArray("assets") {
            assets.forEach { (name, url) ->
                addJsonObject {
                    put("name", name)
                    put("browser_download_url", url)
                }
            }
        }
    }

    companion object {
        /** `null` when the tag is missing or not a Tacit version; assets without a name or URL are skipped. */
        fun parse(json: JsonObject): TacitRelease? {
            val tag = json.string("tag_name") ?: return null
            val version = TacitVersion.parse(tag) ?: return null
            val assets = (json["assets"] as? JsonArray).orEmpty().mapNotNull { asset ->
                val obj = asset as? JsonObject ?: return@mapNotNull null
                val name = obj.string("name") ?: return@mapNotNull null
                val url = obj.string("browser_download_url") ?: return@mapNotNull null
                name to url
            }.toMap()
            return TacitRelease(
                tag = tag,
                version = version,
                htmlUrl = json.string("html_url") ?: RELEASES_PAGE,
                notes = json.string("body"),
                assets = assets,
            )
        }
    }
}

/** The answer to a (conditional) request for the latest release. */
sealed interface ReleaseFetch {
    /** The server confirmed that the release behind the sent ETag is still the latest. */
    data object NotModified : ReleaseFetch

    /** [release] is `null` when the latest release is not a Tacit version. */
    data class Fetched(val release: TacitRelease?, val etag: String?) : ReleaseFetch
}

/** An open download; [contentLength] is `-1` when unknown. */
class ReleaseDownload(val input: InputStream, val contentLength: Long) : Closeable {
    override fun close() = input.close()
}

/** Where releases and their assets come from. Calls block and may be interrupted. */
interface ReleaseSource {
    /** Fetches the latest release; with [etag], the source may answer [ReleaseFetch.NotModified]. */
    fun fetchLatest(etag: String?): ReleaseFetch

    /** Opens an asset download; throws when the server does not answer with the file. */
    fun open(url: String): ReleaseDownload
}

/** The GitHub releases API over `java.net.http`. */
class GitHubReleaseSource(private val apiUrl: String = DEFAULT_RELEASE_API_URL) : ReleaseSource {
    private val http: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(CONNECT_TIMEOUT)
            .build()
    }
    private val userAgent = "Tacit/${BuildConfig.version} (updater)"

    override fun fetchLatest(etag: String?): ReleaseFetch {
        val request = HttpRequest.newBuilder(URI(apiUrl))
            .timeout(REQUEST_TIMEOUT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", userAgent)
            .apply { etag?.let { header("If-None-Match", it) } }
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return when (response.statusCode()) {
            304 -> ReleaseFetch.NotModified
            200 -> ReleaseFetch.Fetched(
                release = parseRelease(response.body()),
                etag = response.headers().firstValue("ETag").orElse(null),
            )

            else -> throw IOException("GitHub answered ${response.statusCode()}")
        }
    }

    override fun open(url: String): ReleaseDownload {
        // The request timeout only covers the response headers; readers check for cancellation themselves.
        val request = HttpRequest.newBuilder(URI(url))
            .timeout(REQUEST_TIMEOUT)
            .header("User-Agent", userAgent)
            .GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() != 200) {
            response.body().close()
            throw IOException("Download of $url answered ${response.statusCode()}")
        }
        return ReleaseDownload(response.body(), response.headers().firstValueAsLong("Content-Length").orElse(-1))
    }

    companion object {
        private val CONNECT_TIMEOUT = Duration.ofSeconds(20)
        private val REQUEST_TIMEOUT = Duration.ofSeconds(30)

        internal fun parseRelease(body: String): TacitRelease? =
            TacitRelease.parse(Json.parseToJsonElement(body).jsonObject)
    }
}
