package com.example.railwaymonitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

class TicketMonitorService : Service() {

    companion object {

        const val ACTION_START =
            "com.example.railwaymonitor.action.START"

        const val ACTION_STOP =
            "com.example.railwaymonitor.action.STOP"

        const val EXTRA_DATE =
            "extra_date"

        const val EXTRA_BOT_TOKEN =
            "extra_bot_token"

        const val EXTRA_CHAT_ID =
            "extra_chat_id"

        const val EXTRA_INTERVAL_SEC =
            "extra_interval_sec"

        const val ACTION_LOG =
            "com.example.railwaymonitor.LOG"

        const val EXTRA_LOG_MSG =
            "extra_log_msg"

        private const val CHANNEL_STATUS =
            "railway_monitor_status"

        private const val CHANNEL_ALERT =
            "railway_monitor_alert_v2"

        private const val NOTIF_ID_STATUS =
            1001

        private var alertNotifId =
            2000

        const val RAILWAY_HOME =
            "https://eticket.railway.gov.bd/"

        const val TRAIN_CLASS =
            "S_CHAIR"

        const val NO_TICKET_MARKER =
            "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"

        private const val MAX_LOG_LINES =
            400

        data class RouteGroup(
            val to: String,
            val froms: List<String>
        )

        val ROUTE_GROUPS =
            listOf(

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

        private val logBuffer =
            Collections.synchronizedList(
                mutableListOf<String>()
            )

        private val timeFmt =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            )

        fun getLogSnapshot(): List<String> =
            synchronized(logBuffer) {
                logBuffer.toList()
            }
    }

    // ============================================================
    // SERVICE STATE
    // ============================================================

    private val serviceScope =
        CoroutineScope(
            Dispatchers.Main +
                SupervisorJob()
        )

    private var monitorJob: Job? =
        null

    private var webView: WebView? =
        null

    private var overlayView: WebView? =
        null

    private var pageFinishedSignal:
        CompletableDeferred<Unit>? =
        null

    private var targetDate =
        "06-09-2026"

    private var botToken:
        String? =
        null

    private var chatId:
        String? =
        null

    // ============================================================
    // SERVICE
    // ============================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

            ACTION_START,
            null -> {

                targetDate =
                    intent?.getStringExtra(
                        EXTRA_DATE
                    ) ?: targetDate

                botToken =
                    intent?.getStringExtra(
                        EXTRA_BOT_TOKEN
                    )

                chatId =
                    intent?.getStringExtra(
                        EXTRA_CHAT_ID
                    )

                startForeground(
                    NOTIF_ID_STATUS,
                    buildStatusNotification(
                        "চালু হচ্ছে..."
                    )
                )

                startMonitoring()
            }

