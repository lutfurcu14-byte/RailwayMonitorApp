package com.example.railwaymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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

class TicketMonitorService : Service() {

    companion object {
        const val ACTION_START = "com.example.railwaymonitor.action.START"
        const val ACTION_STOP = "com.example.railwaymonitor.action.STOP"

        const val EXTRA_DATE = "extra_date"
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
        const val NO_TICKET_MARKER =
            "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"

        data class RouteGroup(
            val to: String,
            val froms: List<String>
        )

        val ROUTE_GROUPS = listOf(
            RouteGroup(
                "Dhaka",
                listOf(
                    "Sylhet",
                    "Maijgaon",
                    "Kulaura",
                    "Shamshernagar",
                    "Sreemangal",
                    "Shaistaganj"
                )
            ),
            RouteGroup(
                "Biman_Bandar",
                listOf(
                    "Sylhet",
                    "Maijgaon",
                    "Kulaura",
                    "Shamshernagar",
                    "Sreemangal",
                    "Shaistaganj"
                )
            )
        )

        private const val MAX_LOG_LINES = 400

        private val logBuffer =
            java.util.Collections.synchronizedList(mutableListOf<String>())

        private val timeFmt =
            java.text.SimpleDateFormat(
                "HH:mm:ss",
                java.util.Locale.getDefault()
            )

        fun getLogSnapshot(): List<String> =
            synchronized(logBuffer) {
                logBuffer.toList()
            }
    }

    private val serviceScope =
        CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var monitorJob: Job? = null
    private var webView: WebView? = null
    private var overlayView: WebView? = null

    private var pageFinishedSignal: CompletableDeferred<Unit>? = null

