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

    private val handler =
        Handler(Looper.getMainLooper())

    private val executor =
        Executors.newSingleThreadExecutor()

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

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        web =
            findViewById(R.id.webView)

        log =
            findViewById(R.id.logText)

        status =
            findViewById(R.id.statusText)

        dateEdit =
            findViewById(R.id.dateEdit)

        tokenEdit =
            findViewById(R.id.tokenEdit)

        chatEdit =
            findViewById(R.id.chatEdit)

        val startButton =
            findViewById<Button>(
                R.id.startButton
            )

        val stopButton =
            findViewById<Button>(
                R.id.stopButton
            )

        val dateButton =
            findViewById<Button>(
                R.id.dateButton
            )

        val prefs =
            getSharedPreferences(
                "settings",
                Context.MODE_PRIVATE
            )

        targetDate =
            prefs.getString(
                "date",
                "07-09-2026"
            ) ?: "07-09-2026"

        dateEdit.setText(
            targetDate
        )

        tokenEdit.setText(
            prefs.getString(
                "token",
                ""
            ) ?: ""
        )

        chatEdit.setText(
            prefs.getString(
                "chat",
                ""
            ) ?: ""
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

        if (
            android.os.Build.VERSION.SDK_INT >= 33
        ) {

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

        val settings =
            web.settings

        settings.javaScriptEnabled =
            true

        settings.domStorageEnabled =
            true

        settings.databaseEnabled =
            true

        settings.loadsImagesAutomatically =
            true

        settings.javaScriptCanOpenWindowsAutomatically =
            true

        settings.cacheMode =
            WebSettings.LOAD_DEFAULT

        settings.userAgentString =
            settings.userAgentString +
                    " RailwayMonitorApp/1.0"

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    super.onPageFinished(
                        view,
                        url
                    )

                    append(
                        "PAGE LOADED: ${url ?: ""}"
                    )

                    if (running) {

                        status.post {

                            status.text =
                                "Page loaded"
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

        val cal =
            Calendar.getInstance()

        try {

            val sdf =
                SimpleDateFormat(
                    "dd-MM-yyyy",
                    Locale.US
                )

            val parsed =
                sdf.parse(
                    dateEdit.text
                        .toString()
                )

            if (parsed != null) {
                cal.time = parsed
            }

        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->

                targetDate =
                    String.format(
                        Locale.US,
                        "%02d-%02d-%04d",
                        day,
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
                Regex(
                    "\\d{2}-\\d{2}-\\d{4}"
                )
            )
        ) {

            Toast.makeText(
                this,
                "Date format must be DD-MM-YYYY",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        targetDate =
            date

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

        append(
            "Date  : $targetDate"
        )

        append(
            "Routes: ${routes.size}"
        )

        append(
            "Cycle : $cycle"
        )

        append(
            "======================================"
        )

        status.text =
            "Monitoring started"

        /*
         * ALWAYS প্রথম route Home page থেকে।
         */
        startRouteFromHome(0)
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        append("")

        append(
            "MONITORING STOPPED"
        )

        status.text =
            "Stopped"
    }

    /*
     * =========================================================
     * HOME → ROUTE
     * =========================================================
     */

    private fun startRouteFromHome(
        index: Int
    ) {

        if (!running) return

        if (
            index !in routes.indices
        ) return

        routeIndex =
            index

        val from =
            routes[index].first

        val to =
            routes[index].second

        append("")

        append(
            "######################################"
        )

        append(
            "MONITORING ROUTE ${index + 1}/${routes.size}"
        )

        append(
            "FROM : $from"
        )

        append(
            "TO   : $to"
        )

        append(
            "DATE : $targetDate"
        )

        append(
            "######################################"
        )

        status.text =
            "$from → $to"

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )

        waitForHome(
            index,
            0
        )
    }

    private fun waitForHome(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 100) {

            append(
                "Home page timeout. Retrying..."
            )

            handler.postDelayed({

                if (running) {
                    startRouteFromHome(
                        index
                    )
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

                    fillHomeForm(
                        index
                    )
                }

            }, 500)

        } else {

            handler.postDelayed({

                waitForHome(
                    index,
                    attempt + 1
                )

            }, 200)
        }
    }

    /*
     * =========================================================
     * HOME FORM
     * =========================================================
     */

    private fun fillHomeForm(
        index: Int
    ) {

        if (!running) return

        val from =
            routes[index].first

        val to =
            routes[index].second

        val iso =
            toIso(targetDate)

        val js = """
            (function() {

                const FROM =
                    ${JSONObject.quote(from)};

                const TO =
                    ${JSONObject.quote(to)};

                const TARGET =
                    ${JSONObject.quote(iso)};

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

                function setNative(
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

                        el.value =
                            value;
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

                function typeInput(
                    name,
                    value
                ) {

                    const input =
                        findInput(name);

                    if (!input) {
                        return false;
                    }

                    input.focus();

                    setNative(
                        input,
                        ''
                    );

                    fire(
                        input,
                        'input'
                    );

                    setNative(
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

                typeInput(
                    'fromcity',
                    FROM
                );

                setTimeout(
                    function() {

                        typeInput(
                            'tocity',
                            TO
                        );

                    },
                    700
                );

                return 'OK';

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {

                    selectCities(
                        index,
                        0
                    )
                }

            }, 1300)
        }
    }

    /*
     * =========================================================
     * AUTOCOMPLETE
     * =========================================================
     */

    private fun selectCities(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 40) {

            append(
                "Station selection timeout."
            )

            handler.postDelayed({

                if (running) {
                    startRouteFromHome(
                        index
                    )
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

                function clickOption(
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
                        '[class*="suggestion"]',
                        '[class*="autocomplete"] li',
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

                            if (!text)
                                continue;

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

                const fromOk =
                    clickOption(FROM);

                setTimeout(
                    function() {

                        clickOption(TO);

                    },
                    700
                );

                return JSON.stringify({
                    from: fromOk
                });

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {

                    syncDateAndSearch(
                        index,
                        0
                    )
                }

            }, 1000)
        }
    }

    /*
     * =========================================================
     * DATE SYNC
     * =========================================================
     */

    private fun syncDateAndSearch(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 80) {

            append(
                "Date synchronization timeout."
            )

            handler.postDelayed({

                if (running) {

                    startRouteFromHome(
                        index
                    )
                }

            }, 1000)

            return
        }

        val iso =
            toIso(targetDate)

        val js = """
            (function() {

                const TARGET =
                    ${JSONObject.quote(iso)};

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

                function setNative(
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

                        el.value =
                            value;
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

                const selectors = [

                    'input[formcontrolname="doj"]',
                    'input[name="doj"]',
                    'input[id="doj"]',
                    'input[id*="doj" i]',
                    'input[placeholder*="date" i]'
                ];

                let input = null;

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

                            input = el;
                            break;
                        }
                    }

                    if (input)
                        break;
                }

                if (!input) {

                    return JSON.stringify({
                        ok: false
                    });
                }

                input.focus();

                setNative(
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

                try {

                    input.dispatchEvent(
                        new KeyboardEvent(
                            'keyup',
                            {
                                key: 'Enter',
                                bubbles: true
                            }
                        )
                    );

                } catch(e) {}

                return JSON.stringify({
                    ok: true,
                    value:
                        input.value,
                    target:
                        TARGET
                });

            })();
        """.trimIndent()

        eval(js) {

            handler.postDelayed({

                if (running) {

                    clickInitialSearch(
                        index,
                        0
                    )
                }

            }, 800)
        }
    }

    /*
     * =========================================================
     * INITIAL SEARCH
     * =========================================================
     */

    private fun clickInitialSearch(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 120) {

            append(
                "Initial Search button timeout."
            )

            handler.postDelayed({

                if (running) {

                    startRouteFromHome(
                        index
                    )
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
                            clicked: true
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
                    "INITIAL SEARCH CLICKED."
                )

                waitForResult(
                    index,
                    0
                )

            } else {

                handler.postDelayed({

                    if (running) {

                        clickInitialSearch(
                            index,
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    /*
     * =========================================================
     * RESULT PAGE LOAD
     * =========================================================
     */

    private fun waitForResult(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 240) {

            append(
                "Result page timeout."
            )

            append(
                "Restarting route from Home..."
            )

            handler.postDelayed({

                if (running) {

                    startRouteFromHome(
                        index
                    )
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

            /*
             * URL এসেছে।
             * কিন্তু DOM পুরোপুরি load হওয়ার
             * জন্য আরও যাচাই হবে।
             */
            checkResultReady(
                index,
                0
            )

        } else {

            handler.postDelayed({

                waitForResult(
                    index,
                    attempt + 1
                )

            }, 250)
        }
    }

    /*
     * =========================================================
     * RESULT READY + TICKET / NO TICKET
     * =========================================================
     */

    private fun checkResultReady(
        index: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 240) {

            append(
                "Result DOM load timeout."
            )

            handler.postDelayed({

                if (running) {

                    startRouteFromHome(
                        index
                    )
                }

            }, 1000)

            return
        }

        val js = """
            (function() {

                const body =
                    (
                        document.body
                            ?.innerText ||
                        ''
                    )
                    .replace(/\s+/g, ' ')
                    .trim();

                const lower =
                    body.toLowerCase();

                const noTicket =
                    lower.includes(
                        'not finding any ticket for your desired route'
                    );

                /*
                 * Search button count.
                 * Result page-এ দুইটি Search
                 * button সাধারণত তখনই DOM-এ থাকে।
                 */
                const elements =
                    document.querySelectorAll(
                        'button, input[type="submit"], input[type="button"], [role="button"]'
                    );

                let searchCount = 0;

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

                        searchCount++;
                    }
                }

                /*
                 * Ticket indicators.
                 *
                 * No-ticket message থাকলে
                 * কখনো ticketFound true হবে না।
                 */
                let ticketFound = false;

                if (!noTicket) {

                    const ticketWords = [

                        'book now',
                        'available',
                        'seat available',
                        'seats available',
                        'select seat',
                        'booking available'
                    ];

                    for (
                        const word
                        of ticketWords
                    ) {

                        if (
                            lower.includes(word)
                        ) {

                            ticketFound = true;
                            break;
                        }
                    }
                }

                /*
                 * Railway result DOM-এ সাধারণত train
                 * result rows/cards/table rows থাকে।
                 *
                 * সম্ভাব্য selectors গুলো count করা হচ্ছে।
                 */
                let rows = 0;

                const rowSelectors = [

                    'table tbody tr',
                    'table tr',
                    '[class*="train"]',
                    '[class*="Train"]',
                    '[class*="result"]',
                    '[class*="Result"]',
                    '[class*="trip"]',
                    '[class*="Trip"]'
                ];

                const seen = [];

                for (
                    const selector
                    of rowSelectors
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
                            (
                                el.innerText ||
                                el.textContent ||
                                ''
                            )
                            .replace(
                                /\s+/g,
                                ' '
                            )
                            .trim();

                        if (
                            text.length < 10
                        ) continue;

                        if (
                            seen.indexOf(el) === -1
                        ) {

                            seen.push(el);
                            rows++;
                        }
                    }
                }

                return JSON.stringify({

                    noTicket:
                        noTicket,

                    ticketFound:
                        ticketFound,

                    searchCount:
                        searchCount,

                    rows:
                        rows,

                    bodyLength:
                        body.length

                });

            })();
        """.trimIndent()

        eval(js) { result ->

            if (result == null) {

                handler.postDelayed({

                    if (running) {

                        checkResultReady(
                            index,
                            attempt + 1
                        )
                    }

                }, 300)

                return@eval
            }

            val text =
                result.toString()

            val noTicket =
                text.contains(
                    "\"noTicket\":true"
                )

            val ticketFound =
                text.contains(
                    "\"ticketFound\":true"
                )

            val searchCount =
                Regex(
                    "\"searchCount\":(\\d+)"
                )
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0

            val rows =
                Regex(
                    "\"rows\":(\\d+)"
                )
                    .find(text)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: 0

            /*
             * -------------------------------------------------
             * NO TICKET
             * -------------------------------------------------
             */

            if (noTicket) {

                append(
                    "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE"
                )

                append(
                    "No ticket. Using FIRST Search button for next route."
                )

                waitForFirstResultSearch(
                    index,
                    0
                )

                return@eval
            }

            /*
             * -------------------------------------------------
             * TICKET FOUND
             * -------------------------------------------------
             */

            if (ticketFound) {

                append(
                    "TICKET FOUND!"
                )

                /*
                 * Available train/row count বের করার চেষ্টা।
                 */
                val count =
                    findTicketCount(
                        index,
                        rows
                    )

                sendTelegram(

                    "🚨 BANGLADESH RAILWAY TICKET FOUND 🚨\n\n" +

                            "Route: " +
                            routes[index].first +
                            " → " +
                            routes[index].second +
                            "\n" +

                            "Date: " +
                            targetDate +
                            "\n" +

                            "Class: S_CHAIR\n" +

                            "Available result/train count: " +
                            count +
                            "\n\n" +

                            "Please check immediately."
                )

                /*
                 * অত্যন্ত গুরুত্বপূর্ণ:
                 *
                 * Ticket পাওয়া গেলে result page-এর
                 * Search ব্যবহার করা যাবে না।
                 *
                 * পরের route অবশ্যই Home থেকে।
                 */
                append(
                    "Ticket found. Next route will start from HOME."
                )

                moveToNextFromHome(
                    index
                )

                return@eval
            }

            /*
             * -------------------------------------------------
             * RESULT PAGE LOADED BUT DECISION NOT CLEAR
             * -------------------------------------------------
             *
             * Search button দুইটি থাকলে DOM ready ধরে
             * আবার check করি।
             */
            if (
                searchCount >= 2
            ) {

                handler.postDelayed({

                    if (running) {

                        checkResultReady(
                            index,
                            attempt + 1
                        )
                    }

                }, 500)

            } else {

                /*
                 * Page এখনও render হচ্ছে।
                 */
                handler.postDelayed({

                    if (running) {

                        checkResultReady(
                            index,
                            attempt + 1
                        )
                    }

                }, 400)
            }
        }
    }

    /*
     * =========================================================
     * TICKET COUNT
     * =========================================================
     *
     * এখানে route text ব্যবহার করা হয় না।
     * Result page-এর সম্ভাব্য train/result row count
     * বের করা হয়।
     */

    private fun findTicketCount(
        index: Int,
        detectedRows: Int
    ): Int {

        /*
         * প্রথমে DOM থেকে আরও নির্দিষ্ট count।
         */
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

                const selectors = [

                    'table tbody tr',
                    '[class*="train-card"]',
                    '[class*="train-card-item"]',
                    '[class*="train-row"]',
                    '[class*="train-item"]',
                    '[class*="result-card"]',
                    '[class*="result-item"]'
                ];

                const unique = [];

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
                            (
                                el.innerText ||
                                el.textContent ||
                                ''
                            )
                            .replace(
                                /\s+/g,
                                ' '
                            )
                            .trim()
                            .toLowerCase();

                        if (!text)
                            continue;

                        /*
                         * Rows containing actual availability
                         * signals.
                         */
                        if (
                            text.includes(
                                'available'
                            ) ||
                            text.includes(
                                'book now'
                            ) ||
                            text.includes(
                                'select seat'
                            ) ||
                            text.includes(
                                'seat'
                            )
                        ) {

                            if (
                                unique.indexOf(
                                    el
                                ) === -1
                            ) {

                                unique.push(el);
                            }
                        }
                    }
                }

                return String(
                    unique.length
                );

            })();
        """.trimIndent()

        var finalCount =
            detectedRows

        web.evaluateJavascript(
            js
        ) { value ->

            val domCount =
                value
                    ?.replace(
                        "\"",
                        ""
                    )
                    ?.toIntOrNull()
                    ?: 0

            if (
                domCount > 0
            ) {

                finalCount =
                    domCount
            }
        }

        /*
         * evaluateJavascript asynchronous হওয়ায়
         * fallback হিসেবে detectedRows return।
         */
        return if (
            finalCount > 0
        ) {

            finalCount

        } else {

            1
        }
    }

    /*
     * =========================================================
     * NO-TICKET RESULT PAGE
     * =========================================================
     *
     * User-এর নির্দিষ্ট rule:
     *
     * "NOT FINDING..." থাকলে
     * প্রথম Search button click করে
     * পরের route।
     *
     * Route text কোনোভাবেই দেখা হবে না।
     */

    private fun waitForFirstResultSearch(
        currentIndex: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 240) {

            append(
                "First result Search timeout."
            )

            append(
                "Restarting next route from HOME."
            )

            moveToNextFromHome(
                currentIndex
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

                            searches.push(
                                el
                            );
                        }
                    }
                }

                /*
                 * প্রথম Search only.
                 */
                if (
                    searches.length >= 1
                ) {

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
                }

                return JSON.stringify({
                    clicked: false,
                    count: 0
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
                    "FIRST RESULT SEARCH CLICKED."
                )

                /*
                 * Search click করার পর
                 * Railway next route-এর result load করবে।
                 */
                waitForNextResult(
                    currentIndex,
                    0
                )

            } else {

                handler.postDelayed({

                    if (running) {

                        waitForFirstResultSearch(
                            currentIndex,
                            attempt + 1
                        )
                    }

                }, 250)
            }
        }
    }

    /*
     * =========================================================
     * NEXT RESULT AFTER FIRST SEARCH
     * =========================================================
     *
     * Result page-এর first Search click হওয়ার পর
     * route change হয়ে নতুন result আসা পর্যন্ত অপেক্ষা।
     *
     * এখানেই পরের route-এর fields Railway নিজে
     * search form অনুযায়ী ব্যবহার করবে।
     */

    private fun waitForNextResult(
        currentIndex: Int,
        attempt: Int
    ) {

        if (!running) return

        if (attempt > 240) {

            append(
                "Next result timeout."
            )

            /*
             * নিরাপত্তা:
             * Home থেকে পরের route শুরু।
             */
            moveToNextFromHome(
                currentIndex
            )

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

                    /*
                     * Next route-এর result
                     * fully rendered কি না check।
                     */
                    checkResultReady(
                        currentIndex + 1,
                        0
                    )
                }

            }, 700)

        } else {

            handler.postDelayed({

                waitForNextResult(
                    currentIndex,
                    attempt + 1
                )

            }, 250)
        }
    }

    /*
     * =========================================================
     * MOVE NEXT FROM HOME
     * =========================================================
     */

    private fun moveToNextFromHome(
        currentIndex: Int
    ) {

        if (!running) return

        val next =
            currentIndex + 1

        /*
         * প্রথম ৬টি শেষ।
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

                /*
                 * Route 7 অবশ্যই Home থেকে।
                 */
                startRouteFromHome(
                    6
                )

            }, 13_000)

            return
        }

        /*
         * দ্বিতীয় ৬টি শেষ।
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

                if (!running)
                    return@postDelayed

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

                /*
                 * নতুন cycle → Home → Route 1.
                 */
                startRouteFromHome(
                    0
                )

            }, 14_000)

            return
        }

        /*
         * সাধারণ next route:
         *
         * Ticket পাওয়া গেলে অবশ্যই Home।
         */
        if (
            next in routes.indices
        ) {

            startRouteFromHome(
                next
            )
        }
    }

    /*
     * =========================================================
     * JAVASCRIPT EVALUATOR
     * =========================================================
     */

    private fun eval(
        javascript: String,
        callback: (String?) -> Unit
    ) {

        web.evaluateJavascript(
            javascript
        ) { raw ->

            if (
                raw == null ||
                raw == "null"
            ) {

                callback(null)

                return@evaluateJavascript
            }

            try {

                /*
                 * Android evaluateJavascript
                 * returned JSON string থেকে quotes খুলে।
                 */
                var value =
                    raw

                if (
                    value.length >= 2 &&
                    value.first() == '"' &&
                    value.last() == '"'
                ) {

                    value =
                        value.substring(
                            1,
                            value.length - 1
                        )
                        .replace(
                            "\\\"",
                            "\""
                        )
                        .replace(
                            "\\\\",
                            "\\"
                        )
                }

                callback(
                    value
                )

            } catch (_: Exception) {

                callback(
                    raw
                )
            }
        }
    }

    /*
     * =========================================================
     * DATE CONVERSION
     * =========================================================
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

                output.format(
                    parsed
                )

            } else {

                date
            }

        } catch (_: Exception) {

            date
        }
    }

    /*
     * =========================================================
     * LOG
     * =========================================================
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

    /*
     * =========================================================
     * TELEGRAM
     * =========================================================
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
                            token +
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

                val response =
                    connection.responseCode

                connection.disconnect()

                runOnUiThread {

                    if (
                        response in 200..299
                    ) {

                        append(
                            "Telegram notification sent."
                        )

                    } else {

                        append(
                            "Telegram error: HTTP $response"
                        )
                    }
                }

            } catch (e: Exception) {

                runOnUiThread {

                    append(
                        "Telegram error: ${
                            e.message
                                ?: "Unknown error"
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

        try {
            web.stopLoading()
        } catch (_: Exception) {
        }

        try {
            web.destroy()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