            else -> {

                targetDate =
                    intent.getStringExtra(
                        EXTRA_DATE
                    ) ?: targetDate

                botToken =
                    intent.getStringExtra(
                        EXTRA_BOT_TOKEN
                    )

                chatId =
                    intent.getStringExtra(
                        EXTRA_CHAT_ID
                    )

                startForeground(
                    NOTIF_ID_STATUS,
                    buildStatusNotification(
                        "চালু হচ্ছে..."
                    )
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

        if (
            monitorJob?.isActive == true
        ) {
            return
        }

        setupWebView()

        monitorJob =
            serviceScope.launch {

                val totalRoutes =
                    ROUTE_GROUPS.sumOf {
                        it.froms.size
                    }

                log(
                    "================================"
                )

                log(
                    "MONITORING STARTED"
                )

                log(
                    "Date: $targetDate"
                )

                log(
                    "Class: $TRAIN_CLASS"
                )

                log(
                    "Total routes: $totalRoutes"
                )

                log(
                    "================================"
                )

                var cycle =
                    0

                while (
                    currentCoroutineContext()
                        .isActive
                ) {

                    cycle++

                    log(
                        "========== CYCLE #$cycle START =========="
                    )

                    var routeCounter =
                        0

                    for (
                        group in ROUTE_GROUPS
                    ) {

                        if (
                            !currentCoroutineContext()
                                .isActive
                        ) {
                            break
                        }

                        /*
                         * প্রথম route FULL।
                         *
                         * কোনো route NO TICKET হলে
                         * পরের route QUICK mode-এ যাবে।
                         *
                         * Ticket/error হলে পরের route FULL।
                         */
                        var lastWasNoTicket =
                            false

                        for (
                            index in group.froms.indices
                        ) {

                            if (
                                !currentCoroutineContext()
                                    .isActive
                            ) {
                                break
                            }

                            val fromCity =
                                group.froms[index]

                            routeCounter++

                            updateStatus(
                                "[$cycle] " +
                                    "$routeCounter/$totalRoutes: " +
                                    "$fromCity → ${group.to}"
                            )

                            try {

                                lastWasNoTicket =
                                    if (
                                        index == 0 ||
                                        !lastWasNoTicket
                                    ) {

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

                            } catch (
                                e: CancellationException
                            ) {

                                throw e

                            } catch (
                                e: Exception
                            ) {

                                log(
                                    "✗ Route error: " +
                                        "$fromCity → ${group.to} | " +
                                        "${e.message}"
                                )

                                lastWasNoTicket =
                                    false
                            }

                            /*
                             * প্রতিটি route result-এর পরে
                             * 2 second pause.
                             */
                            if (
                                currentCoroutineContext()
                                    .isActive
                            ) {

                                log(
                                    "Next route in 2 sec..."
                                )

                                delay(2_000)
                            }
                        }
                    }

                    /*
                     * Odd cycle = 13 sec
                     * Even cycle = 14 sec
                     */
                    val cyclePause =
                        if (
                            cycle % 2 == 1
                        ) {
                            13
                        } else {
                            14
                        }

                    log(
                        "========== CYCLE #$cycle END =========="
                    )

                    log(
                        "Next cycle in $cyclePause sec..."
                    )

                    if (
                        currentCoroutineContext()
                            .isActive
                    ) {
                        delay(
                            cyclePause * 1_000L
                        )
                    }
                }
            }
    }

    // ============================================================
    // STOP
    // ============================================================

    private fun stopMonitoring() {

        monitorJob?.cancel()

        monitorJob =
            null

        val wv =
            webView

        if (wv != null) {

            try {
                wv.stopLoading()
            } catch (_: Exception) {
            }

            try {

                if (
                    overlayView === wv
                ) {

                    val wm =
                        getSystemService(
                            WINDOW_SERVICE
                        ) as android.view.WindowManager

                    wm.removeView(wv)
                }

            } catch (_: Exception) {
            }

            try {
                wv.onPause()
            } catch (_: Exception) {
            }

            try {
                wv.pauseTimers()
            } catch (_: Exception) {
            }

            try {
                wv.destroy()
            } catch (_: Exception) {
            }
        }

        webView =
            null

        overlayView =
            null

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.N
            ) {

                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )

            } else {

                @Suppress("DEPRECATION")
                stopForeground(true)
            }

        } catch (_: Exception) {
        }

        log(
            "✓ Monitoring stopped"
        )

