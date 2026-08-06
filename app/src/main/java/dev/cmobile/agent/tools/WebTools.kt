package dev.cmobile.agent.tools

import dev.cmobile.agent.llm.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder

private const val BROWSER_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

object WebSearch : AgentTool {
    override val name = "web_search"
    override val description =
        "Search the web via DuckDuckGo and get back a ranked list of titles, URLs and snippets. " +
            "Use this for anything you don't know or that may have changed since your training " +
            "data. Snippets are short — follow up with web_fetch to read a promising page in full."
    override val schema = objectSchema {
        string("query", "The search query.", required = true)
        integer("max_results", "How many results to return. Default 8, max 20.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val query = args.str("query")?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: query")
        val maxResults = args.int("max_results", 8).coerceIn(1, 20)

        val results = try {
            searchHtml(query).ifEmpty { searchLite(query) }
        } catch (e: Exception) {
            return err("Search failed: ${e.message}")
        }

        if (results.isEmpty()) {
            return ok {
                put("query", query)
                put("result_count", 0)
                put(
                    "note",
                    "DuckDuckGo returned no parseable results. It may have served a rate-limit or " +
                        "CAPTCHA page. Try rephrasing, or wait before retrying.",
                )
            }
        }

        return ok {
            put("query", query)
            put("result_count", minOf(results.size, maxResults))
            put("results", buildJsonArray {
                results.take(maxResults).forEachIndexed { index, result ->
                    addJsonObject {
                        put("rank", index + 1)
                        put("title", result.title)
                        put("url", result.url)
                        put("snippet", result.snippet)
                    }
                }
            })
        }
    }

    data class SearchResult(val title: String, val url: String, val snippet: String)

    /** Primary endpoint: the no-JS HTML frontend. */
    private suspend fun searchHtml(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://html.duckduckgo.com/html/")
            .addHeader("User-Agent", BROWSER_UA)
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .post(FormBody.Builder().add("q", query).add("kl", "wt-wt").build())
            .build()

        val html = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string().orEmpty()
        }

        Jsoup.parse(html).select("div.result, div.web-result").mapNotNull { element ->
            val link = element.selectFirst("a.result__a") ?: return@mapNotNull null
            val title = link.text().trim().ifEmpty { return@mapNotNull null }
            val url = unwrapRedirect(link.attr("href")).ifEmpty { return@mapNotNull null }
            val snippet = element.selectFirst("a.result__snippet, div.result__snippet")
                ?.text()?.trim().orEmpty()
            SearchResult(title, url, snippet)
        }
    }

    /** Fallback: the even simpler "lite" frontend, which uses a table layout. */
    private suspend fun searchLite(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://lite.duckduckgo.com/lite/")
            .addHeader("User-Agent", BROWSER_UA)
            .post(FormBody.Builder().add("q", query).build())
            .build()

        val html = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.body?.string().orEmpty()
        }

        val document = Jsoup.parse(html)
        val links = document.select("a.result-link")
        val snippets = document.select("td.result-snippet")

        links.mapIndexedNotNull { index, link ->
            val title = link.text().trim().ifEmpty { return@mapIndexedNotNull null }
            val url = unwrapRedirect(link.attr("href")).ifEmpty { return@mapIndexedNotNull null }
            SearchResult(title, url, snippets.getOrNull(index)?.text()?.trim().orEmpty())
        }
    }

    /** DDG wraps outbound links as //duckduckgo.com/l/?uddg=<encoded>. */
    private fun unwrapRedirect(href: String): String {
        val raw = href.trim()
        if (raw.isEmpty()) return ""
        val marker = "uddg="
        val index = raw.indexOf(marker)
        if (index < 0) {
            return when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("http") -> raw
                else -> ""
            }
        }
        val encoded = raw.substring(index + marker.length).substringBefore('&')
        return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrDefault("")
    }
}

object WebFetch : AgentTool {
    override val name = "web_fetch"
    override val description =
        "Fetch a web page and return its readable text content with markup, scripts and " +
            "navigation stripped out. Use after web_search when a snippet isn't enough."
    override val schema = objectSchema {
        string("url", "Absolute URL to fetch (http or https).", required = true)
        integer("max_chars", "Maximum characters of text to return. Default 6000, max 20000.")
    }

    override suspend fun run(args: JsonObject, ctx: ToolContext): String {
        val rawUrl = args.str("url")?.trim()?.takeIf { it.isNotBlank() }
            ?: return err("Missing required argument: url")
        val url = if (rawUrl.startsWith("http")) rawUrl else "https://$rawUrl"
        val maxChars = args.int("max_chars", 6000).coerceIn(500, 20_000)

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", BROWSER_UA)
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .get()
                    .build()

                Http.client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext err("HTTP ${response.code} fetching $url")
                    }

                    val contentType = response.header("Content-Type").orEmpty()
                    if (contentType.startsWith("application/json") || contentType.contains("text/plain")) {
                        return@withContext ok {
                            put("url", url)
                            put("content_type", contentType)
                            put("content", body.truncate(maxChars))
                        }
                    }

                    val document = Jsoup.parse(body, url)
                    ok {
                        put("url", url)
                        put("title", document.title())
                        put("content_type", contentType.ifBlank { "text/html" })
                        put("content", readableText(document).truncate(maxChars))
                    }
                }
            } catch (e: Exception) {
                err("Fetch failed: ${e.message}")
            }
        }
    }

    private fun readableText(document: Document): String {
        document.select("script, style, noscript, svg, nav, footer, header, aside, form").remove()
        val main = document.selectFirst("article, main, [role=main]") ?: document.body()
        return main?.text()?.replace(Regex("\\s{3,}"), "\n\n")?.trim().orEmpty()
    }
}
