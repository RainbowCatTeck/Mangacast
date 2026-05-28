package com.mangacast.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.transform.RoundedCornersTransformation
import com.mangacast.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: CharacterAdapter
    private val api = JikanApi()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupSearch()
        showState(State.IDLE)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return

        // Try every possible text location regardless of mime type
        val raw = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
            ?: intent.getStringExtra(Intent.EXTRA_TITLE)
            ?: intent.clipData?.getItemAt(0)?.text?.toString()

        if (raw == null) {
            Toast.makeText(this, "Share received but no title text found", Toast.LENGTH_LONG).show()
            return
        }

        // Strip "Title: Chapter X, Page Y" (image share format)
        // Strip "Title - TachiyomiSY", chapter suffixes, and leading "Read"
        var cleaned = raw
            .replace(Regex("\\s*:\\s*[Cc]hapter.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–]\\s*TachiyomiSY.*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[-–]\\s*[Cc]h(apter|\\.)?\\s*[\\d.]+.*"), "")
            .replace(Regex("\\s*[Cc]hapter\\s*[\\d.]+.*"), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("^read\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        // If result is blank (e.g. raw was a URL), try extracting title from URL path
        if (cleaned.isBlank() && raw.startsWith("http")) {
            cleaned = extractTitleFromUrl(raw) ?: ""
        }

        binding.sharedPill.visibility = View.VISIBLE

        if (cleaned.isNotBlank()) {
            binding.searchInput.setText(cleaned)
            binding.sharedTitle.text = cleaned
            lookupManga(cleaned)
        } else {
            // Still nothing — show raw so you can edit and tap GO manually
            binding.searchInput.setText(raw)
            binding.sharedTitle.text = raw
        }
    }

    private fun extractTitleFromUrl(url: String): String? {
        return try {
            val path = java.net.URL(url).path.trimEnd('/')
            val segments = path.split("/").filter { it.isNotBlank() }
            // Skip UUID segments (e.g. MangaDex: /title/{uuid}/{slug})
            val titleSegment = segments.lastOrNull {
                it.length > 2 &&
                !it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            }
            titleSegment?.replace(Regex("[-_]"), " ")?.trim()
        } catch (e: Exception) { null }
    }

    private fun setupRecyclerView() {
        adapter = CharacterAdapter()
        binding.charRecycler.layoutManager = LinearLayoutManager(this)
        binding.charRecycler.adapter = adapter
        binding.charRecycler.itemAnimator = null
    }

    private fun setupSearch() {
        binding.searchBtn.setOnClickListener {
            val q = binding.searchInput.text.toString().trim()
            if (q.isNotBlank()) lookupManga(q)
            hideKeyboard()
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val q = binding.searchInput.text.toString().trim()
                if (q.isNotBlank()) lookupManga(q)
                hideKeyboard()
                true
            } else false
        }

        binding.chipAll.setOnClickListener { filterChars("all") }
        binding.chipMain.setOnClickListener { filterChars("Main") }
        binding.chipSupporting.setOnClickListener { filterChars("Supporting") }
    }

    private fun lookupManga(query: String) {
        showState(State.LOADING)

        lifecycleScope.launch {
            try {
                val manga = api.searchManga(query)
                val chars = api.fetchCharacters(manga.malId)

                runOnUiThread {
                    renderManga(manga)
                    adapter.setCharacters(chars)
                    updateCounts(chars)
                    showState(State.RESULTS)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.errorText.text = e.message ?: "Something went wrong. Try again."
                    showState(State.ERROR)
                }
            }
        }
    }

    private fun renderManga(manga: MangaResult) {
        binding.mangaTitleEn.text = manga.titleEn.ifBlank { manga.title }
        binding.mangaTitleJp.text = manga.titleJp
        binding.mangaTitleJp.visibility = if (manga.titleJp.isNotBlank()) View.VISIBLE else View.GONE

        val typeText = listOfNotNull(
            manga.type.ifBlank { null },
            if (manga.score > 0) "★ ${manga.score}" else null,
            manga.status.ifBlank { null }
        ).joinToString("  ·  ")
        binding.mangaMeta.text = typeText
        binding.mangaMeta.visibility = if (typeText.isNotBlank()) View.VISIBLE else View.GONE

        if (manga.imageUrl.isNotBlank()) {
            binding.mangaCover.load(manga.imageUrl) {
                transformations(RoundedCornersTransformation(8f))
                crossfade(true)
            }
            binding.mangaCover.visibility = View.VISIBLE
        } else {
            binding.mangaCover.visibility = View.GONE
        }
    }

    private fun updateCounts(chars: List<CharacterEntry>) {
        val mains = chars.count { it.role == "Main" }
        val sups = chars.count { it.role == "Supporting" }
        binding.chipAll.text = "All (${chars.size})"
        binding.chipMain.text = "Main ($mains)"
        binding.chipSupporting.text = "Supporting ($sups)"
    }

    private fun filterChars(role: String) {
        adapter.filter(role)
        binding.chipAll.isSelected = role == "all"
        binding.chipMain.isSelected = role == "Main"
        binding.chipSupporting.isSelected = role == "Supporting"
    }

    private fun showState(state: State) {
        binding.stateIdle.visibility = View.GONE
        binding.stateLoading.visibility = View.GONE
        binding.stateError.visibility = View.GONE
        binding.resultsPanel.visibility = View.GONE

        when (state) {
            State.IDLE -> binding.stateIdle.visibility = View.VISIBLE
            State.LOADING -> binding.stateLoading.visibility = View.VISIBLE
            State.ERROR -> binding.stateError.visibility = View.VISIBLE
            State.RESULTS -> binding.resultsPanel.visibility = View.VISIBLE
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    enum class State { IDLE, LOADING, ERROR, RESULTS }
}