        try {
            stopSelf()
        } catch (_: Exception) {
        }
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    private fun setupWebView() {

        if (webView != null) {
            return
        }

        val wv =
            WebView(applicationContext)

        wv.settings.javaScriptEnabled =
            true

        wv.settings.domStorageEnabled =
            true

        wv.settings.databaseEnabled =
            true

        wv.settings.loadWithOverviewMode =
            true

        wv.settings.useWideViewPort =
            true

        wv.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    pageFinishedSignal?.let {

                        if (
                            !it.isCompleted
                        ) {

                            it.complete(Unit)
                        }
                    }
                }
            }

        try {

            val wm =
                getSystemService(
                    WINDOW_SERVICE
                ) as android.view.WindowManager

            val type =
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.O
                ) {

                    android.view.WindowManager
                        .LayoutParams
                        .TYPE_APPLICATION_OVERLAY

                } else {

                    @Suppress("DEPRECATION")
                    android.view.WindowManager
                        .LayoutParams
                        .TYPE_SYSTEM_ALERT
                }

            val params =
                android.view.WindowManager.LayoutParams(

                    1,
                    1,

                    type,

                    android.view.WindowManager
                        .LayoutParams
                        .FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager
                            .LayoutParams
                            .FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager
                            .LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS,

                    android.graphics.PixelFormat
                        .TRANSLUCENT
                )

            params.gravity =
                android.view.Gravity.TOP or
                    android.view.Gravity.START

            wm.addView(
                wv,
                params
            )

            overlayView =
                wv

            log(
                "✓ WebView overlay attached"
            )

        } catch (e: Exception) {

            log(
                "⚠ Overlay unavailable: " +
                    "${e.message}"
            )

            try {

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

            } catch (e2: Exception) {

                log(
                    "⚠ WebView fallback failed: " +
                        "${e2.message}"
                )
            }
        }

        try {
            wv.onResume()
        } catch (_: Exception) {
        }

        try {
            wv.resumeTimers()
        } catch (_: Exception) {
        }

        webView =
            wv
    }

    // ============================================================
    // LOAD HOME
    // ============================================================

    private suspend fun loadHome() {

        val wv =
            webView
                ?: return

        pageFinishedSignal =
            CompletableDeferred()

        try {
            wv.loadUrl(
                RAILWAY_HOME
            )
        } catch (e: Exception) {

            log(
                "✗ loadUrl error: " +
                    "${e.message}"
            )

            return
        }

        withTimeoutOrNull(
            20_000
        ) {
            pageFinishedSignal?.await()
        }

        delay(1_500)
    }

    // ============================================================
    // JAVASCRIPT
    // ============================================================

    private suspend fun runJs(
        js: String
    ): String {

        return withContext(
            Dispatchers.Main
        ) {

            val wv =
                webView
                    ?: return@withContext "null"

            suspendCancellableCoroutine { cont ->

                try {

                    wv.evaluateJavascript(
                        js
                    ) { result ->

                        if (
                            cont.isActive
                        ) {

                            cont.resume(
                                result ?: "null"
                            ) {}
                        }
                    }

                } catch (
                    e: Exception
                ) {

                    if (
                        cont.isActive
                    ) {

                        cont.resume(
                            "null"
                        ) {}
                    }
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
            ""
        )

        log(
            "--- FULL ROUTE ---"
        )

        log(
            "$fromCity → $toCity"
        )

        log(
            "Date: $targetDate"
        )

        log(
            "Class: $TRAIN_CLASS"
        )

        loadHome()

        /*
         * Cookie/consent popup.
         */
        runJs(
            JS.clickTextButton(
                "I AGREE"
            )
        )

        runJs(
            JS.clickTextButton(
                "I Agree"
            )
        )

        delay(500)

        /*
         * FROM
         */
        if (
            !selectCity(
                "fromcity",
                fromCity
            )
        ) {

            log(
                "✗ FROM failed: $fromCity"
            )

            return false
        }

        /*
         * TO
         */
        if (
            !selectCity(
                "tocity",
                toCity
            )
        ) {

            log(
                "✗ TO failed: $toCity"
            )

            return false
        }

        /*
         * DATE
         */
        if (
            !setJourneyDate(
                targetDate
            )
        ) {

            log(
                "✗ Date failed: $targetDate"
            )

            return false
        }

        delay(500)

        /*
         * CLASS
         */
        if (
            !selectTrainClass()
        ) {

            log(
                "✗ Class failed: $TRAIN_CLASS"
            )

            return false
        }

        delay(500)

        /*
         * Close dropdown/popup.
         */
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

        /*
         * Final form state.
         */
        var formState =
            runJs(
                JS.getSearchFormState(
                    TRAIN_CLASS
                )
            )

        log(
            "FORM STATE: $formState"
        )

        if (
            !formState.contains(
                "\"ready\":true"
            )
        ) {

            log(
                "⚠ Form not ready; retrying verification..."
            )

            delay(1_000)

            formState =
                runJs(
                    JS.getSearchFormState(
                        TRAIN_CLASS
                    )
                )

            log(
                "FORM STATE RETRY: $formState"
            )

            if (
                !formState.contains(
                    "\"ready\":true"
                )
            ) {

                log(
                    "✗ From/To/Date/Class not ready"
                )

                return false
            }
        }

        /*
         * Search button.
         */
        if (
            !waitAndClickSearch()
        ) {

            log(
                "✗ Search button not clicked"
            )

            return false
        }

        log(
            "✓ Search Trains clicked"
        )

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
            ""
        )

        log(
            "--- QUICK ROUTE ---"
        )

        log(
            "$fromCity → $toCity"
        )

        val before =
            runJs(
                JS.getBodyText()
            )

        val clickResult =
            runJs(
                JS.clickSuggestedSearchForRoute(
                    fromCity,
                    toCity
                )
            )

        log(
            "Suggested Search: $clickResult"
        )

        if (
            !clickResult.contains(
                "\"ok\":true"
            )
        ) {

            log(
                "⚠ Suggested route not found"
            )

            log(
                "→ Falling back to FULL route"
            )

            return checkRouteFull(
                fromCity,
                toCity
            )
        }

        /*
         * Wait for navigation/body change.
         */
        val deadline =
            System.currentTimeMillis() +
                20_000

        var changed =
            false

        while (
            currentCoroutineContext()
                .isActive &&
            System.currentTimeMillis() <
                deadline
        ) {

            delay(500)

            val now =
                runJs(
                    JS.getBodyText()
                )

            if (
                now != before
            ) {

                changed =
                    true

                break
            }
        }

        if (!changed) {

            log(
                "⚠ Body change not detected after quick search"
            )
        }

        /*
         * Give Railway result page time to render.
         */
        delay(1_500)

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

        return handleResultPage(
            fromCity,
            toCity,
            false
        )
    }

    // ============================================================
    // CITY SELECTION
    // ============================================================

    private suspend fun selectCity(
        controlName: String,
        city: String
    ): Boolean {

        for (
            attempt in 1..3
        ) {

            if (
                !currentCoroutineContext()
                    .isActive
            ) {
                return false
            }

            val typed =
                runJs(
                    JS.typeIntoCityField(
                        controlName,
                        city
                    )
                )

            log(
                "$controlName typing attempt " +
                    "$attempt: $typed"
            )

            delay(1_000)

            val option =
                runJs(
                    JS.clickCityOption(
                        city
                    )
                )

            log(
                "$controlName option: $option"
            )

            delay(700)

            val value =
                runJs(
                    JS.getInputValue(
                        controlName
                    )
                )
                    .trim('"')
                    .trim()

            log(
                "$controlName value: '$value'"
            )

            if (
                value.equals(
                    city,
                    ignoreCase = true
                )
            ) {

                log(
                    "✓ $controlName = $city"
                )

                return true
            }

            delay(500)
        }

        return false
    }

    // ============================================================
    // DATE
    // ============================================================

    private suspend fun setJourneyDate(
        date: String
    ): Boolean {

        val parts =
            date.split("-")

        if (
            parts.size != 3
        ) {

            log(
                "✗ Invalid date: $date"
            )

            return false
        }

        val day =
            parts[0].toIntOrNull()
                ?: return false

        val month =
            parts[1].toIntOrNull()
                ?: return false

        val year =
            parts[2].toIntOrNull()
                ?: return false

        if (
            day !in 1..31 ||
            month !in 1..12 ||
            year < 2020
        ) {

            log(
                "✗ Invalid date values: $date"
            )

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
            monthNames[month - 1]

        /*
         * Open calendar.
         */
        val opened =
            runJs(
                JS.openDatePicker()
            )

        log(
            "Open date picker: $opened"
        )

        if (
            !opened.contains(
                "\"ok\":true"
            )
        ) {

            log(
                "✗ Date picker could not be opened"
            )

            return false
        }

        delay(700)

        /*
         * Navigate calendar.
         *
         * for loop ব্যবহার করা হয়েছে যাতে
         * break compile-safe হয়।
         */
        var correctMonth =
            false

        for (
            step in 0 until 30
        ) {

            if (
                !currentCoroutineContext()
                    .isActive
            ) {
                return false
            }

            val headerRaw =
                runJs(
                    JS.readCalendarHeader()
                )

            val monthText =
                Regex(
                    "\"month\":\"([^\"]*)\""
                )
                    .find(headerRaw)
                    ?.groupValues
                    ?.get(1)

            val yearText =
                Regex(
                    "\"year\":\"([^\"]*)\""
                )
                    .find(headerRaw)
                    ?.groupValues
                    ?.get(1)

            val currentYear =
                yearText?.toIntOrNull()

            if (
                monthText ==
                    targetMonthName &&
                currentYear ==
                    year
            ) {

                correctMonth =
                    true

                break
            }

            if (
                monthText == null ||
                currentYear == null
            ) {

                delay(400)

                continue
            }

            val currentMonthIndex =
                monthNames.indexOf(
                    monthText
                )

            if (
                currentMonthIndex < 0
            ) {

                log(
                    "⚠ Unknown calendar month: " +
                        monthText
                )

                delay(400)

                continue
            }

            val currentIndex =
                currentYear * 12 +
                    currentMonthIndex

            val targetIndex =
                year * 12 +
                    (month - 1)

            val direction =
                if (
                    targetIndex >
                        currentIndex
                ) {
                    "next"
                } else {
                    "previous"
                }

            val arrow =
                runJs(
                    JS.clickCalendarArrow(
                        direction
                    )
                )

            if (
                !arrow.contains(
                    "\"ok\":true"
                )
            ) {

                log(
                    "✗ Calendar $direction arrow failed"
                )

                break
            }

            delay(400)
        }

        if (
            !correctMonth
        ) {

            log(
                "✗ Target month/year not found: " +
                    "$targetMonthName $year"
            )

            return false
        }

        /*
         * Click target day.
         */
        val dayResult =
            runJs(
                JS.clickCalendarDay(
                    day
                )
            )

        log(
            "Calendar day result: $dayResult"
        )

        if (
            !dayResult.contains(
                "\"ok\":true"
            )
        ) {

            log(
                "✗ Calendar day click failed"
            )

            return false
        }

        delay(700)

        val finalValue =
            runJs(
                JS.getDateInputValue()
            )
                .trim('"')
                .trim()

        log(
            "Date input: $finalValue"
        )

        val verified =
            runJs(
                JS.verifyDateValue(
                    day,
                    month - 1,
                    year
                )
            )

        log(
            "Date verification: $verified"
        )

        return verified.contains(
            "\"ok\":true"
        )
    }

    // ============================================================
    // CLASS
    // ============================================================

    private suspend fun selectTrainClass():
        Boolean {

        for (
            attempt in 1..3
        ) {

            if (
                !currentCoroutineContext()
                    .isActive
            ) {
                return false
            }

            val result =
                runJs(
                    JS.selectClass(
                        TRAIN_CLASS
                    )
                )

            log(
                "Class selection #$attempt: " +
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
                verify.contains(
                    "\"ok\":true"
                )
            ) {

                return true
            }

            delay(500)
        }

        return false
    }

    // ============================================================
    // SEARCH BUTTON
    // ============================================================

    private suspend fun waitAndClickSearch():
        Boolean {

        val deadline =
            System.currentTimeMillis() +
                30_000

        while (
            currentCoroutineContext()
                .isActive &&
            System.currentTimeMillis() <
                deadline
        ) {

            val state =
                runJs(
                    JS.getSearchFormState(
                        TRAIN_CLASS
                    )
                )

            if (
                state.contains(
                    "\"ready\":true"
                )
            ) {

                val result =
                    runJs(
                        JS.clickSearchIfReady()
                    )

                log(
                    "Search click result: $result"
                )

                if (
                    result.contains(
                        "\"ok\":true"
                    )
                ) {

                    return true
                }
            }

            delay(700)
        }

        return false
    }

    // ============================================================
    // RESULT STATE
    // ============================================================

    private enum class ResultState {
        OK,
        NO_TICKET,
        TIMEOUT
    }

    // ============================================================
    // WAIT RESULT
    // ============================================================

    private suspend fun waitForResult():
        ResultState {

        val deadline =
            System.currentTimeMillis() +
                90_000

        while (
            currentCoroutineContext()
                .isActive &&
            System.currentTimeMillis() <
                deadline
        ) {

            val urlOk =
                runJs(
                    """
                    location.href.indexOf(
                        '/booking/train/search'
                    ) >= 0
                    """.trimIndent()
                )

            if (
                urlOk.trim() ==
                    "true"
            ) {

                delay(1_500)

                val noTicket =
                    runJs(
                        JS.checkNoTicketMarker()
                    )

                if (
                    noTicket.trim() ==
                        "true"
                ) {

                    return ResultState.NO_TICKET
                }

                /*
                 * Result page exists.
                 */
                return ResultState.OK
            }

            delay(700)
        }

        return ResultState.TIMEOUT
    }

    // ============================================================
    // CONFIRM NO TICKET
    // ============================================================

    private suspend fun confirmActuallyNoTicket():
        Boolean {

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

        if (
            enabled > 0
        ) {

            log(
                "⚠ No-ticket marker but " +
                    "$enabled active BOOK NOW found"
            )

            return false
        }

        return true
    }

    // ============================================================
    // HANDLE RESULT
    // ============================================================

    private suspend fun handleResultPage(
        fromCity: String,
        toCity: String,
        isFreshNavigation: Boolean
    ): Boolean {

        if (
            isFreshNavigation
        ) {

            when (
                waitForResult()
            ) {

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
                    // Continue.
                }
            }

        } else {

            /*
             * QUICK result.
             */
            delay(1_500)

            val noTicket =
                runJs(
                    JS.checkNoTicketMarker()
                )

            if (
                noTicket.trim() ==
                    "true"
            ) {

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
        }

        /*
         * Wait for train cards.
         */
        delay(3_000)

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
                "✗ No ticket: " +
                    "$fromCity → $toCity"
            )

            return false
        }

        log(
            "🎫 TICKET FOUND: " +
                "$fromCity → $toCity | " +
                "${availability.size} result(s)"
        )

        val message =
            buildTelegramMessage(
                fromCity,
                toCity,
                availability
            )

        sendTelegram(
            message
        )

        showAlertNotification(
            fromCity,
            toCity,
            message
        )

        /*
         * Caller expects:
         * true = NO TICKET
         *
         * Therefore ticket found = false.
         */
        return false
    }

    // ============================================================
    // AVAILABILITY
    // ============================================================

    private suspend fun fetchAvailability():
        List<TicketRow>? {

        val deadline =
            System.currentTimeMillis() +
                45_000

        while (
            currentCoroutineContext()
                .isActive &&
            System.currentTimeMillis() <
                deadline
        ) {

            val raw =
                runJs(
                    JS.checkAvailability()
                )

            try {

                val array =
                    JSONArray(raw)

                val rows =
                    mutableListOf<TicketRow>()

                for (
                    i in 0 until array.length()
                ) {

                    val obj =
                        array.getJSONObject(i)

                    val available =
                        obj.optInt(
                            "available",
                            0
                        )

                    /*
                     * IMPORTANT:
                     *
                     * BOOK NOW enabled alone is NOT
                     * considered ticket availability.
                     *
                     * Only available > 0.
                     */
                    if (
                        available > 0
                    ) {

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

                if (
                    rows.isNotEmpty()
                ) {

                    return rows
                }

            } catch (
                _: Exception
            ) {
                /*
                 * DOM may still be rendering.
                 */
            }

            delay(1_500)
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
            "Date: $targetDate\n"
        )

        sb.append(
            "Class: $TRAIN_CLASS\n\n"
        )

        for (
            row in rows
        ) {

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
    ) {

        withContext(
            Dispatchers.IO
        ) {

            val token =
                botToken

            val chat =
                chatId

            if (
                token.isNullOrBlank() ||
                chat.isNullOrBlank()
            ) {

                log(
                    "⚠ Telegram token/chat ID missing"
                )

                return@withContext
            }

            var connection:
                HttpURLConnection? =
                null

            try {

                val url =
                    URL(
                        "https://api.telegram.org/" +
                            "bot$token/sendMessage"
                    )

                connection =
                    url.openConnection()
                        as HttpURLConnection

                connection.requestMethod =
                    "POST"

                connection.doOutput =
                    true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )

                connection.connectTimeout =
                    15_000

                connection.readTimeout =
                    15_000

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
                    connection.outputStream
                ).use {
                    it.write(body)
                    it.flush()
                }

                val responseCode =
                    connection.responseCode

                if (
                    responseCode in 200..299
                ) {

                    log(
                        "✓ Telegram notification sent"
                    )

                } else {

                    log(
                        "✗ Telegram HTTP error: " +
                            responseCode
                    )
                }

            } catch (
                e: Exception
            ) {

                log(
                    "✗ Telegram error: " +
                        "${e.message}"
                )

            } finally {

                try {
                    connection?.disconnect()
                } catch (_: Exception) {
                }
            }
        }
    }

    // ============================================================
    // NOTIFICATION CHANNELS
    // ============================================================

    private fun createNotificationChannels() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        /*
         * Status channel.
         */
        val statusChannel =
            NotificationChannel(
                CHANNEL_STATUS,
                "Monitor Status",
                NotificationManager.IMPORTANCE_LOW
            )

        manager.createNotificationChannel(
            statusChannel
        )

        /*
         * Alert channel.
         *
         * New channel ID (_v2) ব্যবহার করা হয়েছে,
         * কারণ Android 8+ এ existing channel-এর
         * sound setting code দিয়ে reliably পরিবর্তন
         * করা যায় না।
         */
        val alertChannel =
            NotificationChannel(
                CHANNEL_ALERT,
                "Railway Ticket Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

        alertChannel.enableVibration(
            true
        )

        val audioAttributes =
            android.media.AudioAttributes
                .Builder()
                .setUsage(
                    android.media.AudioAttributes
                        .USAGE_NOTIFICATION
                )
                .setContentType(
                    android.media.AudioAttributes
                        .CONTENT_TYPE_SONIFICATION
                )
                .build()

        val notificationSound =
            android.media.RingtoneManager
                .getDefaultUri(
                    android.media.RingtoneManager
                        .TYPE_NOTIFICATION
                )

        alertChannel.setSound(
            notificationSound,
            audioAttributes
        )

        manager.createNotificationChannel(
            alertChannel
        )
    }

    // ============================================================
    // STATUS NOTIFICATION
    // ============================================================

    private fun buildStatusNotification(
        text: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_STATUS
        )
            .setContentTitle(
                "Railway Ticket Monitor"
            )
            .setContentText(
                text
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_search
            )
            .setOngoing(
                true
            )
            .setOnlyAlertOnce(
                true
            )
            .build()
    }

    private fun updateStatus(
        text: String
    ) {

        log(
            text
        )

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
            NOTIF_ID_STATUS,
            buildStatusNotification(
                text
            )
        )
    }

    // ============================================================
    // TICKET ALERT
    // ============================================================

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
                    "🎫 Ticket Found"
                )
                .setContentText(
                    "$from → $to | $TRAIN_CLASS"
                )
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(
                            message
                        )
                )
                .setSmallIcon(
                    android.R.drawable
                        .ic_dialog_info
                )
                .setPriority(
                    NotificationCompat
                        .PRIORITY_HIGH
                )
                .setAutoCancel(
                    true
                )
                .setCategory(
                    NotificationCompat
                        .CATEGORY_ALARM
                )
                .setDefaults(
                    NotificationCompat.DEFAULT_ALL
                )
                .build()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.notify(
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
            "[${timeFmt.format(Date())}] $message"

        android.util.Log.d(
            "TicketMonitor",
            stamped
        )

        synchronized(
            logBuffer
        ) {

            logBuffer.add(
                stamped
            )

            while (
                logBuffer.size >
                    MAX_LOG_LINES
            ) {

                logBuffer.removeAt(
                    0
                )
            }
        }

        try {

            sendBroadcast(
                Intent(
                    ACTION_LOG
                )
                    .putExtra(
                        EXTRA_LOG_MSG,
                        stamped
                    )
            )

        } catch (_: Exception) {
        }
    }
}