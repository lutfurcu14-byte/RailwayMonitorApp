package com.example.railwaymonitor

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
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

        targetDate =
            prefs.getString("date", "07-09-2026")
                ?: "07-09-2026"

        dateEdit.setText(targetDate)

        tokenEdit.setText(
            prefs.getString("token", "") ?: ""
        )

        chatEdit.setText(
            prefs.getString("chat", "") ?: ""
        )

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

            if (
                checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.POST_NOTIFICATIONS
                    ),
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
            settings.userAgentString +
                    " RailwayMonitorApp/1.0"

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                append(
                    "PAGE LOADED: ${url ?: ""}"
                )

                if (running) {

                    status.post {
                        status.text = "Page loaded"
                    }

                    /*
                     * Initial home page-এর Search button
                     * page load হওয়ার পরও auto-click করার চেষ্টা।
                     */
                    if (
                        url != null &&
                        url.contains(
                            "eticket.railway.gov.bd"
                        ) &&
                        !url.contains(
                            "/booking/train/search"
                        )
                    ) {
                        handler.postDelayed({

                            if (running) {
                                clickFirstSearch(0)
                            }

                        }, 500)
                    }
                }
            }
        }

        web.webChromeClient =
            WebChromeClient()

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )
    }

    private fun pickDate() {

        val cal = Calendar.getInstance()

        try {

            val sdf =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            val d =
                sdf.parse(
                    dateEdit.text.toString()
                )

            if (d != null) {
                cal.time = d
            }

        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->

                targetDate =
                    String.format(
                        Locale.US,
                        "%02d-%02d-%04d",
                        dayOfMonth,
                        month + 1,
                        year
                    )

                dateEdit.setText(
                    targetDate
                )

                append(
                    "Target date selected: $targetDate"
                )
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startMonitor() {

        val date =
            dateEdit.text
                .toString()
                .trim()

        if (
            !date.matches(
                Regex("\\d{2}-\\d{2}-\\d{4}")
            )
        ) {

            Toast.makeText(
                this,
                "Date format must be DD-MM-YYYY",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        targetDate = date

        getSharedPreferences(
            "settings",
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                "date",
                targetDate
            )
            .putString(
                "token",
                tokenEdit.text
                    .toString()
                    .trim()
            )
            .putString(
                "chat",
                chatEdit.text
                    .toString()
                    .trim()
            )
            .apply()

        running = true
        routeIndex = 0
        cycle = 1

        append("")
        append(
            "======================================"
        )
        append(
            "BANGLADESH RAILWAY MULTI-ROUTE MONITOR"
        )
        append(
            "======================================"
        )
        append("Date  : $targetDate")
        append("Routes: ${routes.size}")
        append("Cycle : $cycle")
        append(
            "======================================"
        )

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
     * Route 0 এবং Route 6 নতুন search group শুরু করে।
     *
     * Route 1-5 এবং Route 7-11:
     * result page-এর প্রথম Search ব্যবহার করে।
     */
    private fun runRoute(index: Int) {

        if (!running) return

        if (index !in routes.indices) return

        routeIndex = index

        val from = routes[index].first
        val to = routes[index].second

        append("")
        append(
            "######################################"
        )
        append(
            "MONITORING ROUTE ${index + 1}/${routes.size}"
        )
        append("FROM : $from")
        append("TO   : $to")
        append("DATE : $targetDate")
        append(
            "######################################"
        )

        status.text = "$from → $to"

        /*
         * Route 0 এবং Route 6:
         * নতুন group — Home page থেকে শুরু।
         */
        if (index == 0 || index == 6) {

            web.loadUrl(
                "https://eticket.railway.gov.bd/"
            )

            waitForHomeAndFill(
                index,
                0
            )

        } else {

            /*
             * একই result page থেকে next route।
             */
            waitForTwoSearchButtons(
                index,
                0
            )
        }
    }

    private fun waitForHomeAndFill(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 80) {

            append(
                "Home page timeout. Retrying..."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val url =
            web.url ?: ""

        if (
            url.contains(
                "eticket.railway.gov.bd"
            ) &&
            !url.contains(
                "/booking/train/search"
            )
        ) {

            handler.postDelayed({

                if (running) {
                    fillAndSearch(index)
                }

            }, 300)

        } else {

            handler.postDelayed({

                waitForHomeAndFill(
                    index,
                    attempt + 1
                )

            }, 150)
        }
    }

    private fun fillAndSearch(index: Int) {

        if (!running) return

        val from =
            routes[index].first

        val to =
            routes[index].second

        val isoDate =
            toIso(targetDate)

        val js = """
            (function() {

                const FROM =
                    ${JSONObject.quote(from)};

                const TO =
                    ${JSONObject.quote(to)};

                const TARGET =
                    ${JSONObject.quote(isoDate)};

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function nativeValue(
                    el,
                    value
                ) {

                    try {

                        const setter =
                            Object.getOwnPropertyDescriptor(
                                HTMLInputElement.prototype,
                                'value'
                            ).set;

                        setter.call(
                            el,
                            value
                        );

                    } catch(e) {

                        el.value = value;
                    }
                }

                function fire(
                    el,
                    type
                ) {

                    try {

                        el.dispatchEvent(
                            new Event(
                                type,
                                {
                                    bubbles: true,
                                    cancelable: true
                                }
                            )
                        );

                    } catch(e) {}
                }

                function findInput(
                    name
                ) {

                    const selectors = [

                        'input[formcontrolname="' +
                            name + '"]',

                        'input[name="' +
                            name + '"]',

                        'input[id="' +
                            name + '"]',

                        'input[id*="' +
                            name + '"]',

                        'input[placeholder*="' +
                            name + '" i]'
                    ];

                    for (
                        const selector
                        of selectors
                    ) {

                        const list =
                            document.querySelectorAll(
                                selector
                            );

                        for (
                            const el
                            of list
                        ) {

                            if (
                                visible(el)
                            ) {
                                return el;
                            }
                        }
                    }

                    return null;
                }

                function typeCity(
                    name,
                    value
                ) {

                    const input =
                        findInput(name);

                    if (!input) {
                        return false;
                    }

                    input.focus();

                    nativeValue(
                        input,
                        ''
                    );

                    fire(
                        input,
                        'input'
                    );

                    fire(
                        input,
                        'change'
                    );

                    nativeValue(
                        input,
                        value
                    );

                    fire(
                        input,
                        'input'
                    );

                    fire(
                        input,
                        'change'
                    );

                    input.dispatchEvent(
                        new KeyboardEvent(
                            'keyup',
                            {
                                key: 'a',
                                bubbles: true
                            }
                        )
                    );

                    return true;
                }

                function closePopup() {

                    const elements =
                        document.querySelectorAll(
                            'button, input, [role="button"]'
                        );

                    const texts = [
                        'I Agree',
                        'Agree',
                        'OK',
                        'Accept',
                        'Close'
                    ];

                    for (
                        const el
                        of elements
                    ) {

                        if (
                            !visible(el)
                        ) continue;

                        const text =
                            (
                                el.innerText ||
                                el.textContent ||
                                el.value ||
                                ''
                            )
                            .trim()
                            .toLowerCase();

                        for (
                            const t
                            of texts
                        ) {

                            if (
                                text ===
                                t.toLowerCase()
                            ) {

                                try {
                                    el.click();
                                } catch(e) {}
                            }
                        }
                    }
                }

                closePopup();

                const fromOk =
                    typeCity(
                        'fromcity',
                        FROM
                    );

                setTimeout(
                    function() {

                        typeCity(
                            'tocity',
                            TO
                        );

                    },
                    600
                );

                return JSON.stringify({
                    from: fromOk
                });

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {

                    selectAutocompleteAndSyncDate(
                        index,
                        0
                    )
                }

            }, 1200)
        }
    }

    private fun selectAutocompleteAndSyncDate(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 30) {

            append(
                "Autocomplete timeout. Retrying route..."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val from =
            routes[index].first

        val to =
            routes[index].second

        val js = """
            (function() {

                const FROM =
                    ${JSONObject.quote(from)};

                const TO =
                    ${JSONObject.quote(to)};

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function norm(s) {

                    return (
                        s || ''
                    )
                    .replace(/\s+/g, ' ')
                    .trim()
                    .toLowerCase();
                }

                function findInput(
                    name
                ) {

                    const selectors = [

                        'input[formcontrolname="' +
                            name + '"]',

                        'input[name="' +
                            name + '"]',

                        'input[id="' +
                            name + '"]',

                        'input[id*="' +
                            name + '"]'
                    ];

                    for (
                        const selector
                        of selectors
                    ) {

                        const list =
                            document.querySelectorAll(
                                selector
                            );

                        for (
                            const el
                            of list
                        ) {

                            if (
                                visible(el)
                            ) {
                                return el;
                            }
                        }
                    }

                    return null;
                }

                function clickOptionFor(
                    value
                ) {

                    const target =
                        norm(value);

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

                    for (
                        const selector
                        of selectors
                    ) {

                        const list =
                            document.querySelectorAll(
                                selector
                            );

                        for (
                            const el
                            of list
                        ) {

                            if (
                                !visible(el)
                            ) continue;

                            const text =
                                norm(
                                    el.innerText ||
                                    el.textContent ||
                                    ''
                                );

                            if (!text) continue;

                            if (
                                text === target ||
                                text.startsWith(
                                    target + ' '
                                ) ||
                                text.startsWith(
                                    target + '-'
                                ) ||
                                text.includes(target)
                            ) {

                                candidates.push(
                                    el
                                );
                            }
                        }
                    }

                    if (
                        candidates.length === 0
                    ) {

                        return false;
                    }

                    const el =
                        candidates[0];

                    try {

                        el.scrollIntoView({
                            block: 'center'
                        });

                    } catch(e) {}

                    try {

                        el.click();

                    } catch(e) {

                        try {

                            el.dispatchEvent(
                                new MouseEvent(
                                    'mousedown',
                                    {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window
                                    }
                                )
                            );

                            el.dispatchEvent(
                                new MouseEvent(
                                    'mouseup',
                                    {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window
                                    }
                                )
                            );

                            el.dispatchEvent(
                                new MouseEvent(
                                    'click',
                                    {
                                        bubbles: true,
                                        cancelable: true,
                                        view: window
                                    }
                                )
                            );

                        } catch(e2) {}
                    }

                    return true;
                }

                const fromInput =
                    findInput(
                        'fromcity'
                    );

                const toInput =
                    findInput(
                        'tocity'
                    );

                const fromBefore =
                    fromInput
                        ? norm(fromInput.value)
                        : '';

                const toBefore =
                    toInput
                        ? norm(toInput.value)
                        : '';

                const fromClicked =
                    clickOptionFor(FROM);

                setTimeout(
                    function() {

                        clickOptionFor(TO);

                    },
                    600
                );

                return JSON.stringify({
                    fromClicked:
                        fromClicked,

                    fromValue:
                        fromBefore,

                    toValue:
                        toBefore
                });

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {

                    forceFinalDateSyncAndSearch(
                        index,
                        0
                    )
                }

            }, 1000)
        }
    }

    /**
     * Target date-টি Railway Angular form-এর
     * DOJ control-এ force sync করে।
     */
    private fun forceFinalDateSyncAndSearch(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 80) {

            append(
                "Date synchronization timeout."
            )

            append(
                "Restarting current route..."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val isoDate =
            toIso(targetDate)

        val js = """
            (function() {

                const TARGET =
                    ${JSONObject.quote(isoDate)};

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function nativeValue(
                    el,
                    value
                ) {

                    try {

                        const setter =
                            Object.getOwnPropertyDescriptor(
                                HTMLInputElement.prototype,
                                'value'
                            ).set;

                        setter.call(
                            el,
                            value
                        );

                    } catch(e) {

                        el.value = value;
                    }
                }

                function fire(
                    el,
                    type
                ) {

                    try {

                        el.dispatchEvent(
                            new Event(
                                type,
                                {
                                    bubbles: true,
                                    cancelable: true
                                }
                            )
                        );

                    } catch(e) {}
                }

                function findDateInput() {

                    const selectors = [

                        'input[formcontrolname="doj"]',
                        'input[name="doj"]',
                        'input[id="doj"]',
                        'input[id*="doj" i]',
                        'input[placeholder*="date" i]',
                        'input[placeholder*="journey" i]'
                    ];

                    for (
                        const selector
                        of selectors
                    ) {

                        const list =
                            document.querySelectorAll(
                                selector
                            );

                        for (
                            const el
                            of list
                        ) {

                            if (
                                visible(el)
                            ) {
                                return el;
                            }
                        }
                    }

                    return null;
                }

                function findAngularControl() {

                    const input =
                        findDateInput();

                    if (!input) {
                        return null;
                    }

                    try {

                        const ng =
                            window.ng;

                        if (
                            ng &&
                            ng.getOwningComponent
                        ) {

                            return {
                                input: input
                            };
                        }

                    } catch(e) {}

                    return {
                        input: input
                    };
                }

                const result =
                    findAngularControl();

                if (!result) {

                    return JSON.stringify({
                        ok: false
                    });
                }

                const input =
                    result.input;

                input.focus();

                nativeValue(
                    input,
                    TARGET
                );

                fire(
                    input,
                    'input'
                );

                fire(
                    input,
                    'change'
                );

                fire(
                    input,
                    'blur'
                );

                /*
                 * Angular event chain.
                 */
                try {

                    input.dispatchEvent(
                        new Event(
                            'keyup',
                            {
                                bubbles: true
                            }
                        )
                    );

                    input.dispatchEvent(
                        new Event(
                            'keydown',
                            {
                                bubbles: true
                            }
                        )
                    );

                } catch(e) {}

                /*
                 * Try Angular FormControl.
                 */
                try {

                    const injector =
                        window.ng &&
                        window.ng.getInjector
                            ? window.ng.getInjector(input)
                            : null;

                    if (injector) {

                        const formControl =
                            injector.get
                                ? null
                                : null;

                        void formControl;
                    }

                } catch(e) {}

                return JSON.stringify({
                    ok: true,
                    value: input.value,
                    target: TARGET
                });

            })();
        """.trimIndent()

        eval(js) { result ->

            handler.postDelayed({

                if (!running) return@postDelayed

                /*
                 * Date sync হওয়ার পর Search button
                 * খুঁজে click করা হবে।
                 */
                clickFirstSearchAfterDate(
                    index,
                    0
                )

            }, 700)
        }
    }

    /**
     * Initial/Home Search.
     *
     * Route text যাচাই করা হয় না।
     * Enabled + visible প্রথম Search button
     * পাওয়া মাত্র click করার চেষ্টা।
     */
    private fun clickFirstSearch(
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 100) {

            append(
                "Initial Search button timeout."
            )

            return
        }

        val js = """
            (function() {

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function textOf(el) {

                    return (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        el.getAttribute('aria-label') ||
                        ''
                    )
                    .trim()
                    .toLowerCase();
                }

                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                for (
                    const el
                    of elements
                ) {

                    if (!visible(el))
                        continue;

                    const text =
                        textOf(el);

                    if (
                        text === 'search' ||
                        text.includes('search')
                    ) {

                        const disabled =
                            el.disabled ||
                            el.getAttribute(
                                'aria-disabled'
                            ) === 'true';

                        if (disabled)
                            continue;

                        try {

                            el.scrollIntoView({
                                block: 'center'
                            });

                        } catch(e) {}

                        try {
                            el.click();
                        } catch(e) {}

                        return JSON.stringify({
                            clicked: true,
                            text: text
                        });
                    }
                }

                return JSON.stringify({
                    clicked: false
                });

            })();
        """.trimIndent()

        eval(js) { result ->

            if (
                result != null &&
                result.contains(
                    "\"clicked\":true"
                )
            ) {

                append(
                    "Initial Search button clicked."
                )

            } else {

                handler.postDelayed({

                    if (running) {
                        clickFirstSearch(
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    private fun clickFirstSearchAfterDate(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 100) {

            append(
                "Search button not ready. Retrying..."
            )

            handler.postDelayed({

                if (running) {
                    forceFinalDateSyncAndSearch(
                        index,
                        attempt + 1
                    )
                }

            }, 500)

            return
        }

        val js = """
            (function() {

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function textOf(el) {

                    return (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        el.getAttribute(
                            'aria-label'
                        ) ||
                        ''
                    )
                    .trim()
                    .toLowerCase();
                }

                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                const searches = [];

                for (
                    const el
                    of elements
                ) {

                    if (!visible(el))
                        continue;

                    const text =
                        textOf(el);

                    if (
                        text === 'search' ||
                        text.includes('search')
                    ) {

                        const disabled =
                            el.disabled ||
                            el.getAttribute(
                                'aria-disabled'
                            ) === 'true';

                        if (!disabled) {
                            searches.push(el);
                        }
                    }
                }

                if (
                    searches.length === 0
                ) {

                    return JSON.stringify({
                        ready: false,
                        count: 0
                    });
                }

                /*
                 * Date sync-এর পরে প্রথম Search।
                 */
                try {

                    searches[0].scrollIntoView({
                        block: 'center'
                    });

                } catch(e) {}

                try {
                    searches[0].click();
                } catch(e) {}

                return JSON.stringify({
                    ready: true,
                    count: searches.length
                });

            })();
        """.trimIndent()

        eval(js) { result ->

            if (
                result != null &&
                result.contains(
                    "\"ready\":true"
                )
            ) {

                append(
                    "Search clicked after date synchronization."
                )

                waitForResultPage(
                    index,
                    0
                )

            } else {

                handler.postDelayed({

                    if (running) {

                        clickFirstSearchAfterDate(
                            index,
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    /**
     * Result page আসা পর্যন্ত অপেক্ষা।
     *
     * কোনো fixed 5/30/60 second wait নেই।
     * Page ready হলেই next processing।
     */
    private fun waitForResultPage(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 180) {

            append(
                "Result page timeout. Restarting..."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val url =
            web.url ?: ""

        if (
            url.contains(
                "/booking/train/search"
            )
        ) {

            handler.postDelayed({

                if (running) {

                    processResultPage(
                        index,
                        0
                    )
                }

            }, 200)

        } else {

            handler.postDelayed({

                waitForResultPage(
                    index,
                    attempt + 1
                )

            }, 200)
        }
    }

    /**
     * Result page:
     *
     * 1. Ticket পাওয়া গেলে Telegram।
     * 2. No ticket হলে route text দেখা হবে না।
     * 3. দুইটি Search button পাওয়া গেলে প্রথমটি
     *    click করে next route।
     */
    private fun processResultPage(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 180) {

            append(
                "Result processing timeout."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val js = """
            (function() {

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function textOf(el) {

                    return (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        ''
                    )
                    .replace(/\s+/g, ' ')
                    .trim();
                }

                const bodyText =
                    textOf(
                        document.body
                    ).toLowerCase();

                /*
                 * Availability keywords.
                 * "No ticket" হলে false।
                 */
                const noTicket =
                    bodyText.includes(
                        'not finding any ticket'
                    ) ||
                    bodyText.includes(
                        'no ticket available'
                    ) ||
                    bodyText.includes(
                        'no seats available'
                    ) ||
                    bodyText.includes(
                        'no train found'
                    );

                const ticketSignals = [

                    'available',
                    'seat',
                    'seats',
                    'booking',
                    'select seat',
                    'book now'
                ];

                let ticketFound = false;

                if (!noTicket) {

                    for (
                        const word
                        of ticketSignals
                    ) {

                        if (
                            bodyText.includes(word)
                        ) {

                            ticketFound = true;
                            break;
                        }
                    }
                }

                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                const searches = [];

                for (
                    const el
                    of elements
                ) {

                    if (!visible(el))
                        continue;

                    const text =
                        textOf(el)
                            .toLowerCase();

                    if (
                        text === 'search' ||
                        text.includes('search')
                    ) {

                        const disabled =
                            el.disabled ||
                            el.getAttribute(
                                'aria-disabled'
                            ) === 'true';

                        if (!disabled) {
                            searches.push(el);
                        }
                    }
                }

                return JSON.stringify({

                    ticketFound:
                        ticketFound,

                    noTicket:
                        noTicket,

                    searchCount:
                        searches.length

                });

            })();
        """.trimIndent()

        eval(js) { result ->

            if (result == null) {

                handler.postDelayed({

                    if (running) {

                        processResultPage(
                            index,
                            attempt + 1
                        )
                    }

                }, 300)

                return@eval
            }

            val resultText =
                result.toString()

            val ticketFound =
                resultText.contains(
                    "\"ticketFound\":true"
                )

            val noTicket =
                resultText.contains(
                    "\"noTicket\":true"
                )

            val searchCount =
                Regex(
                    "\"searchCount\":(\\d+)"
                )
                    .find(resultText)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0

            if (ticketFound) {

                append(
                    "TICKET AVAILABILITY DETECTED!"
                )

                append(
                    "Route: ${routes[index].first} → ${routes[index].second}"
                )

                sendTelegram(
                    "🚨 BANGLADESH RAILWAY TICKET FOUND 🚨\n\n" +
                            "Route: ${routes[index].first} → ${routes[index].second}\n" +
                            "Date: $targetDate\n" +
                            "Class: S_CHAIR\n\n" +
                            "Please check immediately."
                )

            } else if (noTicket) {

                append(
                    "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"
                )

            } else {

                append(
                    "Result page loaded."
                )
            }

            /*
             * User's requested rule:
             *
             * No ticket message-এর route text ignore।
             * শুধু 2 Search button-এর জন্য অপেক্ষা।
             */
            if (searchCount >= 2) {

                clickFirstResultSearch(
                    index
                )

            } else {

                handler.postDelayed({

                    if (running) {

                        processResultPage(
                            index,
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    /**
     * Result page-এর দুইটি Search button পাওয়া গেলে
     * ALWAYS FIRST Search button click।
     *
     * Route text একদম যাচাই করা হয় না।
     */
    private fun clickFirstResultSearch(
        index: Int
    ) {

        if (!running) return

        val js = """
            (function() {

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function textOf(el) {

                    return (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        el.getAttribute(
                            'aria-label'
                        ) ||
                        ''
                    )
                    .trim()
                    .toLowerCase();
                }

                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                const searches = [];

                for (
                    const el
                    of elements
                ) {

                    if (!visible(el))
                        continue;

                    const text =
                        textOf(el);

                    if (
                        text === 'search' ||
                        text.includes('search')
                    ) {

                        const disabled =
                            el.disabled ||
                            el.getAttribute(
                                'aria-disabled'
                            ) === 'true';

                        if (!disabled) {
                            searches.push(el);
                        }
                    }
                }

                /*
                 * Exactly user's rule:
                 * two Search button থাকলে প্রথমটি।
                 */
                if (
                    searches.length < 2
                ) {

                    return JSON.stringify({
                        clicked: false,
                        count:
                            searches.length
                    });
                }

                try {

                    searches[0].scrollIntoView({
                        block: 'center'
                    });

                } catch(e) {}

                try {
                    searches[0].click();
                } catch(e) {

                    try {

                        searches[0].dispatchEvent(
                            new MouseEvent(
                                'click',
                                {
                                    bubbles: true,
                                    cancelable: true,
                                    view: window
                                }
                            )
                        );

                    } catch(e2) {}
                }

                return JSON.stringify({
                    clicked: true,
                    count:
                        searches.length
                });

            })();
        """.trimIndent()

        eval(js) { result ->

            if (
                result != null &&
                result.contains(
                    "\"clicked\":true"
                )
            ) {

                append(
                    "FIRST Search button clicked."
                )

                nextRoute(index)

            } else {

                handler.postDelayed({

                    if (running) {

                        processResultPage(
                            index,
                            0
                        )
                    }

                }, 300)
            }
        }
    }

    /**
     * পরবর্তী route।
     */
    private fun nextRoute(
        currentIndex: Int
    ) {

        if (!running) return

        val next =
            currentIndex + 1

        /*
         * Group 1 শেষ।
         */
        if (currentIndex == 5) {

            append("")
            append(
                "FIRST 6 ROUTES COMPLETED."
            )
            append(
                "WAITING 13 SECONDS..."
            )

            handler.postDelayed({

                if (!running) return@postDelayed

                append(
                    "13 SECOND BREAK COMPLETED."
                )

                runRoute(6)

            }, 13_000)

            return
        }

        /*
         * Group 2 শেষ।
         */
        if (currentIndex == 11) {

            append("")
            append(
                "ALL 12 ROUTES COMPLETED."
            )

            append(
                "WAITING 14 SECONDS BEFORE NEXT CYCLE..."
            )

            handler.postDelayed({

                if (!running) return@postDelayed

                cycle++

                append("")
                append(
                    "======================================"
                )
                append(
                    "STARTING CYCLE $cycle"
                )
                append(
                    "======================================"
                )

                runRoute(0)

            }, 14_000)

            return
        }

        if (next in routes.indices) {

            runRoute(next)
        }
    }

    /**
     * Result page-এ অন্তত দুইটি Search button
     * উপস্থিত/visible/enabled হওয়া পর্যন্ত অপেক্ষা।
     */
    private fun waitForTwoSearchButtons(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 180) {

            append(
                "Two Search buttons timeout."
            )

            append(
                "Restarting current route..."
            )

            handler.postDelayed({

                if (running) {
                    runRoute(index)
                }

            }, 1000)

            return
        }

        val url =
            web.url ?: ""

        if (
            !url.contains(
                "/booking/train/search"
            )
        ) {

            handler.postDelayed({

                waitForTwoSearchButtons(
                    index,
                    attempt + 1
                )

            }, 200)

            return
        }

        val js = """
            (function() {

                function visible(el) {

                    if (!el) return false;

                    const r =
                        el.getBoundingClientRect();

                    const s =
                        getComputedStyle(el);

                    return (
                        r.width > 0 &&
                        r.height > 0 &&
                        s.display !== 'none' &&
                        s.visibility !== 'hidden'
                    );
                }

                function textOf(el) {

                    return (
                        el.innerText ||
                        el.textContent ||
                        el.value ||
                        el.getAttribute(
                            'aria-label'
                        ) ||
                        ''
                    )
                    .trim()
                    .toLowerCase();
                }

                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                let count = 0;

                for (
                    const el
                    of elements
                ) {

                    if (!visible(el))
                        continue;

                    const text =
                        textOf(el);

                    if (
                        text === 'search' ||
                        text.includes('search')
                    ) {

                        const disabled =
                            el.disabled ||
                            el.getAttribute(
                                'aria-disabled'
                            ) === 'true';

                        if (!disabled) {
                            count++;
                        }
                    }
                }

                return String(count);

            })();
        """.trimIndent()

        eval(js) { result ->

            val count =
                result
                    ?.toString()
                    ?.toIntOrNull()
                    ?: 0

            if (count >= 2) {

                append(
                    "Two Search buttons detected."
                )

                /*
                 * Before clicking next route,
                 * result page-এর ticket status check।
                 */
                processResultPage(
                    index,
                    0
                )

            } else {

                handler.postDelayed({

                    if (running) {

                        waitForTwoSearchButtons(
                            index,
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    /**
     * WebView JavaScript executor.
     */
    private fun eval(
        javascript: String,
        callback: (String?) -> Unit
    ) {

        web.evaluateJavascript(
            javascript
        ) { result ->

            callback(
                if (
                    result == null ||
                    result == "null"
                ) {
                    null
                } else {

                    try {

                        JSONObject
                            .quote(result)
                            .let {
                                result
                                    .removeSurrounding(
                                        "\""
                                    )
                                    .replace(
                                        "\\\"",
                                        "\""
                                    )
                            }

                    } catch (_: Exception) {

                        result
                    }
                }
            )
        }
    }

    /**
     * DD-MM-YYYY -> YYYY-MM-DD
     */
    private fun toIso(
        date: String
    ): String {

        return try {

            val input =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            val output =
                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                )

            val parsed =
                input.parse(date)

            if (parsed != null) {

                output.format(parsed)

            } else {

                date
            }

        } catch (_: Exception) {

            date
        }
    }

    /**
     * Log output.
     */
    private fun append(
        message: String
    ) {

        runOnUiThread {

            val old =
                log.text
                    ?.toString()
                    ?: ""

            log.text =
                if (old.isEmpty()) {

                    message

                } else {

                    old +
                            "\n" +
                            message
                }
        }
    }

    /**
     * Telegram message.
     */
    private fun sendTelegram(
        message: String
    ) {

        val token =
            tokenEdit.text
                .toString()
                .trim()

        val chatId =
            chatEdit.text
                .toString()
                .trim()

        if (
            token.isEmpty() ||
            chatId.isEmpty()
        ) {

            append(
                "Telegram not configured."
            )

            return
        }

        executor.execute {

            try {

                val urlString =
                    "https://api.telegram.org/bot" +
                            URLEncoder.encode(
                                token,
                                "UTF-8"
                            ) +
                            "/sendMessage" +
                            "?chat_id=" +
                            URLEncoder.encode(
                                chatId,
                                "UTF-8"
                            ) +
                            "&text=" +
                            URLEncoder.encode(
                                message,
                                "UTF-8"
                            )

                val url =
                    URL(urlString)

                val connection =
                    url.openConnection()
                            as HttpURLConnection

                connection.requestMethod =
                    "GET"

                connection.connectTimeout =
                    15000

                connection.readTimeout =
                    15000

                val responseCode =
                    connection.responseCode

                connection.disconnect()

                runOnUiThread {

                    if (
                        responseCode in 200..299
                    ) {

                        append(
                            "Telegram notification sent."
                        )

                    } else {

                        append(
                            "Telegram error: HTTP $responseCode"
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    append(
                        "Telegram error: ${
                            e.message ?: "Unknown error"
                        }"
                    )
                }
            }
        }
    }

    override fun onDestroy() {

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        executor.shutdownNow()

        web.stopLoading()
        web.destroy()

        super.onDestroy()
    }
}
