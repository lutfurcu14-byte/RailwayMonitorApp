package com.example.railwaymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Background/foreground service that ports the logic of
 * railway_MULTI_ROUTE_SMART_12ROUTE_5SEC_v4.py (Selenium + Chromium/Termux)
 * into an Android WebView + JavaScript-injection engine.
 *
 * IMPORTANT / সততার সাথে জানানো হচ্ছে:
 * - এটি bangladesh railway-র অফিসিয়াল সাইটকে repeatedly poll করে। খুব ঘন ঘন
 *   (কম ইন্টারভ্যালে) চালালে আপনার IP/ডিভাইস সাময়িকভাবে ব্লক হতে পারে বা
 *   সাইটের ব্যবহারবিধি (ToS) লঙ্ঘিত হতে পারে। নিজ দায়িত্বে, যুক্তিসঙ্গত
 *   ইন্টারভ্যালে ব্যবহার করুন।
 * - Android WebView কে Selenium-এর মতো নিখুঁতভাবে "attach" করা যায় না;
 *   তাই সিলেক্টর/সাইট বদলালে এই কোডও আপডেট করা লাগতে পারে।
 */
class TicketMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.example.railwaymonitor.action.START"
        const val ACTION_STOP = "com.example.railwaymonitor.action.STOP"
        const val EXTRA_DATE = "extra_date"          // DD-MM-YYYY
        const val EXTRA_BOT_TOKEN = "extra_bot_token"
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_INTERVAL_SEC = "extra_interval_sec"

        const val ACTION_LOG = "com.example.railwaymonitor.LOG"
        const val EXTRA_LOG_MSG = "extra_log_msg"

        private const val CHANNEL_STATUS = "railway_monitor_status"
        private const val CHANNEL_ALERT = "railway_monitor_alert"
        private const val NOTIF_ID_STATUS = 1001
        private var alertNotifId = 2000

        const val RAILWAY_HOME = "https://eticket.railway.gov.bd/"
        const val TRAIN_CLASS = "S_CHAIR"
        const val NO_TICKET_MARKER = "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"

        val ROUTES = listOf(
            "Sylhet" to "Dhaka",
            "Maijgaon" to "Dhaka",
            "Kulaura" to "Dhaka",
            "Shamshernagar" to "Dhaka",
            "Sreemangal" to "Dhaka",
            "Shaistaganj" to "Dhaka",
            "Sylhet" to "Biman_Bandar",
            "Maijgaon" to "Biman_Bandar",
            "Kulaura" to "Biman_Bandar",
            "Shamshernagar" to "Biman_Bandar",
            "Sreemangal" to "Biman_Bandar",
            "Shaistaganj" to "Biman_Bandar"
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var monitorJob: Job? = null
    private var webView: WebView? = null

    private var targetDate = "06-09-2026"
    private var botToken: String? = null
    private var chatId: String? = null
    private var intervalSec = 30

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> {
                targetDate = intent?.getStringExtra(EXTRA_DATE) ?: targetDate
                botToken = intent?.getStringExtra(EXTRA_BOT_TOKEN)
                chatId = intent?.getStringExtra(EXTRA_CHAT_ID)
                intervalSec = intent?.getIntExtra(EXTRA_INTERVAL_SEC, 30) ?: 30
                startForeground(NOTIF_ID_STATUS, buildStatusNotification("চালু হচ্ছে..."))
                startMonitoring()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    // ------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------

    private fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        setupWebView()
        monitorJob = serviceScope.launch {
            log("মনিটরিং শুরু হলো | তারিখ: $targetDate | ${ROUTES.size} রুট")
            var cycle = 0
            while (isActive) {
                cycle++
                log("===== CYCLE #$cycle শুরু =====")
                for ((index, route) in ROUTES.withIndex()) {
                    if (!isActive) break
                    val (from, to) = route
                    updateStatus("[$cycle] ${index + 1}/${ROUTES.size}: $from → $to")
                    try {
                        checkOneRoute(from, to)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("✗ রুট এরর ($from→$to): ${e.message}")
                    }
                    delay(2500) // routes-এর মাঝে ছোট বিরতি, সাইটকে বেশি চাপ না দিতে
                }
                log("===== CYCLE #$cycle শেষ, ${intervalSec}s পর আবার শুরু =====")
                delay(intervalSec * 1000L)
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------
    // WEBVIEW SETUP  (headless: not attached to any window)
    // ------------------------------------------------------------

    private fun setupWebView() {
        val wv = WebView(applicationContext)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.databaseEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true

        // WebView needs real, non-zero layout dimensions for offsetWidth /
        // getBoundingClientRect-based "is it visible" checks (used heavily
        // by the ported JS) to work correctly even though it is never
        // actually shown on screen.
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, 1080, 1920)

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pageFinishedSignal?.let { if (!it.isCompleted) it.complete(Unit) }
            }
        }
        webView = wv
    }

    private var pageFinishedSignal: CompletableDeferred<Unit>? = null

    private suspend fun loadHome() {
        val wv = webView ?: return
        pageFinishedSignal = CompletableDeferred()
        wv.loadUrl(RAILWAY_HOME)
        withTimeoutOrNull(20_000) { pageFinishedSignal?.await() }
        delay(1500)
    }

    private suspend fun runJs(js: String): String = withContext(Dispatchers.Main) {
        val wv = webView ?: return@withContext "null"
        suspendCancellableCoroutine { cont ->
            wv.evaluateJavascript(js) { result ->
                if (cont.isActive) cont.resume(result ?: "null") {}
            }
        }
    }

    // ------------------------------------------------------------
    // ONE ROUTE CHECK  (mirrors perform_search + wait_for_result +
    // check_ticket_availability from the python script)
    // ------------------------------------------------------------

    private suspend fun checkOneRoute(fromCity: String, toCity: String) {
        log("\n--- রুট: $fromCity → $toCity ---")
        loadHome()

        // 1) "I AGREE" ডিসক্লেইমার থাকলে ক্লিক করো
        runJs(JS.clickTextButton("I AGREE"))
        runJs(JS.clickTextButton("I Agree"))
        delay(500)

        // 2) From / To সিটি সিলেক্ট
        selectCity("fromcity", fromCity)
        selectCity("tocity", toCity)

        // 3) তারিখ (Angular ফর্ম-কন্ট্রোল সরাসরি ফোর্স-সিঙ্ক)
        val isoDate = ddmmyyyyToIso(targetDate)
        runJs(JS.forceSyncDate(isoDate))
        delay(400)

        // 4) ক্লাস সিলেক্ট (S_CHAIR)
        runJs(JS.selectClass(TRAIN_CLASS))
        delay(600)

        // 5) খালি জায়গায় ক্লিক করে ফোকাস সরানো + ডেটপিকার বন্ধ করা
        runJs("document.body.click(); document.body.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));")
        delay(700)

        // ফর্ম ভ্যালিডেশন শেষ হওয়া পর্যন্ত অপেক্ষা করে সার্চ বাটনে ক্লিক
        val searchClicked = waitAndClickSearch()
        if (!searchClicked) {
            log("✗ সার্চ বাটন রেডি হয়নি, এই রুট স্কিপ করা হলো।")
            return
        }

        // 6) রেজাল্ট পেজ ওয়েট করা
        val resultState = waitForResult()
        if (resultState == ResultState.NO_TICKET) {
            log("✗ কোনো টিকেট নেই: $fromCity → $toCity")
            return
        }
        if (resultState == ResultState.TIMEOUT) {
            log("⚠ রেজাল্ট পেজ টাইমআউট: $fromCity → $toCity")
            return
        }

        // 7) টিকেট এভেইলেবিলিটি পার্স করা
        delay(5000)
        val availability = fetchAvailability()
        if (availability.isNullOrEmpty()) {
            log("✗ কোনো টিকেট পাওয়া যায়নি: $fromCity → $toCity")
            return
        }

        log("🎫 টিকেট পাওয়া গেছে! $fromCity → $toCity (${availability.size} ক্লাস)")
        val message = buildTelegramMessage(fromCity, toCity, availability)
        sendTelegram(message)
        showAlertNotification(fromCity, toCity, message)
    }

    private suspend fun selectCity(controlName: String, city: String) {
        runJs(JS.typeIntoCityField(controlName, city))
        delay(900)
        runJs(JS.clickCityOption(city))
        delay(700)
        val finalValue = runJs(JS.getInputValue(controlName)).trim('"')
        if (!finalValue.equals(city, ignoreCase = true)) {
            log("⚠ '$controlName' এর মান '$finalValue' — প্রত্যাশিত '$city' মেলেনি (আরেকবার চেষ্টা)")
            runJs(JS.typeIntoCityField(controlName, city))
            delay(900)
            runJs(JS.clickCityOption(city))
            delay(700)
        }
    }

    private suspend fun waitAndClickSearch(): Boolean {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            val res = runJs(JS.clickSearchIfReady())
            if (res.contains("\"ok\":true")) return true
            delay(700)
        }
        return false
    }

    private enum class ResultState { OK, NO_TICKET, TIMEOUT }

    private suspend fun waitForResult(): ResultState {
        val deadline = System.currentTimeMillis() + 90_000
        val noTicketDeadline = minOf(deadline, System.currentTimeMillis() + 6_000)

        // আগে দ্রুত "no ticket" পেজ কিনা দেখা
        while (System.currentTimeMillis() < noTicketDeadline) {
            val urlOk = runJs("location.href.includes('/booking/train/search')")
            if (urlOk.trim() == "true") {
                val noTicket = runJs(JS.checkNoTicketMarker())
                if (noTicket.trim() == "true") return ResultState.NO_TICKET
                break
            }
            delay(600)
        }

        // রেজাল্ট URL-এর জন্য অপেক্ষা
        while (System.currentTimeMillis() < deadline) {
            val urlOk = runJs("location.href.includes('/booking/train/search')")
            if (urlOk.trim() == "true") {
                delay(3000) // Angular রেন্ডার শেষ হওয়ার জন্য বাড়তি সময়
                return ResultState.OK
            }
            delay(1000)
        }
        return ResultState.TIMEOUT
    }

    private suspend fun fetchAvailability(): List<TicketRow>? {
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            val raw = runJs(JS.checkAvailability())
            try {
                val arr = JSONArray(raw)
                if (arr.length() > 0) {
                    val rows = mutableListOf<TicketRow>()
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val available = o.optInt("available", 0)
                        val bookNow = o.optBoolean("book_now_enabled", false)
                        val availByClass = o.optBoolean("available_by_class", false)
                        if (available > 0 || bookNow || availByClass) {
                            rows.add(
                                TicketRow(
                                    train = o.optString("train", "UNKNOWN"),
                                    className = o.optString("class_name", TRAIN_CLASS),
                                    available = available
                                )
                            )
                        }
                    }
                    if (rows.isNotEmpty()) return rows
                    return null // ডেটা এসেছে কিন্তু কোনো সিট খালি নেই
                }
            } catch (_: Exception) {
                // JSON parse না হলে আরেকবার চেষ্টা
            }
            delay(2000)
        }
        return null
    }

    data class TicketRow(val train: String, val className: String, val available: Int)

    private fun buildTelegramMessage(from: String, to: String, rows: List<TicketRow>): String {
        val sb = StringBuilder()
        sb.append("🎫 BANGLADESH RAILWAY TICKET AVAILABLE\n\n")
        sb.append("Route: $from → $to\n")
        sb.append("Date: $targetDate\n\n")
        for (r in rows) {
            sb.append("🚆 ${r.train}\n")
            sb.append("💺 Class: ${r.className}\n")
            sb.append("🎟 Tickets: ${r.available}\n\n")
        }
        return sb.toString()
    }

    // ------------------------------------------------------------
    // TELEGRAM  (mirrors send_telegram_message from the python script)
    // ------------------------------------------------------------

    private suspend fun sendTelegram(message: String) = withContext(Dispatchers.IO) {
        val token = botToken
        val chat = chatId
        if (token.isNullOrBlank() || chat.isNullOrBlank()) {
            log("⚠ Telegram token/chat id সেট করা নেই।")
            return@withContext
        }
        try {
            val url = URL("https://api.telegram.org/bot$token/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val body = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                "&text=" + URLEncoder.encode(message, "UTF-8")

            OutputStreamWriter(conn.outputStream).use { it.write(body) }

            val code = conn.responseCode
            if (code in 200..299) {
                log("✓ Telegram নোটিফিকেশন পাঠানো হয়েছে।")
            } else {
                log("✗ Telegram error, HTTP $code")
            }
            conn.disconnect()
        } catch (e: Exception) {
            log("✗ Telegram send failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------
    // NOTIFICATIONS
    // ------------------------------------------------------------

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_STATUS, "Monitor Status", NotificationManager.IMPORTANCE_LOW)
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT, "Ticket Alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    private fun buildStatusNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setContentTitle("Railway Ticket Monitor চালু আছে")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setOngoing(true)
            .build()
    }

    private fun updateStatus(text: String) {
        log(text)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_STATUS, buildStatusNotification(text))
    }

    private fun showAlertNotification(from: String, to: String, message: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setContentTitle("🎫 টিকেট পাওয়া গেছে: $from → $to")
            .setContentText(message.lines().firstOrNull { it.isNotBlank() } ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(alertNotifId++, notif)
    }

    private fun log(message: String) {
        Log.d("TicketMonitor", message)
        val intent = Intent(ACTION_LOG).putExtra(EXTRA_LOG_MSG, message)
        sendBroadcast(intent)
    }

    private fun ddmmyyyyToIso(ddmmyyyy: String): String {
        val parts = ddmmyyyy.split("-")
        return if (parts.size == 3) "${parts[2]}-${parts[1]}-${parts[0]}" else ddmmyyyy
    }
}