    private var targetDate = "06-09-2026"
    private var botToken: String? = null
    private var chatId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }

            else -> {
                targetDate =
                    intent?.getStringExtra(EXTRA_DATE)
                        ?: targetDate

                botToken =
                    intent?.getStringExtra(EXTRA_BOT_TOKEN)

                chatId =
                    intent?.getStringExtra(EXTRA_CHAT_ID)

                startForeground(
                    NOTIF_ID_STATUS,
                    buildStatusNotification("চালু হচ্ছে...")
                )

                startMonitoring()
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ============================================================
    // MONITOR LOOP
    // ============================================================

    private fun startMonitoring() {

        if (monitorJob?.isActive == true) return

        setupWebView()

        monitorJob = serviceScope.launch {

            val totalRoutes =
                ROUTE_GROUPS.sumOf { it.froms.size }

            log(
                "মনিটরিং শুরু | তারিখ: $targetDate | " +
                    "$totalRoutes রুট"
            )

            var cycle = 0

            while (isActive) {

                cycle++

                log("========== CYCLE #$cycle START ==========")

                var routeCounter = 0

                for (group in ROUTE_GROUPS) {

                    if (!isActive) break

                    /*
                     * একটি group-এর প্রথম route সবসময় FULL।
                     * কোনো route-এ NO TICKET পাওয়া গেলে পরের route
                     * একই result page-এর suggested Search দিয়ে চেষ্টা হবে।
                     */
                    var lastWasNoTicket = false

                    for ((index, fromCity) in group.froms.withIndex()) {

                        if (!isActive) break

                        routeCounter++

                        updateStatus(
                            "[$cycle] $routeCounter/$totalRoutes: " +
                                "$fromCity → ${group.to}"
                        )

                        try {

                            lastWasNoTicket =
                                if (index == 0 || !lastWasNoTicket) {

                                    checkRouteFull(
                                        fromCity,
                                        group.to
                                    )

                                } else {

                                    checkRouteQuick(
                                        fromCity,
                                        group.to
                                    )
                                }

                        } catch (e: CancellationException) {

                            throw e

                        } catch (e: Exception) {

                            log(
                                "✗ Route error " +
                                    "($fromCity → ${group.to}): " +
                                    "${e.message}"
                            )

                            lastWasNoTicket = false
                        }

                        /*
                         * প্রত্যেক route-এর result-এর পরে 2 sec।
                         */
                        if (isActive) {
                            log("পরবর্তী রুটের আগে 2 sec অপেক্ষা...")
                            delay(2_000)
                        }
                    }
                }

                /*
                 * Odd cycle = 13 sec
                 * Even cycle = 14 sec
                 */
                val cyclePause =
                    if (cycle % 2 == 1) 13 else 14

                log(
                    "========== CYCLE #$cycle END | " +
                        "$cyclePause sec pause =========="
                )

                if (isActive) {
                    delay(cyclePause * 1000L)
                }
            }
        }
    }

    private fun stopMonitoring() {

        monitorJob?.cancel()
        monitorJob = null

        webView?.let { wv ->

            try {
                wv.stopLoading()
            } catch (_: Exception) {
            }

            try {
                if (overlayView === wv) {

                    val wm =
                        getSystemService(WINDOW_SERVICE)
                            as android.view.WindowManager

                    wm.removeView(wv)
                }
            } catch (_: Exception) {
            }

            try {
                wv.destroy()
            } catch (_: Exception) {
            }
        }

        webView = null
        overlayView = null

        stopForeground(STOP_FOREGROUND_REMOVE)

        try {
            stopSelf()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun setupWebView() {

        val wv = WebView(applicationContext)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.databaseEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true

        wv.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    pageFinishedSignal?.let {
                        if (!it.isCompleted) {
                            it.complete(Unit)
                        }
                    }
                }
            }

        try {

            val wm =
                getSystemService(WINDOW_SERVICE)
                    as android.view.WindowManager

            val overlayType =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                    android.view.WindowManager.LayoutParams
                        .TYPE_APPLICATION_OVERLAY

                } else {

                    @Suppress("DEPRECATION")
                    android.view.WindowManager.LayoutParams
                        .TYPE_SYSTEM_ALERT
                }

            val params =
                android.view.WindowManager.LayoutParams(
                    1,
                    1,
                    overlayType,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    android.graphics.PixelFormat.TRANSLUCENT
                )

            params.gravity =
                android.view.Gravity.TOP or
                    android.view.Gravity.START

            wm.addView(wv, params)

            overlayView = wv

            log("✓ WebView overlay window যুক্ত হয়েছে")

        } catch (e: Exception) {

            log(
                "⚠ Overlay যুক্ত করা যায়নি: " +
                    "${e.message}"
            )

            wv.measure(
                View.MeasureSpec.makeMeasureSpec(
                    1080,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    1920,
                    View.MeasureSpec.EXACTLY
                )
            )

            wv.layout(
                0,
                0,
                1080,
                1920
            )
        }

        wv.onResume()
        wv.resumeTimers()

        webView = wv
    }

    private suspend fun loadHome() {

        val wv = webView ?: return

        pageFinishedSignal =
            CompletableDeferred()

        wv.loadUrl(RAILWAY_HOME)

        withTimeoutOrNull(20_000) {
            pageFinishedSignal?.await()
        }

        delay(1500)
    }

    private suspend fun runJs(
        js: String
    ): String =
        withContext(Dispatchers.Main) {

            val wv =
                webView
                    ?: return@withContext "null"

            suspendCancellableCoroutine { cont ->

                wv.evaluateJavascript(js) { result ->

                    if (cont.isActive) {
                        cont.resume(
                            result ?: "null"
                        ) {}
                    }
                }
            }
        }

    // ============================================================
    // FULL ROUTE
    // ============================================================

    private suspend fun checkRouteFull(
        fromCity: String,
        toCity: String
    ): Boolean {

        log(
            "\n--- FULL ROUTE: " +
                "$fromCity → $toCity ---"
        )

        loadHome()

        runJs(
            JS.clickTextButton("I AGREE")
        )

        runJs(
            JS.clickTextButton("I Agree")
        )

        delay(500)

        // FROM
        if (!selectCity("fromcity", fromCity)) {
            log(
                "✗ FROM city set failed: $fromCity"
            )
            return false
        }

        // TO
        if (!selectCity("tocity", toCity)) {
            log(
                "✗ TO city set failed: $toCity"
            )
            return false
        }

        // DATE
        if (!setJourneyDate(targetDate)) {
            log(
                "✗ Date set failed: $targetDate"
            )
            return false
        }

        delay(500)

        // CLASS
        if (!selectTrainClass()) {
            log(
                "✗ $TRAIN_CLASS selection failed"
            )
            return false
        }

        delay(500)

        // close possible popup/dropdown
        runJs(
            """
            (function(){
                try{
                    document.body.click();
                    document.body.dispatchEvent(
                        new KeyboardEvent(
                            'keydown',
                            {
                                key:'Escape',
                                bubbles:true
                            }
                        )
                    );
                }catch(e){}
            })();
            """.trimIndent()
        )

        delay(500)

        // Final verification
        val formState =
            runJs(
                JS.getSearchFormState(TRAIN_CLASS)
            )

        log(
            "FORM STATE: $formState"
        )

        if (!formState.contains("\"ready\":true")) {

            log(
                "⚠ Search form ready নয়। " +
                    "আরেকবার verify করা হচ্ছে..."
            )

            delay(1000)

            val retryState =
                runJs(
                    JS.getSearchFormState(TRAIN_CLASS)
                )

            if (!retryState.contains("\"ready\":true")) {

                log(
                    "✗ From/To/Date/Class সম্পূর্ণ ready নয়"
                )

                return false
            }
        }

        // SEARCH
        val clicked =
            waitAndClickSearch()

        if (!clicked) {

            log(
                "✗ Search button click করা যায়নি"
            )

            return false
        }

        log("✓ Search Trains clicked")

        return handleResultPage(
            fromCity,
            toCity,
            true
        )
    }

    // ============================================================
    // QUICK ROUTE
    // ============================================================

    private suspend fun checkRouteQuick(
        fromCity: String,
        toCity: String
    ): Boolean {

        log(
            "\n--- QUICK ROUTE: " +
                "$fromCity → $toCity ---"
        )

        val before =
            runJs(JS.getBodyText())

        /*
         * এখন আর সবসময় Search button #1 click করা হবে না।
         * JS নিজে expected FROM/TO text অনুযায়ী
         * suggested route-এর Search button খুঁজবে।
         */
        val clickResult =
            runJs(
                JS.clickSuggestedSearchForRoute(
                    fromCity,
                    toCity
                )
            )

        log(
            "Suggested Search result: $clickResult"
        )

        if (!clickResult.contains("\"ok\":true")) {

            log(
                "⚠ Expected suggested route পাওয়া যায়নি। " +
                    "FULL search-এ fallback."
            )

            return checkRouteFull(
                fromCity,
                toCity
            )
        }

        val deadline =
            System.currentTimeMillis() + 20_000

        var changed = false

        while (
            isActive &&
            System.currentTimeMillis() < deadline
        ) {

            delay(500)

            val now =
                runJs(JS.getBodyText())

            if (now != before) {

                changed = true
                break
            }
        }

        if (!changed) {

            log(
                "⚠ Suggested Search-এর পরে body পরিবর্তন শনাক্ত হয়নি"
            )
        }

        /*
         * Result page render হওয়ার জন্য একটু সময়।
         */
        delay(1500)

        /*
         * Expected route verification.
         */
        val routeCheck =
            runJs(
                JS.verifyResultRoute(
                    fromCity,
                    toCity
                )
            )

        log(
            "QUICK route verification: $routeCheck"
        )

        if (
            routeCheck.contains("\"ok\":false")
        ) {

            log(
                "⚠ Expected route নিশ্চিত করা যায়নি"
            )
        }

        return handleResultPage(
            fromCity,
            toCity,
            false
        )
    }

    // ============================================================
    // CITY
    // ============================================================

    private suspend fun selectCity(
        controlName: String,
        city: String
    ): Boolean {

        repeat(3) { attempt ->

            runJs(
                JS.typeIntoCityField(
                    controlName,
                    city
                )
            )

            delay(1000)

            val optionResult =
                runJs(
                    JS.clickCityOption(city)
                )

            log(
                "$controlName → $city | " +
                    "option=$optionResult"
            )

            delay(700)

            val finalValue =
                runJs(
                    JS.getInputValue(controlName)
                ).trim('"')

            if (
                finalValue.equals(
                    city,
                    ignoreCase = true
                )
            ) {

                log(
                    "✓ $controlName = $city"
                )

                return true
            }

            log(
                "⚠ Attempt ${attempt + 1}: " +
                    "$controlName='$finalValue', " +
                    "expected='$city'"
            )

            delay(500)
        }

        return false
    }

    // ============================================================
    // DATE
    // ============================================================

    private suspend fun setJourneyDate(
        ddmmyyyy: String
    ): Boolean {

        val parts =
            ddmmyyyy.split("-")

        if (parts.size != 3) {

            log(
                "⚠ Invalid date: $ddmmyyyy"
            )

            return false
        }

        val targetDay =
            parts[0].toIntOrNull()
                ?: return false

        val targetMonthIndex =
            (parts[1].toIntOrNull()
                ?: return false) - 1

        val targetYear =
            parts[2].toIntOrNull()
                ?: return false

        if (
            targetMonthIndex !in 0..11 ||
            targetDay !in 1..31
        ) {
            return false
        }

        val monthNames =
            listOf(
                "January",
                "February",
                "March",
                "April",
                "May",
                "June",
                "July",
                "August",
                "September",
                "October",
                "November",
                "December"
            )

        val targetMonthName =
            monthNames[targetMonthIndex]

        var opened = false

        repeat(15) {

            val result =
                runJs(
                    JS.openDatePicker()
                )

            if (
                result.contains("\"ok\":true")
            ) {

                opened = true
                return@repeat
            }

            delay(700)
        }

        if (!opened) {

            log(
                "✗ Date picker খুলতে পারিনি"
            )

            log(
                "Date diagnostic: " +
                    runJs(JS.dumpDateFieldHtml())
            )

            return false
        }

        delay(700)

        var correctMonth = false

        repeat(30) {

            val headerRaw =
                runJs(
                    JS.readCalendarHeader()
                )

            val month =
                Regex(
                    "\"month\":\"([^\"]*)\""
                )
                    .find(headerRaw)
                    ?.groupValues
                    ?.get(1)

            val year =
                Regex(
                    "\"year\":\"([^\"]*)\""
                )
                    .find(headerRaw)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()

            if (
                month == targetMonthName &&
                year == targetYear
            ) {

                correctMonth = true
                return@repeat
            }

            if (
                month == null ||
                year == null
            ) {
                delay(300)
                return@repeat
            }

            val currentIndex =
                monthNames.indexOf(month) +
                    year * 12

            val targetIndex =
                targetMonthIndex +
                    targetYear * 12

            val direction =
                if (targetIndex > currentIndex)
                    "next"
                else
                    "previous"

            val arrow =
                runJs(
                    JS.clickCalendarArrow(
                        direction
                    )
                )

            if (
                !arrow.contains("\"ok\":true")
            ) {
                break
            }

            delay(350)
        }

        if (!correctMonth) {

            log(
                "✗ Target month/year পাওয়া যায়নি: " +
                    "$targetMonthName $targetYear"
            )

            return false
        }

        val dayResult =
            runJs(
                JS.clickCalendarDay(
                    targetDay
                )
            )

        log(
            "Calendar day result: $dayResult"
        )

        delay(700)

        val finalValue =
            runJs(
                JS.getDateInputValue()
            ).trim('"')

        log(
            "Date input after selection: $finalValue"
        )

        /*
         * Exact format site অনুযায়ী হতে পারে।
         * তাই expected day/month/year দিয়ে verify করা হচ্ছে।
         */
        val dateOk =
            runJs(
                JS.verifyDateValue(
                    targetDay,
                    targetMonthIndex,
                    targetYear
                )
            )

        log(
            "Date verification: $dateOk"
        )

        return (
            dayResult.contains("\"ok\":true") &&
                dateOk.contains("\"ok\":true")
            )
    }

    // ============================================================
    // CLASS
    // ============================================================

    private suspend fun selectTrainClass(): Boolean {

        repeat(3) { attempt ->

            val result =
                runJs(
                    JS.selectClass(
                        TRAIN_CLASS
                    )
                )

            log(
                "Class selection attempt ${attempt + 1}: " +
                    result
            )

            delay(600)

            val verify =
                runJs(
                    JS.verifyClass(
                        TRAIN_CLASS
                    )
                )

            log(
                "Class verification: $verify"
            )

            if (
                verify.contains("\"ok\":true")
            ) {
                return true
            }

            delay(500)
        }

        return false
    }

    // ============================================================
    // SEARCH
    // ============================================================

    private suspend fun waitAndClickSearch(): Boolean {

        val deadline =
            System.currentTimeMillis() + 30_000

        while (
            isActive &&
            System.currentTimeMillis() < deadline
        ) {

            /*
             * আগে form ready কিনা দেখা।
             */
            val state =
                runJs(
                    JS.getSearchFormState(
                        TRAIN_CLASS
                    )
                )

            if (
                state.contains("\"ready\":true")
            ) {

                val result =
                    runJs(
                        JS.clickSearchIfReady()
                    )

                if (
                    result.contains("\"ok\":true")
                ) {
                    return true
                }
            }

            delay(700)
        }

        return false
    }

    // ============================================================
    // RESULT
    // ============================================================

    private enum class ResultState {
        OK,
        NO_TICKET,
        TIMEOUT
    }

    private suspend fun waitForResult(): ResultState {

        val deadline =
            System.currentTimeMillis() + 90_000

        val earlyDeadline =
            System.currentTimeMillis() + 8_000

        while (
            isActive &&
            System.currentTimeMillis() < earlyDeadline
        ) {

            val urlOk =
                runJs(
                    "location.href.includes('/booking/train/search')"
                )

            if (urlOk.trim() == "true") {

                val noTicket =
                    runJs(
                        JS.checkNoTicketMarker()
                    )

                if (
                    noTicket.trim() == "true"
                ) {
                    return ResultState.NO_TICKET
                }

                break
            }

            delay(600)
        }

        while (
            isActive &&
            System.currentTimeMillis() < deadline
        ) {

            val urlOk =
                runJs(
                    "location.href.includes('/booking/train/search')"
                )

            if (urlOk.trim() == "true") {

                delay(2500)

                val noTicket =
                    runJs(
                        JS.checkNoTicketMarker()
                    )

                if (
                    noTicket.trim() == "true"
                ) {
                    return ResultState.NO_TICKET
                }

                return ResultState.OK
            }

            delay(1000)
        }

        return ResultState.TIMEOUT
    }

    private suspend fun confirmActuallyNoTicket(): Boolean {

        val raw =
            runJs(
                JS.debugBookNowCount()
            )

        val enabled =
            Regex(
                "\"enabled\":(\\d+)"
            )
                .find(raw)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: 0

        if (enabled > 0) {

            log(
                "⚠ No-ticket marker আছে কিন্তু " +
                    "$enabled active BOOK NOW পাওয়া গেছে"
            )

            return false
        }

        return true
    }

    private suspend fun handleResultPage(
        fromCity: String,
        toCity: String,
        isFreshNavigation: Boolean
    ): Boolean {

        if (isFreshNavigation) {

            when (waitForResult()) {

                ResultState.NO_TICKET -> {

                    if (
                        confirmActuallyNoTicket()
                    ) {

                        log(
                            "✗ NO TICKET: " +
                                "$fromCity → $toCity"
                        )

                        return true
                    }
                }

                ResultState.TIMEOUT -> {

                    log(
                        "⚠ Result timeout: " +
                            "$fromCity → $toCity"
                    )

                    return false
                }

                ResultState.OK -> {
                    // continue
                }
            }

        } else {

            /*
             * Quick route result render.
             */
            delay(1500)

            val noTicket =
                runJs(
                    JS.checkNoTicketMarker()
                )

            if (
                noTicket.trim() == "true" &&
                confirmActuallyNoTicket()
            ) {

                log(
                    "✗ NO TICKET: " +
                        "$fromCity → $toCity"
                )

                return true
            }
        }

        /*
         * Ticket cards render করার জন্য wait.
         */
        delay(3000)

        val debug =
            runJs(
                JS.debugBookNowCount()
            )

        log(
            "BOOK NOW debug: $debug"
        )

        val availability =
            fetchAvailability()

        if (
            availability.isNullOrEmpty()
        ) {

            log(
                "✗ No ticket found: " +
                    "$fromCity → $toCity"
            )

            return false
        }

        log(
            "🎫 TICKET FOUND: " +
                "$fromCity → $toCity | " +
                "${availability.size} class"
        )

        val message =
            buildTelegramMessage(
                fromCity,
                toCity,
                availability
            )

        sendTelegram(message)

        showAlertNotification(
            fromCity,
            toCity,
            message
        )

        /*
         * Ticket পাওয়ার পর true না ফেরানোর কারণ:
         * true মানে caller-এর কাছে NO_TICKET।
         */
        return false
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

    private suspend fun fetchAvailability():
        List<TicketRow>? {

        val deadline =
            System.currentTimeMillis() + 45_000

        while (
            isActive &&
            System.currentTimeMillis() < deadline
        ) {

            val raw =
                runJs(
                    JS.checkAvailability()
                )

            try {

                val arr =
                    JSONArray(raw)

                if (arr.length() > 0) {

                    val rows =
                        mutableListOf<TicketRow>()

                    for (i in 0 until arr.length()) {

                        val obj =
                            arr.getJSONObject(i)

                        val available =
                            obj.optInt(
                                "available",
                                0
                            )

                        /*
                         * Primary rule:
                         * available number > 0.
                         *
                         * Enabled BOOK NOW একা ticket
                         * হিসেবে গণ্য হবে না।
                         */
                        if (available > 0) {

                            rows.add(
                                TicketRow(
                                    train =
                                        obj.optString(
                                            "train",
                                            "UNKNOWN TRAIN"
                                        ),
                                    className =
                                        obj.optString(
                                            "class_name",
                                            TRAIN_CLASS
                                        ),
                                    available =
                                        available
                                )
                            )
                        }
                    }

                    if (rows.isNotEmpty()) {
                        return rows
                    }

                    return null
                }

            } catch (_: Exception) {
                // DOM এখনও render হচ্ছে
            }

            delay(1500)
        }

        return null
    }

    data class TicketRow(
        val train: String,
        val className: String,
        val available: Int
    )

    // ============================================================
    // TELEGRAM
    // ============================================================

    private fun buildTelegramMessage(
        from: String,
        to: String,
        rows: List<TicketRow>
    ): String {

        val sb =
            StringBuilder()

        sb.append(
            "🎫 BANGLADESH RAILWAY TICKET AVAILABLE\n\n"
        )

        sb.append(
            "Route: $from → $to\n"
        )

        sb.append(
            "Date: $targetDate\n\n"
        )

        for (row in rows) {

            sb.append(
                "🚆 ${row.train}\n"
            )

            sb.append(
                "💺 Class: ${row.className}\n"
            )

            sb.append(
                "🎟 Tickets: ${row.available}\n\n"
            )
        }

        return sb.toString()
    }

    private suspend fun sendTelegram(
        message: String
    ) = withContext(Dispatchers.IO) {

        val token = botToken
        val chat = chatId

        if (
            token.isNullOrBlank() ||
            chat.isNullOrBlank()
        ) {

            log(
                "⚠ Telegram token/chat ID নেই"
            )

            return@withContext
        }

        try {

            val url =
                URL(
                    "https://api.telegram.org/bot$token/sendMessage"
                )

            val conn =
                url.openConnection()
                    as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true

            conn.setRequestProperty(
                "Content-Type",
                "application/x-www-form-urlencoded"
            )

            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000

            val body =
                "chat_id=" +
                    URLEncoder.encode(
                        chat,
                        "UTF-8"
                    ) +
                    "&text=" +
                    URLEncoder.encode(
                        message,
                        "UTF-8"
                    )

            OutputStreamWriter(
                conn.outputStream
            ).use {
                it.write(body)
            }

            val code =
                conn.responseCode

            if (code in 200..299) {

                log(
                    "✓ Telegram notification sent"
                )

            } else {

                log(
                    "✗ Telegram HTTP error: $code"
                )
            }

            conn.disconnect()

        } catch (e: Exception) {

            log(
                "✗ Telegram error: ${e.message}"
            )
        }
    }

    // ============================================================
    // NOTIFICATION
    // ============================================================

    private fun createChannels() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val nm =
                getSystemService(
                    NotificationManager::class.java
                )

            val statusChannel =
                NotificationChannel(
                    CHANNEL_STATUS,
                    "Monitor Status",
                    NotificationManager.IMPORTANCE_LOW
                )

            nm.createNotificationChannel(
                statusChannel
            )

            val alertChannel =
                NotificationChannel(
                    CHANNEL_ALERT,
                    "Ticket Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                )

            /*
             * Android 8+ notification sound
             * channel level-এ set করতে হয়।
             */
            alertChannel.enableVibration(true)

            val audioAttributes =
                android.media.AudioAttributes.Builder()
                    .setUsage(
                        android.media.AudioAttributes.USAGE_NOTIFICATION
                    )
                    .setContentType(
                        android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION
                    )
                    .build()

            alertChannel.setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                audioAttributes
            )

            nm.createNotificationChannel(
                alertChannel
            )
        }
    }

    private fun buildStatusNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_STATUS
        )
            .setContentTitle(
                "Railway Ticket Monitor চালু আছে"
            )
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_menu_search
            )
            .setOngoing(true)
            .build()
    }

    private fun updateStatus(
        text: String
    ) {

        log(text)

        val nm =
            getSystemService(
                NotificationManager::class.java
            )

        nm.notify(
            NOTIF_ID_STATUS,
            buildStatusNotification(text)
        )
    }

    private fun showAlertNotification(
        from: String,
        to: String,
        message: String
    ) {

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ALERT
            )
                .setContentTitle(
                    "🎫 Ticket Found: $from → $to"
                )
                .setContentText(
                    "S_CHAIR ticket available"
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(message)
                )
                .setSmallIcon(
                    android.R.drawable.ic_dialog_info
                )
                .setPriority(
                    NotificationCompat.PRIORITY_HIGH
                )
                .setAutoCancel(true)
                .setDefaults(
                    NotificationCompat.DEFAULT_ALL
                )
                .build()

        getSystemService(
            NotificationManager::class.java
        ).notify(
            alertNotifId++,
            notification
        )
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun log(
        message: String
    ) {

        val stamped =
            "[${timeFmt.format(java.util.Date())}] $message"

        Log.d(
            "TicketMonitor",
            stamped
        )

        synchronized(logBuffer) {

            logBuffer.add(stamped)

            while (
                logBuffer.size > MAX_LOG_LINES
            ) {
                logBuffer.removeAt(0)
            }
        }

        sendBroadcast(
            Intent(ACTION_LOG)
                .putExtra(
                    EXTRA_LOG_MSG,
                    stamped
                )
        )
    }
}