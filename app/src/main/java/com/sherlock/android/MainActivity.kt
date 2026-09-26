package com.sherlock.android

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private data class Site(
    val name: String,
    val urlMain: String,
    val url: String,
    val errorType: List<String>,
    val errorMsg: Any?,
    val errorCode: List<Int>,
    val regexCheck: String?,
    val urlProbe: String?,
    val requestMethod: String?,
    val requestPayload: Any?,
    val isNsfw: Boolean
)

private enum class Status { CLAIMED, AVAILABLE, WAF, UNKNOWN, ILLEGAL, ERROR }

private data class ResultItem(
    val site: Site,
    val username: String,
    val profileUrl: String,
    val status: Status,
    val httpCode: Int? = null,
    val detail: String = ""
)

class MainActivity : Activity() {
    private val executor = Executors.newFixedThreadPool(20)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private lateinit var usernameInput: EditText
    private lateinit var searchButton: Button
    private lateinit var includeNsfw: CheckBox
    private lateinit var progress: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var resultsContainer: LinearLayout
    private lateinit var summary: TextView
    private var allSites = emptyList<Site>()
    private var runId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSites()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 18, 20, 12)
        }

        val logo = ImageView(this).apply {
            setImageResource(com.sherlock.android.R.drawable.sherlock_logo)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        header.addView(logo, LinearLayout.LayoutParams(54, 54))

        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(TextView(this).apply {
            text = "Sherlock"
            textSize = 25f
            setTextColor(Color.rgb(25, 25, 25))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        titleBox.addView(TextView(this).apply {
            text = "Username search • Android port of 0.16.2"
            textSize = 12f
            setTextColor(Color.DKGRAY)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 6, 16, 8)
        }
        usernameInput = EditText(this).apply {
            hint = "Enter username"
            isSingleLine = true
            textSize = 17f
            setPadding(18, 0, 18, 0)
        }
        inputRow.addView(usernameInput, LinearLayout.LayoutParams(0, 52, 1f))
        searchButton = Button(this).apply {
            text = "SEARCH"
            isAllCaps = false
            setOnClickListener { startSearch() }
        }
        inputRow.addView(searchButton, LinearLayout.LayoutParams(115, 52))
        root.addView(inputRow)

        includeNsfw = CheckBox(this).apply {
            text = "Include NSFW sites (off by default, matching Sherlock CLI)"
            textSize = 13f
            setPadding(16, 0, 16, 4)
        }
        root.addView(includeNsfw)

        progress = ProgressBar(this).apply { visibility = View.GONE }
        progressText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(18, 0, 18, 4)
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, 4))
        root.addView(progressText)

        summary = TextView(this).apply {
            textSize = 14f
            setPadding(18, 8, 18, 8)
            setTextColor(Color.DKGRAY)
        }
        root.addView(summary)

        val scroll = ScrollView(this)
        resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 4, 12, 24)
        }
        scroll.addView(resultsContainer)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
    }

    private fun loadSites() {
        try {
            val json = assets.open("data.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val list = mutableListOf<Site>()
            for (key in obj.keys()) {
                if (key == "\$schema") continue
                val o = obj.optJSONObject(key) ?: continue
                val types = mutableListOf<String>()
                val et = o.opt("errorType")
                when (et) {
                    is JSONArray -> for (i in 0 until et.length()) types.add(et.optString(i))
                    is String -> types.add(et)
                }
                val codes = mutableListOf<Int>()
                val ec = o.opt("errorCode")
                when (ec) {
                    is JSONArray -> for (i in 0 until ec.length()) if (!ec.isNull(i)) codes.add(ec.optInt(i))
                    is Number -> codes.add(ec.toInt())
                }
                list.add(Site(
                    key,
                    o.optString("urlMain"),
                    o.optString("url"),
                    types,
                    o.opt("errorMsg"),
                    codes,
                    o.optString("regexCheck", null),
                    o.optString("urlProbe", null),
                    o.optString("request_method", null),
                    o.opt("request_payload"),
                    o.optBoolean("isNSFW", false)
                ))
            }
            allSites = list.sortedBy { it.name.lowercase(Locale.ROOT) }
            val nsfwCount = allSites.count { it.isNsfw }
            summary.text = "${allSites.size} site definitions loaded • $nsfwCount NSFW (excluded by default)."
        } catch (e: Exception) {
            summary.text = "Could not load site data: ${e.message}"
            searchButton.isEnabled = false
        }
    }

    private fun startSearch() {
        val username = usernameInput.text.toString().trim()
        if (username.isEmpty()) {
            usernameInput.error = "Enter a username"
            return
        }

        runId++
        val currentRun = runId
        resultsContainer.removeAllViews()
        searchButton.isEnabled = false
        usernameInput.isEnabled = false
        progress.visibility = View.VISIBLE
        progressText.text = "Starting checks…"
        summary.text = "Checking ${allSites.size} sites…"

        val sitesToCheck = if (includeNsfw.isChecked) {
            allSites
        } else {
            allSites.filter { !it.isNsfw }
        }
        val completed = AtomicInteger(0)
        val total = sitesToCheck.size
        summary.text = "Checking $total sites…"

        for (site in sitesToCheck) {
            executor.execute {
                val result = checkSite(site, username)
                mainHandler.post {
                    if (currentRun != runId) return@post
                    addResultView(result)
                    val done = completed.incrementAndGet()
                    progressText.text = "Checked $done / $total"
                    if (done == total) finishSearch()
                }
            }
        }
    }

    private fun finishSearch() {
        progress.visibility = View.GONE
        progressText.text = "Search complete."
        searchButton.isEnabled = true
        usernameInput.isEnabled = true

        val children = resultsContainer.childCount
        summary.text = "Complete • $children results. Tap a CLAIMED profile to open it."
    }

    private fun checkSite(site: Site, username: String): ResultItem {
        val profileUrl = interpolate(site.url, username)
        try {
            if (site.regexCheck != null && !Regex(site.regexCheck).containsMatchIn(username)) {
                return ResultItem(site, username, profileUrl, Status.ILLEGAL)
            }

            var probeUrl = site.urlProbe?.let { interpolate(it, username) } ?: profileUrl
            val method = site.requestMethod?.uppercase(Locale.ROOT)
                ?: if (site.errorType.contains("status_code")) "HEAD" else "GET"

            val builder = Request.Builder().url(probeUrl)
                .header("User-Agent", USER_AGENT)

            if (method == "POST" || method == "PUT") {
                val payload = interpolateJson(site.requestPayload, username)
                val body = (payload?.toString() ?: "{}").toRequestBody(
                    "application/json; charset=utf-8".toMediaType()
                )
                builder.method(method, body)
            } else {
                builder.method(method, null)
            }

            val request = builder.build()
            val callClient = if (site.errorType.contains("response_url")) {
                client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            } else client

            callClient.newCall(request).execute().use { response ->
                val bodyText = try { response.body?.string() ?: "" } catch (_: Exception) { "" }
                val code = response.code

                val waf = WAF_MESSAGES.any { bodyText.contains(it) }
                if (waf) return ResultItem(site, username, profileUrl, Status.WAF, code)

                if (site.errorType.any { it !in setOf("message", "status_code", "response_url") }) {
                    return ResultItem(site, username, profileUrl, Status.UNKNOWN, code, "Unknown detection method")
                }

                var status: Status? = null

                if (site.errorType.contains("message")) {
                    val errors = when (val msg = site.errorMsg) {
                        is String -> listOf(msg)
                        is JSONArray -> (0 until msg.length()).map { msg.optString(it) }
                        else -> emptyList()
                    }
                    status = if (errors.any { bodyText.contains(it) }) Status.AVAILABLE else Status.CLAIMED
                }

                if (site.errorType.contains("status_code") && status != Status.AVAILABLE) {
                    status = if (site.errorCode.isNotEmpty() && site.errorCode.contains(code)) {
                        Status.AVAILABLE
                    } else if (code !in 200..299) {
                        Status.AVAILABLE
                    } else {
                        Status.CLAIMED
                    }
                }

                if (site.errorType.contains("response_url") && status != Status.AVAILABLE) {
                    status = if (code in 200..299) Status.CLAIMED else Status.AVAILABLE
                }

                return ResultItem(site, username, profileUrl, status ?: Status.UNKNOWN, code)
            }
        } catch (e: IOException) {
            return ResultItem(site, username, profileUrl, Status.ERROR, null, e.message ?: "Network error")
        } catch (e: Exception) {
            return ResultItem(site, username, profileUrl, Status.ERROR, null, e.message ?: "Error")
        }
    }

    private fun addResultView(result: ResultItem) {
        val color = when (result.status) {
            Status.CLAIMED -> Color.rgb(27, 122, 61)
            Status.AVAILABLE -> Color.rgb(190, 65, 45)
            Status.WAF -> Color.rgb(190, 125, 15)
            Status.ILLEGAL -> Color.GRAY
            Status.UNKNOWN, Status.ERROR -> Color.DKGRAY
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.rgb(247, 247, 247))
            setOnClickListener {
                if (result.status == Status.CLAIMED) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.profileUrl))) }
                    catch (_: Exception) {}
                }
            }
        }

        val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        line.addView(TextView(this).apply {
            text = result.site.name
            textSize = 16f
            setTextColor(Color.rgb(30, 30, 30))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        line.addView(TextView(this).apply {
            text = result.status.name
            textSize = 12f
            setTextColor(color)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        card.addView(line)

        val sub = TextView(this).apply {
            text = buildString {
                if (result.httpCode != null) append("HTTP ${result.httpCode} • ")
                append(result.profileUrl)
                if (result.detail.isNotEmpty()) append("\n${result.detail}")
            }
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, 4, 0, 0)
        }
        card.addView(sub)

        resultsContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 6
        })
    }

    private fun interpolate(template: String, username: String): String =
        template.replace("{}", username)

    private fun interpolateJson(value: Any?, username: String): Any? {
        return when (value) {
            is JSONObject -> {
                val out = JSONObject()
                val keys = value.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out.put(k, interpolateJson(value.opt(k), username))
                }
                out
            }
            is JSONArray -> {
                val out = JSONArray()
                for (i in 0 until value.length()) out.put(interpolateJson(value.opt(i), username))
                out
            }
            is String -> value.replace("{}", username)
            else -> value
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
        super.onDestroy()
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

        private val WAF_MESSAGES = listOf(
            ".loading-spinner{visibility:hidden}body.no-js .challenge-running{display:none}",
            "<span id=\"challenge-error-text\">",
            "AwsWafIntegration.forceRefreshToken",
            "{return l.onPageView}}),Object.defineProperty(r,\"perimeterxIdentifiers\""
        )
    }
}
