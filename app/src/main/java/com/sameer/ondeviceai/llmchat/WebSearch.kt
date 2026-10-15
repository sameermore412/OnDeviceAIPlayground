package com.sameer.ondeviceai.llmchat

import com.sameer.ondeviceai.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Web search for the chat's webSearch tool. Uses Brave Search when `brave.api.key` is set in
 * local.properties (free tier at https://brave.com/search/api/), otherwise Wikipedia, which needs no key.
 * Results are kept short because the model's context is small (4096 tokens).
 */
object WebSearch {
    data class Result(val title: String, val url: String, val snippet: String)
    data class Response(val source: String, val results: List<Result>)

    private const val MAX_RESULTS = 5
    private const val MAX_SNIPPET = 300

    /** Blocking network call. Tools run on LiteRT-LM's worker thread, so that's fine there. */
    fun search(query: String): Response =
        if (BuildConfig.BRAVE_API_KEY.isNotBlank()) brave(query) else wikipedia(query)

    private fun brave(query: String): Response {
        val json = getJson(
            "https://api.search.brave.com/res/v1/web/search?count=$MAX_RESULTS&q=${encode(query)}",
            "X-Subscription-Token" to BuildConfig.BRAVE_API_KEY,
        )
        val items = json.optJSONObject("web")?.optJSONArray("results")
        val results = (0 until (items?.length() ?: 0)).map { i ->
            val item = items!!.getJSONObject(i)
            Result(item.optString("title"), item.optString("url"), clean(item.optString("description")))
        }
        return Response("Brave Search", results)
    }

    private fun wikipedia(query: String): Response {
        val json = getJson(
            "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json" +
                "&srlimit=$MAX_RESULTS&srsearch=${encode(query)}",
        )
        val items = json.optJSONObject("query")?.optJSONArray("search")
        val results = (0 until (items?.length() ?: 0)).map { i ->
            val item = items!!.getJSONObject(i)
            val title = item.optString("title")
            Result(title, "https://en.wikipedia.org/wiki/${title.replace(' ', '_')}", clean(item.optString("snippet")))
        }
        return Response("Wikipedia", results)
    }

    private fun getJson(url: String, vararg headers: Pair<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            // Wikimedia asks API clients to identify themselves.
            setRequestProperty("User-Agent", "OnDeviceAIPlayground/0.1 (Android)")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    /** Strips the HTML highlighting both APIs put in snippets, and trims them. */
    private fun clean(html: String): String {
        val text = html.replace(Regex("<[^>]+>"), "")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
            .replace(Regex("\\s+"), " ").trim()
        return if (text.length > MAX_SNIPPET) text.take(MAX_SNIPPET) + "…" else text
    }

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8")
}
