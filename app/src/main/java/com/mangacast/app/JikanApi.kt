package com.mangacast.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MangaResult(
    val malId: Int,
    val title: String,
    val titleEn: String,
    val titleJp: String,
    val imageUrl: String,
    val type: String,
    val status: String,
    val score: Double
)

data class CharacterEntry(
    val malId: Int,
    val name: String,
    val nameKanji: String,
    val imageUrl: String,
    val role: String
)

class JikanApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val base = "https://api.jikan.moe/v4"

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) throw Exception("API error: ${resp.code}")
        val body = resp.body?.string() ?: throw Exception("Empty response")
        JSONObject(body)
    }

    suspend fun searchManga(query: String): MangaResult {
        val json = get("$base/manga?q=${encode(query)}&limit=1&sfw")
        val data = json.getJSONArray("data")
        if (data.length() == 0) throw Exception("No results found for \"$query\"")

        val m = data.getJSONObject(0)
        val images = m.optJSONObject("images")?.optJSONObject("jpg")
        val imageUrl = images?.optString("large_image_url")
            ?.takeIf { it.isNotBlank() }
            ?: images?.optString("image_url") ?: ""

        return MangaResult(
            malId = m.getInt("mal_id"),
            title = m.optString("title", ""),
            titleEn = m.optString("title_english", ""),
            titleJp = m.optString("title_japanese", ""),
            imageUrl = imageUrl,
            type = m.optString("type", ""),
            status = m.optString("status", ""),
            score = m.optDouble("score", 0.0)
        )
    }

    suspend fun fetchCharacters(malId: Int): List<CharacterEntry> {
        val json = get("$base/manga/$malId/characters")
        val data = json.getJSONArray("data")
        val list = mutableListOf<CharacterEntry>()

        for (i in 0 until data.length()) {
            val entry = data.getJSONObject(i)
            val char = entry.optJSONObject("character") ?: continue
            val role = entry.optString("role", "Supporting")
            val images = char.optJSONObject("images")?.optJSONObject("jpg")
            val img = images?.optString("image_url") ?: ""

            list.add(CharacterEntry(
                malId = char.optInt("mal_id", 0),
                name = char.optString("name", ""),
                nameKanji = char.optString("name_kanji", ""),
                imageUrl = img,
                role = role
            ))
        }

        return list.sortedWith(compareBy { if (it.role == "Main") 0 else 1 })
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}
