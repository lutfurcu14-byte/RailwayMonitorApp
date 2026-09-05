package com.example.railwaymonitor

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var log: TextView
    private lateinit var status: TextView
    private lateinit var dateEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var chatEdit: EditText

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var running = false
    private var routeIndex = 0
    private var cycle = 1

    private var targetDate = "07-09-2026"

    private val routes = listOf(
        Pair("Sylhet", "Dhaka"),
        Pair("Maijgaon", "Dhaka"),
        Pair("Kulaura", "Dhaka"),
        Pair("Shamshernagar", "Dhaka"),
        Pair("Sreemangal", "Dhaka"),
        Pair("Shaistaganj", "Dhaka"),

        Pair("Sylhet", "Biman_Bandar"),
        Pair("Maijgaon", "Biman_Bandar"),
        Pair("Kulaura", "Biman_Bandar"),
        Pair("Shamshernagar", "Biman_Bandar"),
        Pair("Sreemangal", "Biman_Bandar"),
        Pair("Shaistaganj", "Biman_Bandar")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
        log = findViewById(R.id.logText)
        status = findViewById(R.id.statusText)
        dateEdit = findViewById(R.id.dateEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        chatEdit = findViewById(R.id.chatEdit)

        val startButton = findViewById<Button>(R.id.startButton)
        val stopButton = findViewById<Button>(R.id.stopButton)
        val dateButton = findViewById<Button>(R.id.dateButton)

        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)

        targetDate = prefs.getString("date", "07-09-2026") ?: "07-09-2026"

        dateEdit.setText(targetDate)
        tokenEdit.setText(prefs.getString("token", "") ?: "")
        chatEdit.setText(prefs.getString("chat", "") ?: "")

        setupWebView()

        dateButton.setOnClickListener {
            pickDate()
        }

        startButton.setOnClickListener {
            startMonitor()
        }

        stopButton.setOnClickListener {
            stopMonitor()
        }

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun setupWebView() {

        val settings = web.settings

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        settings.userAgentString =
            settings.userAgentString + " RailwayMonitorApp/1.0"

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                append("PAGE LOADED: ${url ?: ""}")

                if (running) {
                    status.post {
                        status.text = "Page loaded"
                    }
                }
            }
        }

        web.webChromeClient = WebChromeClient()

        web.loadUrl("https://eticket.railway.gov.bd/")
    }

    private fun pickDate() {

        val cal = Calendar.getInstance()

        try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.US)
            val d = sdf.parse(dateEdit.text.toString())

            if (d != null) {
                cal.time = d
            }
        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->

                targetDate = String.format(
                    Locale.US,
                    "%02d-%02d-%04d",
                    dayOfMonth,
                    month + 1,
                    year
                )

                dateEdit.setText(targetDate)

                append("Target date selected: $targetDate")

            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startMonitor() {

        val date = dateEdit.text.toString().trim()

        if (!date.matches(Regex("\\d{2}-\\d{2}-\\d{4}"))) {
            Toast.makeText(
                this,
                "Date format must be DD-MM-YYYY",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        targetDate = date

        getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("date", targetDate)
            .putString("token", tokenEdit.text.toString().trim())
            .putString("chat", chatEdit.text.toString().trim())
            .apply()

        running = true
        routeIndex = 0
        cycle = 1

        append("")
        append("======================================")
        append("BANGLADESH RAILWAY MULTI-ROUTE MONITOR")
        append("======================================")
        append("Date  : $targetDate")
        append("Routes: ${routes.size}")
        append("Cycle : $cycle")
        append("======================================")

        status.text = "Monitoring started"

        runRoute(0)
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(null)

        append("")
        append("MONITORING STOPPED")

        status.text = "Stopped"
    }

    /**
     * Route 0 এবং Route 6 থেকে নতুন group শুরু হয়।
     * অন্য route-গুলো result page থেকেই next Search ব্যবহার করবে।
     */
    private fun runRoute(index: Int) {

        if (!running) return

        if (index !in routes.indices) return

        routeIndex = index

        val from = routes[index].first
        val to = routes[index].second

        append("")
        append("######################################")
        append("MONITORING ROUTE ${index + 1}/${routes.size}")
        append("FROM : $from")
        append("TO   : $to")
        append("DATE : $targetDate")
        append("######################################")

        status.text = "$from → $to"

        web.loadUrl("https://eticket.railway.gov.bd/")

        waitForHomeAndFill(index, 0)
    }

    private fun waitForHomeAndFill(index: Int, attempt: Int) {

        if (!running) return

        if (attempt > 50) {
            append("Home page timeout. Retrying route...")
            handler.postDelayed({
                if (running) runRoute(index)
            }, 1000)
            return
        }

        val url = web.url ?: ""

        if (
            url.contains("eticket.railway.gov.bd") &&
            !url.contains("/booking/train/search")
        ) {
            handler.postDelayed({
                fillAndSearch(index)
            }, 300)

        } else {

            handler.postDelayed({
                waitForHomeAndFill(index, attempt + 1)
            }, 100)
        }
    }

    private fun fillAndSearch(index: Int) {

        if (!running) return

        val from = routes[index].first
        val to = routes[index].second
        val isoDate = toIso(targetDate)

        val js = """
            (function() {

                const FROM = ${JSONObject.quote(from)};
                const TO = ${JSONObject.quote(to)};
                const TARGET = ${JSONObject.quote(isoDate)};

                function visible(el) {
                    if (!el) return false;

                    const r = el.getBoundingClientRect();
                    const s = getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function nativeValue(el, value) {
                    try {
                        const setter =
                            Object.getOwnPropertyDescriptor(
                                HTMLInputElement.prototype,
                                'value'
                            ).set;

                        setter.call(el, value);
                    } catch(e) {
                        el.value = value;
                    }
                }

                function fire(el, type) {
                    try {
                        el.dispatchEvent(
                            new Event(type, {
                                bubbles: true,
                                cancelable: true
                            })
                        );
                    } catch(e) {}
                }

                function findInput(name) {

                    const selectors = [
                        'input[formcontrolname="' + name + '"]',
                        'input[name="' + name + '"]',
                        'input[id="' + name + '"]',
                        'input[id*="' + name + '"]',
                        'input[placeholder*="' + name + '" i]'
                    ];

                    for (const selector of selectors) {

                        const list =
                            document.querySelectorAll(selector);

                        for (const el of list) {
                            if (visible(el)) return el;
                        }
                    }

                    return null;
                }

                function typeCity(name, value) {

                    const input = findInput(name);

                    if (!input) return false;

                    input.focus();

                    nativeValue(input, '');

                    fire(input, 'input');
                    fire(input, 'change');

                    nativeValue(input, value);

                    fire(input, 'input');
                    fire(input, 'change');

                    input.dispatchEvent(
                        new KeyboardEvent('keyup', {
                            key: 'a',
                            bubbles: true
                        })
                    );

                    return true;
                }

                function clickAgree() {

                    const texts = [
                        'I Agree',
                        'Agree',
                        'OK',
                        'Accept',
                        'Close'
                    ];

                    const all =
                        document.querySelectorAll(
                            'button, input, [role="button"]'
                        );

                    for (const el of all) {

                        if (!visible(el)) continue;

                        const text =
                            (
                                el.innerText ||
                                el.textContent ||
                                el.value ||
                                ''
                            ).trim();

                        for (const t of texts) {

                            if (
                                text.toLowerCase() ===
                                t.toLowerCase()
                            ) {
                                try {
                                    el.click();
                                } catch(e) {}
                            }
                        }
                    }
                }

                clickAgree();

                typeCity('fromcity', FROM);

                setTimeout(function() {
                    typeCity('tocity', TO);
                }, 500);

                return 'FIELDS_TYPED';

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {
                    selectAutocompleteAndSyncDate(index, 0)
                }

            }, 900)
        }
    }

    /**
     * Railway autocomplete অবশ্যই select করতে হবে।
     * শুধু input-এ station name লেখা যথেষ্ট নয়।
     */
    private fun selectAutocompleteAndSyncDate(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 25) {

            append("Autocomplete timeout. Retrying route...")

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val from = routes[index].first
        val to = routes[index].second

        val js = """
            (function() {

                const FROM = ${JSONObject.quote(from)};
                const TO = ${JSONObject.quote(to)};

                function visible(el) {

                    if (!el) return false;

                    const r = el.getBoundingClientRect();
                    const s = getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function norm(s) {

                    return (s || '')
                        .replace(/\s+/g, ' ')
                        .trim()
                        .toLowerCase();
                }

                function clickOptionFor(value) {

                    const target = norm(value);

                    const selectors = [
                        '[role="option"]',
                        '.ui-menu-item',
                        '.ui-menu-item-wrapper',
                        '.ng-option',
                        'mat-option',
                        '.autocomplete-option',
                        '[class*="autocomplete"] li',
                        '[class*="suggestion"]',
                        '[class*="option"]'
                    ];

                    const candidates = [];

                    for (const selector of selectors) {

                        const list =
                            document.querySelectorAll(selector);

                        for (const el of list) {

                            if (!visible(el)) continue;

                            const text =
                                norm(
                                    el.innerText ||
                                    el.textContent ||
                                    ''
                                );

                            if (!text) continue;

                            if (
                                text === target ||
                                text.startsWith(target + ' ') ||
                                text.startsWith(target + '-') ||
                                text.includes(target)
                            ) {
                                candidates.push(el);
                            }
                        }
                    }

                    if (candidates.length === 0) {

                        return false;
                    }

                    try {
                        candidates[0].scrollIntoView({
                            block: 'center'
                        });
                    } catch(e) {}

                    try {
                        candidates[0].click();
                    } catch(e) {

                        candidates[0].dispatchEvent(
                            new MouseEvent('mousedown', {
                                bubbles: true,
                                cancelable: true,
                                view: window
                            })
                        );

                        candidates[0].dispatchEvent(
                            new MouseEvent('mouseup', {
                                bubbles: true,
                                cancelable: true,
                                view: window
                            })
                        );

                        candidates[0].dispatchEvent(
                            new MouseEvent('click', {
                                bubbles: true,
                                cancelable: true,
                                view: window
                            })
                        );
                    }

                    return true;
                }

                const fromClicked =
                    clickOptionFor(FROM);

                setTimeout(function() {

                    clickOptionFor(TO);

                }, 500);

                return JSON.stringify({
                    from: fromClicked
                });

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {
                    forceFinalDateSyncAndSearch(index, 0)
                }

            }, 900)
        }
    }

    /**
     * Railway Angular form-এর DOJ control-এ target date সেট করা।
     */
    private fun forceFinalDateSyncAndSearch(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 80) {

            append("Date synchronization timeout.")
            append("Restarting current route...")

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val isoDate = toIso(targetDate)

        val js = """
            (function() {

                const TARGET = ${JSONObject.quote(isoDate)};

                function visible(el) {

                    if (!el) return false;

                    const r = el.getBoundingClientRect();
                    const s = getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function fire(el, type) {

                    try {
                        el.dispatchEvent(
                            new Event(type, {
                                bubbles:
