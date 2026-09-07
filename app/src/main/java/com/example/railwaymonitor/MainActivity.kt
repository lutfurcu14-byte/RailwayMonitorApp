package com.example.railwaymonitor

import android.annotation.SuppressLint
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
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var dateEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var chatEdit: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private val telegramExecutor = Executors.newSingleThreadExecutor()

    private val prefs by lazy {
        getSharedPreferences("railway_monitor_settings", MODE_PRIVATE)
    }

    private val homeUrl = "https://eticket.railway.gov.bd/"

    private val noTicketPhrase =
        "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE ?"

    private var monitoring = false
    private var routeIndex = 0
    private var operationId = 0L

    private data class Route(
        val from: String,
        val to: String,
        val suggestion: String
    )

    /*
     * ============================
     * 12 ROUTES
     * ============================
     */

    private val routes = listOf(

        // PHASE 1
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

        // PHASE 2
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        logText = findViewById(R.id.logText)
        statusText = findViewById(R.id.statusText)

        dateEdit = findViewById(R.id.dateEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        chatEdit = findViewById(R.id.chatEdit)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        setupWebView()
        loadSavedSettings()

        startButton.setOnClickListener {
            startMonitoring()
        }

        stopButton.setOnClickListener {
            stopMonitoring()
        }

        stopButton.isEnabled = false

        statusText.text = "Ready"

        appendLog("Railway Monitor Ready")
    }

    // ============================================================
    // WEBVIEW
    // ============================================================

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {

        webView.settings.apply {

            javaScriptEnabled = true

            domStorageEnabled = true

            databaseEnabled = true

            loadsImagesAutomatically = true

            javaScriptCanOpenWindowsAutomatically = true

            allowFileAccess = true

            allowContentAccess = true

            cacheMode = WebSettings.LOAD_DEFAULT

            userAgentString =
                userAgentString + " RailwayMonitorAndroid"
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {

                super.onPageFinished(view, url)

                appendLog(
                    "Page loaded: ${url ?: ""}"
                )

                if (!monitoring) return

                val op = operationId

                if (isHomeUrl(url)) {

                    appendLog("Home page detected.")

                    schedule(700, op) {

                        configureRoute(op)
                    }

                } else if (isResultUrl(url)) {

                    appendLog("Result page detected.")

                    schedule(700, op) {

                        inspectResult(op)
                    }
                }
            }
        }
    }

    // ============================================================
    // SETTINGS
    // ============================================================

    private fun loadSavedSettings() {

        dateEdit.setText(
            prefs.getString("date", "")
        )

        tokenEdit.setText(
            prefs.getString("token", "")
        )

        chatEdit.setText(
            prefs.getString("chat", "")
        )
    }

    private fun saveSettings() {

        prefs.edit()
            .putString(
                "date",
                dateEdit.text.toString().trim()
            )
            .putString(
                "token",
                tokenEdit.text.toString().trim()
            )
            .putString(
                "chat",
                chatEdit.text.toString().trim()
            )
            .apply()
    }

    // ============================================================
    // START
    // ============================================================

    private fun startMonitoring() {

        if (monitoring) {

            appendLog(
                "Monitor is already running."
            )

            return
        }

        val date =
            dateEdit.text.toString().trim()

        if (!isValidDate(date)) {

            statusText.text = "Invalid date"

            appendLog(
                "Invalid date. Use dd-MM-yyyy."
            )

            return
        }

        saveSettings()

        monitoring = true

        operationId++

        routeIndex = 0

        startButton.isEnabled = false

        stopButton.isEnabled = true

        appendLog("")
        appendLog("==============================")
        appendLog("MONITOR STARTED")
        appendLog(
            "Date : $date"
        )
        appendLog(
            "Class: S_CHAIR"
        )
        appendLog("==============================")

        freshHome(operationId)
    }

    // ============================================================
    // STOP
    // ============================================================

    private fun stopMonitoring() {

        monitoring = false

        operationId++

        handler.removeCallbacksAndMessages(null)

        try {
            webView.stopLoading()
        } catch (_: Exception) {
        }

        startButton.isEnabled = true

        stopButton.isEnabled = false

        statusText.text = "Stopped"

        appendLog("MONITOR STOPPED")
    }

    // ============================================================
    // FRESH HOME
    // ============================================================

    private fun freshHome(op: Long) {

        if (!monitoring || op != operationId) return

        val route = routes[routeIndex]

        appendLog("")

        appendLog(
            "ROUTE ${routeIndex + 1}/12"
        )

        appendLog(
            "${route.from} → ${route.to}"
        )

        statusText.text =
            "${route.from} → ${route.to}"

        webView.loadUrl(homeUrl)
    }

    // ============================================================
    // ROUTE CONFIGURATION
    // ============================================================

    private fun configureRoute(op: Long) {

        if (!monitoring || op != operationId) return

        val route = routes[routeIndex]

        appendLog(
            "Step 1: From = ${route.from}"
        )

        selectCity(
            "fromcity",
            route.from,
            op,
            0
        )
    }

    // ============================================================
    // SELECT CITY
    // ============================================================

    private fun selectCity(
        controlName: String,
        city: String,
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const input =
                    document.querySelector(
                        'input[formcontrolname="${jsQuote(controlName)}"]'
                    );

                if (!input) {

                    return JSON.stringify({
                        ok:false,
                        reason:"input_not_found"
                    });
                }

                input.scrollIntoView({
                    block:"center"
                });

                input.focus();

                const setter =
                    Object.getOwnPropertyDescriptor(
                        HTMLInputElement.prototype,
                        "value"
                    )?.set;

                if (setter) {

                    setter.call(
                        input,
                        ${jsQuote(city)}
                    );

                } else {

                    input.value =
                        ${jsQuote(city)};
                }

                input.dispatchEvent(
                    new Event(
                        "input",
                        {bubbles:true}
                    )
                );

                input.dispatchEvent(
                    new Event(
                        "change",
                        {bubbles:true}
                    )
                );

                input.dispatchEvent(
                    new KeyboardEvent(
                        "keyup",
                        {
                            bubbles:true,
                            key:"a"
                        }
                    )
                );

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (!jsonBoolean(result, "ok")) {

                if (attempt < 20) {

                    schedule(700, op) {

                        selectCity(
                            controlName,
                            city,
                            op,
                            attempt + 1
                        )
                    }

                } else {

                    appendLog(
                        "Cannot find $controlName."
                    )

                    retryCurrentRoute(
                        op,
                        3000
                    )
                }

                return@evaluate
            }

            waitForCityOption(
                controlName,
                city,
                op,
                0
            ) { success ->

                if (!success) {

                    appendLog(
                        "City selection failed: $city"
                    )

                    retryCurrentRoute(
                        op,
                        3000
                    )

                    return@waitForCityOption
                }

                if (controlName == "fromcity") {

                    appendLog(
                        "From selected: $city"
                    )

                    val to =
                        routes[routeIndex].to

                    appendLog(
                        "Step 2: To = $to"
                    )

                    schedule(500, op) {

                        selectCity(
                            "tocity",
                            to,
                            op,
                            0
                        )
                    }

                } else {

                    appendLog(
                        "To selected: $city"
                    )

                    appendLog(
                        "Step 3: Opening REAL Railway date picker..."
                    )

                    schedule(500, op) {

                        openRealDatePicker(
                            op,
                            0
                        )
                    }
                }
            }
        }
    }

    // ============================================================
    // WAIT FOR CITY AUTOCOMPLETE
    // ============================================================

    private fun waitForCityOption(
        controlName: String,
        city: String,
        op: Long,
        attempt: Int,
        callback: (Boolean) -> Unit
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const target =
                    ${jsQuote(city)}
                    .trim()
                    .toLowerCase();

                const elements =
                    Array.from(
                        document.querySelectorAll(
                            '[role="option"], li, .mat-option, .ng-option, .autocomplete-option'
                        )
                    );

                const visible =
                    elements.filter(el => {

                        const r =
                            el.getBoundingClientRect();

                        if (
                            r.width <= 0 ||
                            r.height <= 0
                        ) return false;

                        const text =
                            (
                                el.innerText ||
                                el.textContent ||
                                ""
                            )
                            .trim()
                            .toLowerCase();

                        return text === target;
                    });

                if (visible.length === 0) {

                    return JSON.stringify({
                        ok:false
                    });
                }

                visible[0].click();

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                schedule(400, op) {

                    callback(true)
                }

            } else if (attempt < 30) {

                schedule(400, op) {

                    waitForCityOption(
                        controlName,
                        city,
                        op,
                        attempt + 1,
                        callback
                    )
                }

            } else {

                callback(false)
            }
        }
    }

    // ============================================================
    // REAL DATE PICKER
    // ============================================================

    private fun openRealDatePicker(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const inputs =
                    Array.from(
                        document.querySelectorAll("input")
                    );

                const candidates =
                    inputs.filter(input => {

                        const r =
                            input.getBoundingClientRect();

                        if (
                            r.width <= 0 ||
                            r.height <= 0
                        ) return false;

                        const value =
                            (
                                input.value || ""
                            )
                            .trim()
                            .toLowerCase();

                        const placeholder =
                            (
                                input.getAttribute(
                                    "placeholder"
                                ) || ""
                            )
                            .trim()
                            .toLowerCase();

                        const parentText =
                            (
                                input.parentElement?.innerText ||
                                ""
                            )
                            .trim()
                            .toLowerCase();

                        return (
                            value === "pick a date" ||
                            placeholder === "pick a date" ||
                            value.includes("pick a date") ||
                            placeholder.includes("pick a date") ||
                            parentText.includes("pick a date")
                        );
                    });

                if (
                    candidates.length === 0
                ) {

                    return JSON.stringify({
                        ok:false
                    });
                }

                const input =
                    candidates[0];

                input.scrollIntoView({
                    block:"center"
                });

                input.focus();

                input.click();

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "Real date field clicked."
                )

                waitForCalendar(
                    op,
                    0
                )

            } else if (attempt < 30) {

                schedule(500, op) {

                    openRealDatePicker(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Pick a date field not found."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // WAIT FOR CALENDAR
    // ============================================================

    private fun waitForCalendar(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== "none" &&
                        s.visibility !== "hidden"
                    );
                };

                const calendars =
                    Array.from(
                        document.querySelectorAll(
                            '.ui-datepicker, .datepicker, .mat-calendar'
                        )
                    )
                    .filter(visible);

                return JSON.stringify({
                    ok: calendars.length > 0
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "REAL Railway calendar opened."
                )

                schedule(300, op) {

                    selectDateFromCalendar(
                        op,
                        0
                    )
                }

            } else if (attempt < 40) {

                schedule(400, op) {

                    waitForCalendar(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Calendar did not open."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // SELECT DATE FROM REAL CALENDAR
    // ============================================================

    private fun selectDateFromCalendar(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val target =
            parseTargetDate(
                dateEdit.text.toString().trim()
            )

        if (target == null) {

            appendLog(
                "Invalid target date."
            )

            retryCurrentRoute(
                op,
                3000
            )

            return
        }

        val year = target.year
        val month = target.month
        val day = target.day

        val script = """
            (function() {

                const targetYear = $year;
                const targetMonth = $month;
                const targetDay = $day;

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== "none" &&
                        s.visibility !== "hidden"
                    );
                };

                const calendar =
                    Array.from(
                        document.querySelectorAll(
                            ".ui-datepicker"
                        )
                    )
                    .filter(visible)
                    .pop();

                if (!calendar) {

                    return JSON.stringify({
                        ok:false,
                        reason:"calendar_missing"
                    });
                }

                const yearElement =
                    calendar.querySelector(
                        ".ui-datepicker-year"
                    );

                const monthElement =
                    calendar.querySelector(
                        ".ui-datepicker-month"
                    );

                const currentYear =
                    parseInt(
                        yearElement?.value ||
                        yearElement?.textContent ||
                        "",
                        10
                    );

                const monthText =
                    (
                        monthElement?.value ||
                        monthElement?.textContent ||
                        ""
                    )
                    .trim()
                    .toLowerCase();

                const months = [
                    "january",
                    "february",
                    "march",
                    "april",
                    "may",
                    "june",
                    "july",
                    "august",
                    "september",
                    "october",
                    "november",
                    "december"
                ];

                let currentMonth =
                    parseInt(
                        monthText,
                        10
                    );

                if (
                    isNaN(currentMonth)
                ) {

                    currentMonth =
                        months.indexOf(
                            monthText
                        ) + 1;
                }

                if (
                    !isNaN(currentYear) &&
                    currentMonth > 0
                ) {

                    const diff =
                        (
                            targetYear -
                            currentYear
                        ) * 12 +
                        (
                            targetMonth -
                            currentMonth
                        );

                    if (diff !== 0) {

                        const button =
                            calendar.querySelector(
                                diff > 0
                                    ? ".ui-datepicker-next"
                                    : ".ui-datepicker-prev"
                            );

                        if (!button || !visible(button)) {

                            return JSON.stringify({
                                ok:false,
                                reason:"navigation_missing"
                            });
                        }

                        button.click();

                        return JSON.stringify({
                            ok:false,
                            moved:true
                        });
                    }
                }

                const days =
                    Array.from(
                        calendar.querySelectorAll(
                            "td a.ui-state-default"
                        )
                    )
                    .filter(visible);

                const exact =
                    days.find(el => {

                        const td =
                            el.closest("td");

                        if (
                            td &&
                            td.classList.contains(
                                "ui-datepicker-other-month"
                            )
                        ) {
                            return false;
                        }

                        return (
                            (
                                el.innerText ||
                                el.textContent ||
                                ""
                            ).trim() ===
                            String(targetDay)
                        );
                    });

                if (!exact) {

                    return JSON.stringify({
                        ok:false,
                        reason:"day_not_found"
                    });
                }

                exact.scrollIntoView({
                    block:"center"
                });

                exact.click();

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "Date selected from REAL calendar: " +
                            dateEdit.text.toString().trim()
                )

                schedule(500, op) {

                    verifyWebsiteDate(
                        op,
                        0
                    )
                }

            } else if (
                jsonBoolean(result, "moved")
            ) {

                schedule(500, op) {

                    selectDateFromCalendar(
                        op,
                        attempt + 1
                    )
                }

            } else if (attempt < 50) {

                schedule(500, op) {

                    selectDateFromCalendar(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Requested date could not be selected."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // VERIFY DATE
    // ============================================================

    private fun verifyWebsiteDate(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val target =
            dateEdit.text.toString().trim()

        val script = """
            (function() {

                const inputs =
                    Array.from(
                        document.querySelectorAll("input")
                    );

                const found =
                    inputs.some(input => {

                        const r =
                            input.getBoundingClientRect();

                        if (
                            r.width <= 0 ||
                            r.height <= 0
                        ) return false;

                        return (
                            (
                                input.value || ""
                            ).trim() ===
                            ${jsQuote(target)}
                        );
                    });

                return JSON.stringify({
                    ok:found
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "Website date verified."
                )

                schedule(300, op) {

                    selectSeatClass(
                        op,
                        0
                    )
                }

            } else if (attempt < 15) {

                schedule(500, op) {

                    verifyWebsiteDate(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Website date verification failed."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // S_CHAIR
    // ============================================================

    private fun selectSeatClass(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    return (
                        r.width > 0 &&
                        r.height > 0
                    );
                };

                const text = el =>
                    (
                        el.innerText ||
                        el.textContent ||
                        ""
                    ).trim();

                const exact =
                    Array.from(
                        document.querySelectorAll(
                            "mat-option, [role='option'], option, li, button, .ng-option"
                        )
                    )
                    .find(el =>
                        visible(el) &&
                        text(el)
                            .toUpperCase() ===
                        "S_CHAIR"
                    );

                if (exact) {

                    exact.click();

                    return JSON.stringify({
                        ok:true
                    });
                }

                const controls =
                    Array.from(
                        document.querySelectorAll(
                            "select, input, mat-select, [role='combobox'], .mat-select"
                        )
                    )
                    .filter(visible);

                const control =
                    controls.find(el => {

                        const t = (
                            text(el) +
                            " " +
                            (
                                el.getAttribute(
                                    "placeholder"
                                ) || ""
                            ) +
                            " " +
                            (
                                el.getAttribute(
                                    "aria-label"
                                ) || ""
                            ) +
                            " " +
                            (
                                el.getAttribute(
                                    "formcontrolname"
                                ) || ""
                            )
                        ).toLowerCase();

                        return (
                            t.includes("class") ||
                            t.includes("seat") ||
                            t.includes("coach")
                        );
                    });

                if (control) {

                    control.click();

                    return JSON.stringify({
                        ok:false,
                        opened:true
                    });
                }

                return JSON.stringify({
                    ok:false
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "S_CHAIR selected."
                )

                schedule(500, op) {

                    clickMainSearch(
                        op,
                        0
                    )
                }

            } else if (attempt < 30) {

                schedule(500, op) {

                    selectSeatClass(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "S_CHAIR could not be selected."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // MAIN SEARCH
    // ============================================================

    private fun clickMainSearch(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== "none" &&
                        s.visibility !== "hidden"
                    );
                };

                const text = el =>
                    (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        ""
                    ).trim();

                const buttons =
                    Array.from(
                        document.querySelectorAll(
                            "button, input[type='button'], input[type='submit']"
                        )
                    )
                    .filter(visible);

                const search =
                    buttons.find(button => {

                        const t =
                            text(button)
                                .toLowerCase();

                        if (!t.includes("search"))
                            return false;

                        if (
                            t.includes(
                                "previous day"
                            )
                        )
                            return false;

                        if (
                            t.includes(
                                "previous"
                            )
                        )
                            return false;

                        const disabled =
                            button.disabled ||
                            button.getAttribute(
                                "aria-disabled"
                            ) === "true" ||
                            button.classList.contains(
                                "disabled"
                            );

                        return !disabled;
                    });

                if (!search) {

                    return JSON.stringify({
                        ok:false
                    });
                }

                search.scrollIntoView({
                    block:"center"
                });

                search.click();

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                appendLog(
                    "Main Search clicked successfully."
                )

                statusText.text =
                    "Searching..."

                waitForResultPage(
                    op,
                    0
                )

            } else if (attempt < 90) {

                if (attempt == 0) {

                    appendLog(
                        "Search is not enabled yet. Waiting..."
                    )
                }

                schedule(1000, op) {

                    clickMainSearch(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Search did not become enabled."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // WAIT FOR RESULT PAGE
    // ============================================================

    private fun waitForResultPage(
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const url =
                    location.href || "";

                const body =
                    (
                        document.body?.innerText ||
                        ""
                    ).toLowerCase();

                const resultUrl =
                    url.includes(
                        "/booking/train/search"
                    ) ||
                    url.includes(
                        "train/search"
                    );

                const marker =
                    body.includes(
                        ${jsQuote(
                            noTicketPhrase.lowercase()
                        )}
                    );

                const available =
                    body.includes(
                        "available tickets(counter + online)"
                    );

                return JSON.stringify({

                    resultUrl:resultUrl,

                    marker:marker,

                    available:available

                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            val obj =
                parseJson(result)

            val resultUrl =
                obj?.optBoolean(
                    "resultUrl",
                    false
                ) ?: false

            val marker =
                obj?.optBoolean(
                    "marker",
                    false
                ) ?: false

            val available =
                obj?.optBoolean(
                    "available",
                    false
                ) ?: false

            if (
                resultUrl ||
                marker ||
                available
            ) {

                appendLog(
                    "Result state confirmed."
                )

                inspectResult(op)

            } else if (attempt < 90) {

                schedule(1000, op) {

                    waitForResultPage(
                        op,
                        attempt + 1
                    )
                }

            } else {

                appendLog(
                    "Result page not confirmed."
                )

                appendLog(
                    "Route will NOT change."
                )

                retryCurrentRoute(
                    op,
                    3000
                )
            }
        }
    }

    // ============================================================
    // RESULT INSPECTION
    // ============================================================

    private fun inspectResult(op: Long) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const body =
                    (
                        document.body?.innerText ||
                        ""
                    )
                    .replace(/\s+/g, " ")
                    .trim();

                const lower =
                    body.toLowerCase();

                const marker =
                    ${jsQuote(
                        noTicketPhrase.lowercase()
                    )};

                /*
                 * FIRST:
                 * Exact no-ticket phrase.
                 */

                if (
                    lower.includes(marker)
                ) {

                    return JSON.stringify({
                        state:"NO_TICKET"
                    });
                }

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== "none" &&
                        s.visibility !== "hidden"
                    );
                };

                const normalize = value =>
                    String(value || "")
                        .replace(/\s+/g, " ")
                        .trim();

                /*
                 * EXACT LABEL ONLY
                 */

                const labels =
                    Array.from(
                        document.querySelectorAll("*")
                    )
                    .filter(visible)
                    .filter(el =>
                        normalize(
                            el.innerText ||
                            el.textContent ||
                            ""
                        ) ===
                        "Available Tickets(Counter + Online)"
                    );

                const tickets = [];

                for (
                    const label of labels
                ) {

                    let seatCard =
                        label.closest(
                            ".single-seat-class"
                        );

                    if (!seatCard) {

                        seatCard =
                            label.parentElement;
                    }

                    let trip =
                        label.closest(
                            ".single-trip-wrapper"
                        );

                    if (!trip && seatCard) {

                        trip =
                            seatCard.closest(
                                ".single-trip-wrapper"
                            );
                    }

                    let count = null;

                    const labelLines =
                        (
                            label.innerText ||
                            label.textContent ||
                            ""
                        )
                        .split(/\n+/)
                        .map(x =>
                            x.trim()
                        )
                        .filter(Boolean);

                    /*
                     * Last numeric line = ticket count
                     */

                    for (
                        let i =
                            labelLines.length - 1;
                        i >= 0;
                        i--
                    ) {

                        const n =
                            parseInt(
                                labelLines[i],
                                10
                            );

                        if (
                            !isNaN(n) &&
                            String(n) ===
                            labelLines[i]
                        ) {

                            count = n;

                            break;
                        }
                    }

                    /*
                     * Fallback:
                     * search inside ticket card.
                     */

                    if (
                        count === null &&
                        seatCard
                    ) {

                        const lines =
                            (
                                seatCard.innerText ||
                                ""
                            )
                            .split(/\n+/)
                            .map(x =>
                                x.trim()
                            )
                            .filter(Boolean);

                        for (
                            let i =
                                lines.length - 1;
                            i >= 0;
                            i--
                        ) {

                            const n =
                                parseInt(
                                    lines[i],
                                    10
                                );

                            if (
                                !isNaN(n) &&
                                String(n) ===
                                lines[i]
                            ) {

                                count = n;

                                break;
                            }
                        }
                    }

                    if (
                        count === null
                    ) continue;

                    const tripText =
                        trip
                            ? trip.innerText || ""
                            : "";

                    const cardText =
                        seatCard
                            ? seatCard.innerText || ""
                            : "";

                    const tripLines =
                        tripText
                            .split(/\n+/)
                            .map(x =>
                                x.trim()
                            )
                            .filter(Boolean);

                    const cardLines =
                        cardText
                            .split(/\n+/)
                            .map(x =>
                                x.trim()
                            )
                            .filter(Boolean);

                    const trainName =
                        tripLines.length > 0
                            ? tripLines[0]
                            : "Unknown Train";

                    let seatClass =
                        "S_CHAIR";

                    for (
                        const line of cardLines
                    ) {

                        if (
                            line
                                .toUpperCase()
                                .includes("S_CHAIR")
                        ) {

                            seatClass =
                                "S_CHAIR";

                            break;
                        }
                    }

                    tickets.push({

                        count:count,

                        trainName:trainName,

                        seatClass:seatClass
                    });
                }

                if (
                    tickets.length > 0
                ) {

                    return JSON.stringify({

                        state:"TICKETS",

                        tickets:tickets
                    });
                }

                /*
                 * Nothing confirmed yet.
                 */

                return JSON.stringify({
                    state:"WAITING"
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            val obj =
                parseJson(result)

            val state =
                obj?.optString(
                    "state",
                    "WAITING"
                ) ?: "WAITING"

            when (state) {

                "NO_TICKET" -> {

                    appendLog(
                        "Exact no-ticket phrase detected."
                    )

                    handleNoTicket(
                        op
                    )
                }

                "TICKETS" -> {

                    val tickets =
                        obj?.optJSONArray(
                            "tickets"
                        ) ?: JSONArray()

                    handleTickets(
                        tickets,
                        op
                    )
                }

                else -> {

                    appendLog(
                        "Waiting for exact ticket-card result..."
                    )

                    schedule(1000, op) {

                        inspectResult(op)
                    }
                }
            }
        }
    }

    // ============================================================
    // TICKET RESULT
    // ============================================================

    private fun handleTickets(
        tickets: JSONArray,
        op: Long
    ) {

        if (!monitoring || op != operationId) return

        var positiveFound = false

        for (
            i in 0 until tickets.length()
        ) {

            val ticket =
                tickets.optJSONObject(i)
                    ?: continue

            val count =
                ticket.optInt(
                    "count",
                    0
                )

            val trainName =
                ticket.optString(
                    "trainName",
                    "Unknown Train"
                )

            val seatClass =
                ticket.optString(
                    "seatClass",
                    "S_CHAIR"
                )

            appendLog(
                "Available Tickets(Counter + Online): $count"
            )

            appendLog(
                "Train: $trainName | Class: $seatClass"
            )

            if (count > 0) {

                positiveFound = true

                appendLog(
                    ">>> TICKET AVAILABLE <<<"
                )

                sendTelegram(
                    trainName,
                    seatClass,
                    count,
                    routes[routeIndex]
                )
            }
        }

        if (positiveFound) {

            statusText.text =
                "TICKET FOUND"

        } else {

            statusText.text =
                "No ticket"
        }

        /*
         * Positive/zero result:
         * next route after 2 seconds.
         */

        transitionAfterResult(
            op,
            2000
        )
    }

    // ============================================================
    // NO TICKET
    // ============================================================

    private fun handleNoTicket(
        op: Long
    ) {

        if (!monitoring || op != operationId) return

        /*
         * End of PHASE 1
         */

        if (routeIndex == 5) {

            appendLog(
                "PHASE 1 complete."
            )

            appendLog(
                "Waiting 13 seconds..."
            )

            statusText.text =
                "Phase 1 break: 13 sec"

            schedule(13000, op) {

                if (
                    !monitoring ||
                    op != operationId
                ) return@schedule

                routeIndex = 6

                freshHome(op)
            }

            return
        }

        /*
         * End of PHASE 2
         */

        if (routeIndex == 11) {

            appendLog(
                "PHASE 2 complete."
            )

            appendLog(
                "Waiting 14 seconds..."
            )

            statusText.text =
                "Phase 2 break: 14 sec"

            schedule(14000, op) {

                if (
                    !monitoring ||
                    op != operationId
                ) return@schedule

                routeIndex = 0

                freshHome(op)
            }

            return
        }

        /*
         * Next route is handled by
         * the exact suggested Search button.
         */

        val nextRoute =
            routes[routeIndex + 1]

        appendLog(
            "Finding exact next route Search:"
        )

        appendLog(
            nextRoute.suggestion
        )

        clickSuggestedSearch(
            nextRoute,
            op,
            0
        )
    }

    // ============================================================
    // EXACT SUGGESTED SEARCH
    // ============================================================

    private fun clickSuggestedSearch(
        nextRoute: Route,
        op: Long,
        attempt: Int
    ) {

        if (!monitoring || op != operationId) return

        val script = """
            (function() {

                const target =
                    ${jsQuote(
                        nextRoute.suggestion.lowercase()
                    )};

                const visible = el => {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== "none" &&
                        s.visibility !== "hidden"
                    );
                };

                const normalize = value =>
                    String(value || "")
                        .replace(/\s+/g, " ")
                        .trim()
                        .toLowerCase();

                const containers =
                    Array.from(
                        document.querySelectorAll(
                            "div, section, article, li, td, p"
                        )
                    )
                    .filter(visible);

                let block =
                    containers.find(el =>
                        normalize(
                            el.innerText ||
                            el.textContent ||
                            ""
                        ) === target
                    );

                if (!block) {

                    block =
                        containers.find(el => {

                            const t =
                                normalize(
                                    el.innerText ||
                                    el.textContent ||
                                    ""
                                );

                            return t.includes(target);
                        });
                }

                if (!block) {

                    return JSON.stringify({
                        ok:false,
                        reason:"block_not_found"
                    });
                }

                const buttons =
                    Array.from(
                        block.querySelectorAll(
                            "button, input[type='button'], input[type='submit'], a"
                        )
                    )
                    .filter(visible);

                const search =
                    buttons.find(button => {

                        const t =
                            normalize(
                                button.innerText ||
                                button.textContent ||
                                button.value ||
                                button.getAttribute(
                                    "aria-label"
                                ) ||
                                ""
                            );

                        if (
                            t !== "search" &&
                            !t.endsWith(
                                " search"
                            )
                        ) {

                            return false;
                        }

                        if (
                            t.includes(
                                "previous day"
                            )
                        ) {

                            return false;
                        }

                        if (
                            t.includes(
                                "previous"
                            )
                        ) {

                            return false;
                        }

                        const disabled =
                            button.disabled ||
                            button.getAttribute(
                                "aria-disabled"
                            ) === "true" ||
                            button.classList.contains(
                                "disabled"
                            );

                        return !disabled;
                    });

                if (!search) {

                    return JSON.stringify({
                        ok:false,
                        reason:"search_not_found"
                    });
                }

                search.scrollIntoView({
                    block:"center"
                });

                search.click();

                return JSON.stringify({
                    ok:true
                });

            })()
        """.trimIndent()

        evaluate(script) { result ->

            if (!monitoring || op != operationId) return@evaluate

            if (jsonBoolean(result, "ok")) {

                routeIndex++

                appendLog(
                    "Exact suggested Search clicked."
                )

                appendLog(
                    "Next: " +
                            routes[routeIndex].from +
                            " → " +
                            routes[routeIndex].to
                )

                statusText.text =
                    "${routes[routeIndex].from} → " +
                            routes[routeIndex].to

                /*
                 * Required 2-second transition.
                 */

                schedule(2000, op) {

                    waitForResultPage(
                        op,
                        0
                    )
                }

            } else if (attempt < 30) {

                schedule(700, op) {

                    clickSuggestedSearch(
                        nextRoute,
                        op,
                        attempt + 1
                    )
                }

            } else {

                /*
                 * The required Search button was not
                 * confirmed.
                 *
                 * Do not silently assume that it was clicked.
                 *
                 * We move to the intended next route only
                 * after this controlled recovery.
                 */

                appendLog(
                    "Exact suggested Search not found."
                )

                appendLog(
                    "Retrying intended next route from Home."
                )

                routeIndex++

                schedule(3000, op) {

                    freshHome(op)
                }
            }
        }
    }

    // ============================================================
    // TRANSITION AFTER TICKET RESULT
    // ============================================================

    private fun transitionAfterResult(
        op: Long,
        delay: Long
    ) {

        if (!monitoring || op != operationId) return

        /*
         * PHASE 1 end
         */

        if (routeIndex == 5) {

            appendLog(
                "PHASE 1 complete."
            )

            appendLog(
                "Waiting 13 seconds before PHASE 2."
            )

            statusText.text =
                "Phase 1 break: 13 sec"

            schedule(13000, op) {

                if (
                    !monitoring ||
                    op != operationId
                ) return@schedule

                routeIndex = 6

                freshHome(op)
            }

            return
        }

        /*
         * PHASE 2 end
         */

        if (routeIndex == 11) {

            appendLog(
                "PHASE 2 complete."
            )

            appendLog(
                "Waiting 14 seconds before new cycle."
            )

            statusText.text =
                "Phase 2 break: 14 sec"

            schedule(14000, op) {

                if (
                    !monitoring ||
                    op != operationId
                ) return@schedule

                routeIndex = 0

                freshHome(op)
            }

            return
        }

        routeIndex++

        appendLog(
            "Waiting 2 seconds before next route..."
        )

        schedule(delay, op) {

            freshHome(op)
        }
    }

    // ============================================================
    // SAME ROUTE RETRY
    // ============================================================

    private fun retryCurrentRoute(
        op: Long,
        delay: Long
    ) {

        if (!monitoring || op != operationId) return

        val route =
            routes[routeIndex]

        appendLog(
            "SAME ROUTE will be retried."
        )

        appendLog(
            "${route.from} → ${route.to}"
        )

        statusText.text =
            "Retrying same route..."

        schedule(delay, op) {

            freshHome(op)
        }
    }

    // ============================================================
    // TELEGRAM
    // ============================================================

    private fun sendTelegram(
        trainName: String,
        seatClass: String,
        count: Int,
        route: Route
    ) {

        val token =
            tokenEdit.text.toString().trim()

        val chatId =
            chatEdit.text.toString().trim()

        if (
            token.isEmpty() ||
            chatId.isEmpty()
        ) {

            appendLog(
                "Telegram not sent: Bot Token/Chat ID missing."
            )

            return
        }

        val message =
            "🚆 Bangladesh Railway Ticket Available\n\n" +
            "Train: $trainName\n" +
            "Route: ${route.from} → ${route.to}\n" +
            "Class: $seatClass\n" +
            "Available Tickets: $count\n" +
            "Date: ${dateEdit.text.toString().trim()}"

        telegramExecutor.execute {

            var connection:
                    HttpURLConnection? = null

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

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    15000

                connection.doOutput =
                    true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/x-www-form-urlencoded"
                )

                val data =
                    "chat_id=" +
                            URLEncoder.encode(
                                chatId,
                                "UTF-8"
                            ) +
                            "&text=" +
                            URLEncoder.encode(
                                message,
                                "UTF-8"
                            )

                connection.outputStream.use {
                    it.write(
                        data.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }

                val responseCode =
                    connection.responseCode

                handler.post {

                    if (
                        responseCode in 200..299
                    ) {

                        appendLog(
                            "Telegram notification sent."
                        )

                    } else {

                        appendLog(
                            "Telegram HTTP error: $responseCode"
                        )
                    }
                }

            } catch (e: Exception) {

                handler.post {

                    appendLog(
                        "Telegram error: " +
                                (e.message
                                    ?: "unknown error")
                    )
                }

            } finally {

                connection?.disconnect()
            }
        }
    }

    // ============================================================
    // JAVASCRIPT
    // ============================================================

    private fun evaluate(
        script: String,
        callback: (String) -> Unit
    ) {

        if (!monitoring) return

        webView.evaluateJavascript(
            script
        ) { result ->

            callback(
                result ?: ""
            )
        }
    }

    // ============================================================
    // SCHEDULER
    // ============================================================

    private fun schedule(
        delay: Long,
        op: Long,
        action: () -> Unit
    ) {

        handler.postDelayed({

            if (
                monitoring &&
                op == operationId
            ) {

                action()
            }

        }, delay)
    }

    // ============================================================
    // URL CHECK
    // ============================================================

    private fun isHomeUrl(
        url: String?
    ): Boolean {

        if (url == null) return false

        return (
            url == homeUrl ||
            url.startsWith(
                homeUrl + "#"
            ) ||
            url.startsWith(
                homeUrl + "?"
            )
        )
    }

    private fun isResultUrl(
        url: String?
    ): Boolean {

        if (url == null) return false

        return (
            url.contains(
                "/booking/train/search"
            ) ||
            url.contains(
                "train/search"
            )
        )
    }

    // ============================================================
    // JSON
    // ============================================================

    private fun parseJson(
        raw: String
    ): JSONObject? {

        return try {

            val value =
                org.json.JSONTokener(
                    raw
                ).nextValue()

            if (value is String) {

                JSONObject(value)

            } else if (
                value is JSONObject
            ) {

                value

            } else {

                JSONObject(raw)
            }

        } catch (_: Exception) {

            try {
                JSONObject(raw)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun jsonBoolean(
        raw: String,
        key: String
    ): Boolean {

        return (
            parseJson(raw)
                ?.optBoolean(
                    key,
                    false
                )
                ?: false
        )
    }

    // ============================================================
    // JS STRING QUOTE
    // ============================================================

    private fun jsQuote(
        value: String
    ): String {

        return JSONObject.quote(value)
    }

    // ============================================================
    // DATE
    // ============================================================

    private fun isValidDate(
        value: String
    ): Boolean {

        return try {

            val sdf =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            sdf.isLenient = false

            sdf.parse(value)

            true

        } catch (_: Exception) {

            false
        }
    }

    private data class TargetDate(
        val year: Int,
        val month: Int,
        val day: Int
    )

    private fun parseTargetDate(
        value: String
    ): TargetDate? {

        return try {

            val sdf =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            sdf.isLenient = false

            val date =
                sdf.parse(value)
                    ?: return null

            val calendar =
                Calendar.getInstance()

            calendar.time = date

            TargetDate(
                year =
                    calendar.get(
                        Calendar.YEAR
                    ),

                month =
                    calendar.get(
                        Calendar.MONTH
                    ) + 1,

                day =
                    calendar.get(
                        Calendar.DAY_OF_MONTH
                    )
            )

        } catch (_: Exception) {

            null
        }
    }

    // ============================================================
    // LOG
    // ============================================================

    private fun appendLog(
        message: String
    ) {

        runOnUiThread {

            val old =
                logText.text
                    ?.toString()
                    .orEmpty()

            val combined =
                if (old.isEmpty()) {

                    message

                } else {

                    old +
                            "\n" +
                            message
                }

            val maxChars =
                20000

            logText.text =
                if (
                    combined.length >
                    maxChars
                ) {

                    combined.takeLast(
                        maxChars
                    )

                } else {

                    combined
                }

            logText.post {

                try {

                    if (
                        logText.parent
                            is android.widget.ScrollView
                    ) {

                        (
                            logText.parent
                                    as android.widget.ScrollView
                            ).fullScroll(
                                android.view.View.FOCUS_DOWN
                            )
                    }

                } catch (_: Exception) {
                }
            }
        }
    }

    // ============================================================
    // DESTROY
    // ============================================================

    override fun onDestroy() {

        monitoring = false

        operationId++

        handler.removeCallbacksAndMessages(
            null
        )

        telegramExecutor.shutdownNow()

        try {

            webView.stopLoading()

            webView.destroy()

        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
