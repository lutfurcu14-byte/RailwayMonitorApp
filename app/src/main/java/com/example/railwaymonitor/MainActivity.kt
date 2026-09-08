package com.example.railwaymonitor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var dateEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var chatEdit: EditText
    private lateinit var dateButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val telegramExecutor =
        Executors.newSingleThreadExecutor()

    private val prefs by lazy {
        getSharedPreferences(
            "railway_monitor_settings",
            MODE_PRIVATE
        )
    }

    private var monitoring = false
    private var routeIndex = 0
    private var operationId = 0L

    private val homeUrl =
        "https://eticket.railway.gov.bd/"

    private val resultPath =
        "/booking/train/search"

    private val trainClass =
        "S_CHAIR"

    private val noTicketPhrase =
        "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE ?"

    private data class Route(
        val from: String,
        val to: String,
        val suggestion: String
    )

    /*
     * 12 ROUTES
     *
     * Route 1-6:
     *   Sylhet-side stations -> Dhaka
     *
     * Route 7-12:
     *   Sylhet-side stations -> Biman_Bandar
     */
    private val routes = listOf(

        Route(
            "Sylhet",
            "Dhaka",
            "Maijgaon - Dhaka Search"
        ),

        Route(
            "Maijgaon",
            "Dhaka",
            "Kulaura - Dhaka Search"
        ),

        Route(
            "Kulaura",
            "Dhaka",
            "Shamshernagar - Dhaka Search"
        ),

        Route(
            "Shamshernagar",
            "Dhaka",
            "Sreemangal - Dhaka Search"
        ),

        Route(
            "Sreemangal",
            "Dhaka",
            "Shaistaganj - Dhaka Search"
        ),

        Route(
            "Shaistaganj",
            "Dhaka",
            ""
        ),

        Route(
            "Sylhet",
            "Biman_Bandar",
            "Maijgaon - Biman_Bandar Search"
        ),

        Route(
            "Maijgaon",
            "Biman_Bandar",
            "Kulaura - Biman_Bandar Search"
        ),

        Route(
            "Kulaura",
            "Biman_Bandar",
            "Shamshernagar - Biman_Bandar Search"
        ),

        Route(
            "Shamshernagar",
            "Biman_Bandar",
            "Sreemangal - Biman_Bandar Search"
        ),

        Route(
            "Sreemangal",
            "Biman_Bandar",
            "Shaistaganj - Biman_Bandar Search"
        ),

        Route(
            "Shaistaganj",
            "Biman_Bandar",
            ""
        )
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        logText = findViewById(R.id.logText)
        statusText = findViewById(R.id.statusText)

        dateEdit = findViewById(R.id.dateEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        chatEdit = findViewById(R.id.chatEdit)

        dateButton = findViewById(R.id.dateButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        setupWebView()
        loadSettings()
        setupButtons()

        statusText.text = "Ready"

        startButton.isEnabled = true
        stopButton.isEnabled = false

        appendLog("Railway Monitor ready.")
    }

    private fun setupButtons() {

        dateButton.setOnClickListener {
            showNativeDatePicker()
        }

        startButton.setOnClickListener {
            startMonitoring()
        }

        stopButton.setOnClickListener {
            stopMonitoring()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        val settings = webView.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                if (!monitoring) {
                    return
                }

                val currentOperation = operationId
                val currentRoute = routes.getOrNull(routeIndex)
                    ?: return

                appendLog(
                    "Page loaded: ${url ?: "unknown"}"
                )

                if (url?.contains(resultPath) == true) {

                    appendLog(
                        "Result page loaded for " +
                            "${currentRoute.from} -> ${currentRoute.to}"
                    )

                    handler.postDelayed(
                        {
                            if (
                                monitoring &&
                                operationId == currentOperation
                            ) {
                                inspectResult(
                                    currentOperation,
                                    currentRoute
                                )
                            }
                        },
                        3500L
                    )

                } else if (
                    url?.startsWith(homeUrl) == true ||
                    url == homeUrl
                ) {

                    appendLog(
                        "Railway home page loaded."
                    )

                    handler.postDelayed(
                        {
                            if (
                                monitoring &&
                                operationId == currentOperation
                            ) {
                                configureCurrentRoute(
                                    currentOperation
                                )
                            }
                        },
                        2500L
                    )
                }
            }
        }

        webView.loadUrl(homeUrl)
    }

    private fun startMonitoring() {

        val selectedDate = dateEdit.text
            ?.toString()
            ?.trim()
            .orEmpty()

        if (!isValidDate(selectedDate)) {

            appendLog(
                "ERROR: Please select a valid date first."
            )

            statusText.text =
                "Invalid date"

            return
        }

        monitoring = true
        operationId++

        routeIndex = 0

        saveSettings()

        startButton.isEnabled = false
        stopButton.isEnabled = true

        statusText.text =
            "Monitoring..."

        appendLog("")
        appendLog("==============================")
        appendLog("MONITOR STARTED")
        appendLog(
            "Date : $selectedDate"
        )
        appendLog(
            "Class: $trainClass"
        )
        appendLog(
            "Routes: ${routes.size}"
        )
        appendLog("==============================")

        webView.loadUrl(homeUrl)
    }

    private fun stopMonitoring() {

        monitoring = false
        operationId++

        handler.removeCallbacksAndMessages(null)

        startButton.isEnabled = true
        stopButton.isEnabled = false

        statusText.text =
            "Stopped"

        appendLog("")
        appendLog("MONITOR STOPPED")
    }

    private fun configureCurrentRoute(
        currentOperation: Long
    ) {

        if (!monitoring) {
            return
        }

        if (currentOperation != operationId) {
            return
        }

        val route = routes.getOrNull(routeIndex)
            ?: return

        val displayRoute =
            "${route.from} -> ${route.to}"

        statusText.text =
            "Route ${routeIndex + 1}/${routes.size}"

        appendLog("")
        appendLog(
            "--------------------------------"
        )
        appendLog(
            "ROUTE ${routeIndex + 1}/${routes.size}"
        )
        appendLog(
            displayRoute
        )
        appendLog(
            "--------------------------------"
        )

        selectCity(
            fieldType = "from",
            city = route.from,
            operation = currentOperation
        )
    }

    private fun selectCity(
        fieldType: String,
        city: String,
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        val escapedCity =
            jsString(city)

        val script = """
            (function() {
                var wanted = "$escapedCity";

                var selectors;

                if ("$fieldType" === "from") {
                    selectors = [
                        'input[placeholder*="From"]',
                        'input[placeholder*="from"]',
                        'input[formcontrolname="from"]',
                        'input[name="from"]',
                        'input'
                    ];
                } else {
                    selectors = [
                        'input[placeholder*="To"]',
                        'input[placeholder*="to"]',
                        'input[formcontrolname="to"]',
                        'input[name="to"]',
                        'input'
                    ];
                }

                var input = null;

                for (var i = 0; i < selectors.length; i++) {
                    var nodes =
                        document.querySelectorAll(
                            selectors[i]
                        );

                    for (var j = 0; j < nodes.length; j++) {
                        var n = nodes[j];

                        if (
                            n.offsetParent !== null &&
                            !n.disabled &&
                            !n.readOnly
                        ) {
                            input = n;
                            break;
                        }
                    }

                    if (input !== null) {
                        break;
                    }
                }

                if (!input) {
                    return "INPUT_NOT_FOUND";
                }

                input.focus();

                var setter =
                    Object.getOwnPropertyDescriptor(
                        HTMLInputElement.prototype,
                        "value"
                    ).set;

                setter.call(input, wanted);

                input.dispatchEvent(
                    new Event(
                        "input",
                        {bubbles: true}
                    )
                );

                input.dispatchEvent(
                    new Event(
                        "change",
                        {bubbles: true}
                    )
                );

                return "INPUT_SET";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("INPUT_NOT_FOUND")) {

                appendLog(
                    "Could not find $fieldType station input."
                )

                retryCurrentRoute(operation)

                return@evaluateJavascript
            }

            appendLog(
                "$fieldType station entered: $city"
            )

            handler.postDelayed(
                {
                    waitForCityOption(
                        fieldType,
                        city,
                        operation,
                        0
                    )
                },
                900L
            )
        }
    }

    private fun waitForCityOption(
        fieldType: String,
        city: String,
        operation: Long,
        attempt: Int
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        if (attempt >= 15) {

            appendLog(
                "Autocomplete option not confirmed: $city"
            )

            retryCurrentRoute(operation)

            return
        }

        val wanted =
            jsString(city)

        val script = """
            (function() {
                var wanted = "$wanted";
                var elements =
                    document.querySelectorAll(
                        'li, [role="option"], mat-option, .ui-menu-item'
                    );

                for (var i = 0; i < elements.length; i++) {
                    var el = elements[i];

                    if (
                        el.offsetParent !== null &&
                        (el.innerText || "")
                            .trim()
                            .toLowerCase()
                            .indexOf(
                                wanted.toLowerCase()
                            ) !== -1
                    ) {
                        el.click();
                        return "CLICKED";
                    }
                }

                return "NOT_FOUND";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("CLICKED")) {

                appendLog(
                    "$fieldType option selected: $city"
                )

                if (fieldType == "from") {

                    handler.postDelayed(
                        {
                            selectCity(
                                "to",
                                routes[routeIndex].to,
                                operation
                            )
                        },
                        900L
                    )

                } else {

                    handler.postDelayed(
                        {
                            setRailwayDate(
                                operation
                            )
                        },
                        900L
                    )
                }

            } else {

                handler.postDelayed(
                    {
                        waitForCityOption(
                            fieldType,
                            city,
                            operation,
                            attempt + 1
                        )
                    },
                    500L
                )
            }
        }
    }

    private fun setRailwayDate(
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        val selectedDate =
            dateEdit.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val parsed =
            parseDisplayDate(selectedDate)

        if (parsed == null) {

            appendLog(
                "ERROR: Invalid monitoring date."
            )

            retryCurrentRoute(operation)

            return
        }

        val day =
            parsed.get(Calendar.DAY_OF_MONTH)

        val month =
            parsed.get(Calendar.MONTH) + 1

        val year =
            parsed.get(Calendar.YEAR)

        val script = """
            (function() {
                var day = $day;
                var month = $month;
                var year = $year;

                var inputs =
                    document.querySelectorAll(
                        'input'
                    );

                var dateInput = null;

                for (var i = 0; i < inputs.length; i++) {
                    var n = inputs[i];

                    if (
                        n.offsetParent !== null &&
                        (
                            (n.placeholder || "")
                                .toLowerCase()
                                .indexOf("date") !== -1 ||
                            (n.name || "")
                                .toLowerCase()
                                .indexOf("date") !== -1
                        )
                    ) {
                        dateInput = n;
                        break;
                    }
                }

                if (!dateInput) {
                    return "DATE_INPUT_NOT_FOUND";
                }

                dateInput.click();

                return "DATE_OPENED";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (
                result.contains(
                    "DATE_INPUT_NOT_FOUND"
                )
            ) {

                appendLog(
                    "Railway date input not found."
                )

                retryCurrentRoute(operation)

                return@evaluateJavascript
            }

            handler.postDelayed(
                {
                    selectDateFromRailwayPicker(
                        day,
                        month,
                        year,
                        operation,
                        0
                    )
                },
                700L
            )
        }
    }

    private fun selectDateFromRailwayPicker(
        day: Int,
        month: Int,
        year: Int,
        operation: Long,
        attempt: Int
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        if (attempt >= 20) {

            appendLog(
                "Railway date picker could not be controlled."
            )

            retryCurrentRoute(operation)

            return
        }

        val script = """
            (function() {

                var picker =
                    document.querySelector(
                        '.ui-datepicker'
                    );

                if (!picker ||
                    picker.offsetParent === null) {
                    return "PICKER_NOT_READY";
                }

                var monthSelect =
                    picker.querySelector(
                        'select.ui-datepicker-month'
                    );

                var yearSelect =
                    picker.querySelector(
                        'select.ui-datepicker-year'
                    );

                if (monthSelect && yearSelect) {

                    monthSelect.value =
                        String($month - 1);

                    monthSelect.dispatchEvent(
                        new Event(
                            "change",
                            {bubbles:true}
                        )
                    );

                    yearSelect.value =
                        String($year);

                    yearSelect.dispatchEvent(
                        new Event(
                            "change",
                            {bubbles:true}
                        )
                    );

                    return "SELECTED";
                }

                return "NO_SELECT";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("SELECTED")) {

                handler.postDelayed(
                    {
                        clickDateDay(
                            day,
                            operation
                        )
                    },
                    500L
                )

            } else {

                handler.postDelayed(
                    {
                        selectDateFromRailwayPicker(
                            day,
                            month,
                            year,
                            operation,
                            attempt + 1
                        )
                    },
                    400L
                )
            }
        }
    }

    private fun clickDateDay(
        day: Int,
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        val script = """
            (function() {

                var picker =
                    document.querySelector(
                        '.ui-datepicker'
                    );

                if (!picker) {
                    return "NO_PICKER";
                }

                var links =
                    picker.querySelectorAll(
                        'a.ui-state-default'
                    );

                for (var i = 0; i < links.length; i++) {

                    var text =
                        (links[i].innerText || "")
                            .trim();

                    if (
                        text === String($day) &&
                        links[i].offsetParent !== null
                    ) {
                        links[i].click();
                        return "DAY_CLICKED";
                    }
                }

                return "DAY_NOT_FOUND";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("DAY_CLICKED")) {

                appendLog(
                    "Date selected: ${dateEdit.text}"
                )

                handler.postDelayed(
                    {
                        selectSeatClass(
                            operation
                        )
                    },
                    700L
                )

            } else {

                appendLog(
                    "Could not select date day."
                )

                retryCurrentRoute(operation)
            }
        }
    }

    private fun selectSeatClass(
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        val script = """
            (function() {

                var selects =
                    document.querySelectorAll(
                        'select'
                    );

                for (var i = 0; i < selects.length; i++) {

                    var s = selects[i];

                    if (
                        s.offsetParent !== null &&
                        !s.disabled
                    ) {

                        var options =
                            s.querySelectorAll(
                                'option'
                            );

                        for (
                            var j = 0;
                            j < options.length;
                            j++
                        ) {

                            var option =
                                options[j];

                            var value =
                                (option.value || "")
                                    .toUpperCase();

                            var text =
                                (option.innerText || "")
                                    .toUpperCase();

                            if (
                                value === "S_CHAIR" ||
                                text.indexOf("S CHAIR") !== -1
                            ) {

                                s.value =
                                    option.value;

                                s.dispatchEvent(
                                    new Event(
                                        "change",
                                        {bubbles:true}
                                    )
                                );

                                return "CLASS_SELECTED";
                            }
                        }
                    }
                }

                return "CLASS_NOT_FOUND";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("CLASS_SELECTED")) {

                appendLog(
                    "Class selected: $trainClass"
                )

            } else {

                appendLog(
                    "Class selector not found; continuing."
                )
            }

            handler.postDelayed(
                {
                    waitForSearchAndClick(
                        operation,
                        0
                    )
                },
                1000L
            )
        }
    }

    private fun waitForSearchAndClick(
        operation: Long,
        attempt: Int
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        if (attempt >= 25) {

            appendLog(
                "Search button was not found."
            )

            retryCurrentRoute(operation)

            return
        }

        val script = """
            (function() {

                var buttons =
                    document.querySelectorAll(
                        'button, input[type="button"], input[type="submit"]'
                    );

                for (var i = 0; i < buttons.length; i++) {

                    var b = buttons[i];

                    if (
                        b.offsetParent !== null &&
                        !b.disabled
                    ) {

                        var text =
                            (
                                b.innerText ||
                                b.value ||
                                ""
                            )
                            .trim()
                            .toLowerCase();

                        if (
                            text === "search" ||
                            text.indexOf("search") !== -1
                        ) {
                            b.click();
                            return "SEARCH_CLICKED";
                        }
                    }
                }

                return "SEARCH_NOT_READY";
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            if (result.contains("SEARCH_CLICKED")) {

                val route =
                    routes[routeIndex]

                appendLog(
                    "SEARCH clicked: " +
                        "${route.from} -> ${route.to}"
                )

                appendLog(
                    "Waiting for result page..."
                )

                waitForResultPage(
                    operation,
                    0
                )

            } else {

                handler.postDelayed(
                    {
                        waitForSearchAndClick(
                            operation,
                            attempt + 1
                        )
                    },
                    500L
                )
            }
        }
    }

    private fun waitForResultPage(
        operation: Long,
        attempt: Int
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        if (attempt >= 40) {

            appendLog(
                "Result page timeout."
            )

            retryCurrentRoute(operation)

            return
        }

        val url =
            webView.url.orEmpty()

        if (url.contains(resultPath)) {

            appendLog(
                "Result page confirmed."
            )

            handler.postDelayed(
                {
                    inspectResult(
                        operation,
                        routes[routeIndex]
                    )
                },
                3500L
            )

            return
        }

        handler.postDelayed(
            {
                waitForResultPage(
                    operation,
                    attempt + 1
                )
            },
            1000L
        )
    }

    private fun inspectResult(
        operation: Long,
        route: Route
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        val script = """
            (function() {

                var body =
                    (
                        document.body &&
                        document.body.innerText
                    ) || "";

                var upper =
                    body.toUpperCase();

                if (
                    upper.indexOf(
                        "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"
                    ) !== -1
                ) {
                    return JSON.stringify({
                        "type":"NONE",
                        "count":0
                    });
                }

                var selectors = [
                    '.single-trip',
                    '.trip-card',
                    '.train-card',
                    '.train-list',
                    '.booking-card',
                    '[class*="train"]',
                    '[class*="Train"]'
                ];

                var count = 0;

                for (
                    var i = 0;
                    i < selectors.length;
                    i++
                ) {

                    var nodes =
                        document.querySelectorAll(
                            selectors[i]
                        );

                    if (nodes.length > count) {
                        count = nodes.length;
                    }
                }

                var ticketWords = [
                    "S_CHAIR",
                    "S CHAIR",
                    "AVAILABLE",
                    "BOOK NOW",
                    "SEAT"
                ];

                var positive = false;

                for (
                    var j = 0;
                    j < ticketWords.length;
                    j++
                ) {

                    if (
                        upper.indexOf(
                            ticketWords[j]
                        ) !== -1
                    ) {
                        positive = true;
                        break;
                    }
                }

                if (count > 0 && positive) {

                    return JSON.stringify({
                        "type":"TICKET",
                        "count":count
                    });
                }

                return JSON.stringify({
                    "type":"UNKNOWN",
                    "count":count
                });

            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { raw ->

            if (!monitoring || operation != operationId) {
                return@evaluateJavascript
            }

            val decoded =
                raw
                    .removePrefix("\"")
                    .removeSuffix("\"")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")

            try {

                val json =
                    JSONObject(decoded)

                val type =
                    json.optString("type")

                val count =
                    json.optInt("count", 0)

                when (type) {

                    "NONE" -> {

                        appendLog(
                            "No ticket: " +
                                "${route.from} -> ${route.to}"
                        )

                        handleNoTicket(
                            operation
                        )
                    }

                    "TICKET" -> {

                        appendLog(
                            "!!! TICKET FOUND !!!"
                        )

                        appendLog(
                            "Route: " +
                                "${route.from} -> ${route.to}"
                        )

                        appendLog(
                            "Detected cards: $count"
                        )

                        handleTickets(
                            operation,
                            route,
                            count
                        )
                    }

                    else -> {

                        appendLog(
                            "Result not confirmed."
                        )

                        appendLog(
                            "Retrying same route..."
                        )

                        retryCurrentRoute(
                            operation
                        )
                    }
                }

            } catch (_: Exception) {

                appendLog(
                    "Could not parse result."
                )

                retryCurrentRoute(
                    operation
                )
            }
        }
    }

    private fun handleNoTicket(
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        /*
         * Wait after the result has actually been
         * inspected before moving to the next route.
         */
        appendLog(
            "Waiting before next route..."
        )

        val delay =
            if (routeIndex == 5) {
                14000L
            } else {
                13000L
            }

        handler.postDelayed(
            {
                if (
                    monitoring &&
                    operation == operationId
                ) {
                    moveToNextRoute(
                        operation
                    )
                }
            },
            delay
        )
    }

    private fun handleTickets(
        operation: Long,
        route: Route,
        count: Int
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        statusText.text =
            "TICKET FOUND"

        val message =
            buildTelegramMessage(
                route,
                count
            )

        sendTelegram(
            message
        )

        appendLog(
            "Telegram notification queued."
        )

        /*
         * Do not immediately start another search.
         * Give the result page time to remain stable.
         */
        handler.postDelayed(
            {
                if (
                    monitoring &&
                    operation == operationId
                ) {
                    moveToNextRoute(
                        operation
                    )
                }
            },
            13000L
        )
    }

    private fun retryCurrentRoute(
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        appendLog(
            "Retrying current route after 3 seconds..."
        )

        handler.postDelayed(
            {
                if (
                    monitoring &&
                    operation == operationId
                ) {
                    webView.loadUrl(homeUrl)
                }
            },
            3000L
        )
    }

    private fun moveToNextRoute(
        operation: Long
    ) {

        if (!monitoring || operation != operationId) {
            return
        }

        routeIndex++

        if (routeIndex >= routes.size) {

            appendLog("")
            appendLog(
                "================================"
            )
            appendLog(
                "ALL 12 ROUTES COMPLETED"
            )
            appendLog(
                "Starting next monitoring cycle..."
            )
            appendLog(
                "================================"
            )

            routeIndex = 0

            handler.postDelayed(
                {
                    if (
                        monitoring &&
                        operation == operationId
                    ) {
                        webView.loadUrl(homeUrl)
                    }
                },
                14000L
            )

            return
        }

        val next =
            routes[routeIndex]

        appendLog("")
        appendLog(
            "Next route:"
        )
        appendLog(
            "${next.from} -> ${next.to}"
        )

        /*
         * Return to the home page first.
         * The next route is configured only after
         * the home page has completely loaded.
         */
        handler.postDelayed(
            {
                if (
                    monitoring &&
                    operation == operationId
                ) {
                    webView.loadUrl(homeUrl)
                }
            },
            2000L
        )
    }

    private fun showNativeDatePicker() {

        val calendar =
            Calendar.getInstance()

        val existing =
            parseDisplayDate(
                dateEdit.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )

        if (existing != null) {
            calendar.time =
                existing.time
        }

        val dialog =
            DatePickerDialog(
                this,
                { _, year, month, day ->

                    val selected =
                        Calendar.getInstance()

                    selected.set(
                        year,
                        month,
                        day
                    )

                    val formatter =
                        SimpleDateFormat(
                            "dd-MM-yyyy",
                            Locale.US
                        )

                    dateEdit.setText(
                        formatter.format(
                            selected.time
                        )
                    )

                    appendLog(
                        "Monitoring date: " +
                            dateEdit.text
                    )
                },
                calendar.get(
                    Calendar.YEAR
                ),
                calendar.get(
                    Calendar.MONTH
                ),
                calendar.get(
                    Calendar.DAY_OF_MONTH
                )
            )

        dialog.show()
    }

    private fun isValidDate(
        value: String
    ): Boolean {

        return parseDisplayDate(value) != null
    }

    private fun parseDisplayDate(
        value: String
    ): Calendar? {

        if (value.isBlank()) {
            return null
        }

        return try {

            val formatter =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            formatter.isLenient = false

            val date =
                formatter.parse(value)
                    ?: return null

            Calendar.getInstance().apply {
                time = date
            }

        } catch (_: Exception) {
            null
        }
    }

    private fun buildTelegramMessage(
        route: Route,
        count: Int
    ): String {

        val date =
            dateEdit.text
                ?.toString()
                ?.trim()
                .orEmpty()

        return buildString {

            append("🚆 BANGLADESH RAILWAY ALERT\n\n")

            append(
                "Route: ${route.from} → ${route.to}\n"
            )

            append(
                "Date: $date\n"
            )

            append(
                "Class: $trainClass\n"
            )

            append(
                "Detected: $count ticket card(s)\n\n"
            )

            append(
                "Ticket availability detected."
            )
        }
    }

    private fun sendTelegram(
        message: String
    ) {

        val token =
            tokenEdit.text
                ?.toString()
                ?.trim()
                .orEmpty()

        val chatId =
            chatEdit.text
                ?.toString()
                ?.trim()
                .orEmpty()

        if (
            token.isBlank() ||
            chatId.isBlank()
        ) {

            appendLog(
                "Telegram skipped: token/chat ID missing."
            )

            return
        }

        telegramExecutor.execute {

            var connection:
                HttpURLConnection? = null

            try {

                val encodedText =
                    URLEncoder.encode(
                        message,
                        "UTF-8"
                    )

                val urlString =
                    "https://api.telegram.org/bot" +
                        token +
                        "/sendMessage" +
                        "?chat_id=" +
                        URLEncoder.encode(
                            chatId,
                            "UTF-8"
                        ) +
                        "&text=" +
                        encodedText

                connection =
                    URL(urlString)
                        .openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    15000

                val responseCode =
                    connection.responseCode

                runOnUiThread {

                    if (responseCode in 200..299) {

                        appendLog(
                            "Telegram: message sent."
                        )

                    } else {

                        appendLog(
                            "Telegram HTTP error: " +
                                responseCode
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    appendLog(
                        "Telegram error: " +
                            (e.message ?: "unknown error")
                    )
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    private fun loadSettings() {

        dateEdit.setText(
            prefs.getString(
                "date",
                ""
            )
        )

        tokenEdit.setText(
            prefs.getString(
                "telegram_token",
                ""
            )
        )

        chatEdit.setText(
            prefs.getString(
                "telegram_chat",
                ""
            )
        )
    }

    private fun saveSettings() {

        prefs.edit()
            .putString(
                "date",
                dateEdit.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )
            .putString(
                "telegram_token",
                tokenEdit.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )
            .putString(
                "telegram_chat",
                chatEdit.text
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            )
            .apply()
    }

    private fun jsString(
        value: String
    ): String {

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    private fun appendLog(
        message: String
    ) {

        runOnUiThread {

            val old =
                logText.text
                    ?.toString()
                    .orEmpty()

            val newText =
                if (old.isEmpty()) {
                    message
                } else {
                    old + "\n" + message
                }

            val maxChars = 20000

            logText.text =
                if (newText.length > maxChars) {
                    newText.takeLast(
                        maxChars
                    )
                } else {
                    newText
                }

            /*
             * logText is a TextView.
             * The ScrollView is its parent.
             *
             * This avoids the previous compile error caused
             * by treating TextView itself as ScrollView.
             */
            logText.post {

                (
                    logText.parent
                        as? android.widget.ScrollView
                    )?.fullScroll(
                        android.view.View.FOCUS_DOWN
                    )
            }
        }
    }

    override fun onDestroy() {

        monitoring = false
        operationId++

        handler.removeCallbacksAndMessages(
            null
        )

        telegramExecutor.shutdownNow()

        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }
}