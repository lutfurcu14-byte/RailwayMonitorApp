package com.example.railwaymonitor

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.View
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
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

    private var running = false

    private var routeIndex = 0

    /*
     * Round number:
     *
     * Odd  = secondary Search #1
     * Even = secondary Search #2
     */
    private var cycle = 1

    private var targetDate = ""

    /*
     * ONLY FIRST 6 ROUTES
     */
    private val routes = listOf(

        "Sylhet" to "Dhaka",

        "Maijgaon" to "Dhaka",

        "Kulaura" to "Dhaka",

        "Shamshernagar" to "Dhaka",

        "Sreemangal" to "Dhaka",

        "Shaistaganj" to "Dhaka"
    )

    private val executor =
        Executors.newSingleThreadExecutor()

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_main
        )

        web =
            findViewById(
                R.id.webView
            )

        log =
            findViewById(
                R.id.logText
            )

        status =
            findViewById(
                R.id.statusText
            )

        dateEdit =
            findViewById(
                R.id.dateEdit
            )

        tokenEdit =
            findViewById(
                R.id.tokenEdit
            )

        chatEdit =
            findViewById(
                R.id.chatEdit
            )

        val prefs =
            getSharedPreferences(
                "settings",
                MODE_PRIVATE
            )

        dateEdit.setText(
            prefs.getString(
                "date",
                "07-09-2026"
            )
        )

        tokenEdit.setText(
            prefs.getString(
                "token",
                ""
            )
        )

        chatEdit.setText(
            prefs.getString(
                "chat",
                ""
            )
        )

        setupWebView()

        findViewById<Button>(
            R.id.dateButton
        ).setOnClickListener {

            pickDate()
        }

        findViewById<Button>(
            R.id.startButton
        ).setOnClickListener {

            startMonitor()
        }

        findViewById<Button>(
            R.id.stopButton
        ).setOnClickListener {

            stopMonitor()
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            requestPermissions(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS
                ),
                55
            )
        }
    }

    private fun setupWebView() {

        web.settings.javaScriptEnabled =
            true

        web.settings.domStorageEnabled =
            true

        web.settings.databaseEnabled =
            true

        web.settings.userAgentString =
            web.settings.userAgentString +
                    " RailwayMonitorApp/1.0"

        web.webViewClient =
            object : WebViewClient() {

                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {

                    append(
                        "Page loaded: ${url ?: ""}"
                    )
                }
            }

        web.webChromeClient =
            WebChromeClient()

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )
    }

    /*
     * App's own date picker.
     *
     * This only selects the date that the user wants
     * the Railway website calendar to select later.
     */
    private fun pickDate() {

        val c =
            Calendar.getInstance()

        val current =
            dateEdit.text
                .toString()
                .split("-")

        if (
            current.size == 3
        ) {

            try {

                c.set(
                    current[2].toInt(),
                    current[1].toInt() - 1,
                    current[0].toInt()
                )

            } catch (_: Exception) {
            }
        }

        DatePickerDialog(
            this,
            { _, y, m, d ->

                dateEdit.setText(
                    String.format(
                        Locale.US,
                        "%02d-%02d-%04d",
                        d,
                        m + 1,
                        y
                    )
                )

            },
            c.get(
                Calendar.YEAR
            ),
            c.get(
                Calendar.MONTH
            ),
            c.get(
                Calendar.DAY_OF_MONTH
            )
        ).show()
    }

    private fun startMonitor() {

        if (running)
            return

        targetDate =
            dateEdit.text
                .toString()
                .trim()

        if (
            !Regex(
                "\\d{2}-\\d{2}-\\d{4}"
            ).matches(
                targetDate
            )
        ) {

            Toast.makeText(
                this,
                "Date must be DD-MM-YYYY",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        getSharedPreferences(
            "settings",
            MODE_PRIVATE
        )
            .edit()
            .putString(
                "date",
                targetDate
            )
            .putString(
                "token",
                tokenEdit.text.toString()
            )
            .putString(
                "chat",
                chatEdit.text.toString()
            )
            .apply()

        running = true

        routeIndex = 0

        cycle = 1

        append(
            "=== MONITOR STARTED ==="
        )

        append(
            "Date: $targetDate | Class: S_CHAIR | Routes: 6"
        )

        append(
            "Round 1: Odd → secondary Search #1"
        )

        setStatus(
            "Running"
        )

        /*
         * ALWAYS START FROM ROUTE 1
         */
        runRoute(0)
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        setStatus(
            "Stopped"
        )

        append(
            "=== MONITOR STOPPED ==="
        )
    }

    /*
     * Start one route from a fresh Home page.
     */
    private fun runRoute(
        index: Int
    ) {

        if (!running)
            return

        if (
            index !in routes.indices
        )
            return

        routeIndex =
            index

        val (
            from,
            to
        ) =
            routes[index]

        append(
            "[${index + 1}/6] $from → $to"
        )

        setStatus(
            "Searching ${index + 1}/6: $from → $to"
        )

        /*
         * Fresh Home page.
         */
        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )

        /*
         * Wait for Home page.
         */
        handler.postDelayed(
            {

                if (!running)
                    return@postDelayed

                fillAndSearch(
                    from,
                    to,
                    0
                )

            },
            2200
        )
    }

    /*
     * Fill From + To + Seat Class.
     *
     * IMPORTANT:
     * The date is NOT injected into the input.
     *
     * The real "Pick a date" field is clicked.
     */
    private fun fillAndSearch(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running)
            return

        val js =
            """
            (function(){

              const from =
                ${JSONObject.quote(from)};

              const to =
                ${JSONObject.quote(to)};

              function visible(e){

                return !!(
                  e &&
                  (
                    e.offsetWidth ||
                    e.offsetHeight ||
                    e.getClientRects().length
                  )
                );
              }

              function vis(selector){

                return [
                  ...document.querySelectorAll(
                    selector
                  )
                ].find(
                  e => visible(e)
                );
              }

              function fire(e){

                [
                  'input',
                  'change',
                  'blur'
                ].forEach(type => {

                  e.dispatchEvent(
                    new Event(
                      type,
                      {
                        bubbles:true
                      }
                    )
                  );

                });
              }

              function setCity(
                name,
                ctrl
              ){

                const e =
                  vis(
                    'input[formcontrolname="' +
                    ctrl +
                    '"]'
                  );

                if(!e)
                  return false;

                e.focus();

                const setter =
                  Object.getOwnPropertyDescriptor(
                    HTMLInputElement.prototype,
                    'value'
                  ).set;

                setter.call(
                  e,
                  name
                );

                fire(e);

                return true;
              }

              function setClass(){

                const selects =
                  [
                    ...document.querySelectorAll(
                      'select'
                    )
                  ];

                selects.forEach(s => {

                  [
                    ...s.options
                  ].forEach(o => {

                    if(
                      o.value ===
                        'S_CHAIR' ||

                      (
                        o.textContent ||
                        ''
                      ).trim() ===
                        'S_CHAIR'
                    ){

                      o.selected = true;

                      fire(s);
                    }

                  });

                });

                const txt =
                  [
                    ...document.querySelectorAll(
                      '*'
                    )
                  ].find(
                    e =>
                      e.childElementCount === 0 &&
                      (
                        e.textContent ||
                        ''
                      ).trim() ===
                        'S_CHAIR' &&
                      visible(e)
                  );

                if(txt)
                  txt.click();
              }

              function findDateField(){

                const inputs =
                  [
                    ...document.querySelectorAll(
                      'input'
                    )
                  ].filter(
                    e => visible(e)
                  );

                return inputs.find(e => {

                  const placeholder =
                    (
                      e.getAttribute(
                        'placeholder'
                      ) || ''
                    )
                    .trim()
                    .toLowerCase();

                  const value =
                    (
                      e.value ||
                      ''
                    )
                    .trim()
                    .toLowerCase();

                  const aria =
                    (
                      e.getAttribute(
                        'aria-label'
                      ) || ''
                    )
                    .trim()
                    .toLowerCase();

                  return (
                    placeholder ===
                      'pick a date' ||

                    value ===
                      'pick a date' ||

                    aria ===
                      'pick a date'
                  );

                }) ||

                vis(
                  'input[formcontrolname="doj"]'
                ) ||

                vis(
                  'input#doj'
                );
              }

              /*
               * Close I AGREE if present.
               */
              [
                ...document.querySelectorAll(
                  'button,[role="button"]'
                )
              ].forEach(e => {

                const text =
                  (
                    e.innerText ||
                    e.textContent ||
                    ''
                  ).trim();

                if(
                  text ===
                    'I AGREE'
                ){

                  e.click();
                }

              });

              const fromOK =
                setCity(
                  from,
                  'fromcity'
                );

              const toOK =
                setCity(
                  to,
                  'tocity'
                );

              setClass();

              if(
                !fromOK ||
                !toOK
              ){

                return 'WAIT_CITY';
              }

              const dateField =
                findDateField();

              if(!dateField){

                return 'WAIT_DATE_FIELD';
              }

              /*
               * VERY IMPORTANT:
               *
               * Do NOT assign a date value here.
               *
               * Only click the Railway date field.
               */
              dateField.focus();

              dateField.click();

              return 'DATE_FIELD_CLICKED';

            })()
            """.trimIndent()

        eval(js) { result ->

            val clean =
                result
                    .trim('"')
                    .replace(
                        "\\\"",
                        "\""
                    )

            append(
                "Form step: $clean"
            )

            /*
             * NEVER change route here.
             *
             * Continue working on SAME route.
             */
            handler.postDelayed(
                {

                    if (!running)
                        return@postDelayed

                    selectDateFromCalendar(
                        from,
                        to,
                        0
                    )

                },
                700
            )
        }
    }

    /*
     * Select date from the ACTUAL Railway website calendar.
     *
     * No direct value assignment.
     */
    private fun selectDateFromCalendar(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running)
            return

        val js =
            """
            (function(){

              const target =
                ${JSONObject.quote(targetDate)};

              function visible(e){

                return !!(
                  e &&
                  (
                    e.offsetWidth ||
                    e.offsetHeight ||
                    e.getClientRects().length
                  )
                );
              }

              const calendars =
                [
                  ...document.querySelectorAll(
                    '.ui-datepicker,' +
                    '.mat-datepicker-content,' +
                    '[role="dialog"],' +
                    '[role="grid"]'
                  )
                ].filter(
                  e => visible(e)
                );

              if(
                calendars.length === 0
              ){

                return 'CALENDAR_NOT_OPEN';
              }

              const p =
                target.split('-');

              const wantedDay =
                parseInt(
                  p[0],
                  10
                );

              const wantedMonth =
                parseInt(
                  p[1],
                  10
                ) - 1;

              const wantedYear =
                parseInt(
                  p[2],
                  10
                );

              /*
               * Railway currently uses a
               * jQuery-UI style calendar.
               */
              const ui =
                calendars.find(
                  e =>
                    e.matches(
                      '.ui-datepicker'
                    ) ||
                    e.querySelector(
                      'td[data-handler="selectDay"]'
                    )
                );

              if(ui){

                const cells =
                  [
                    ...ui.querySelectorAll(
                      'td[data-handler="selectDay"]'
                    )
                  ];

                /*
                 * First attempt to click the requested
                 * date if it is already visible.
                 */
                for(
                  const td of cells
                ){

                  const y =
                    parseInt(
                      td.getAttribute(
                        'data-year'
                      ),
                      10
                    );

                  const m =
                    parseInt(
                      td.getAttribute(
                        'data-month'
                      ),
                      10
                    );

                  const a =
                    td.querySelector(
                      'a.ui-state-default'
                    );

                  if(
                    a &&
                    y === wantedYear &&
                    m === wantedMonth &&
                    (
                      a.textContent ||
                      ''
                    ).trim() ===
                      String(wantedDay)
                  ){

                    a.click();

                    return 'DATE_CLICKED';
                  }
                }

                /*
                 * Date not visible.
                 *
                 * Navigate calendar month.
                 */
                const monthSelect =
                  ui.querySelector(
                    'select.ui-datepicker-month'
                  );

                const yearSelect =
                  ui.querySelector(
                    'select.ui-datepicker-year'
                  );

                let currentMonth =
                  -1;

                let currentYear =
                  -1;

                if(
                  monthSelect &&
                  yearSelect
                ){

                  currentMonth =
                    parseInt(
                      monthSelect.value,
                      10
                    );

                  currentYear =
                    parseInt(
                      yearSelect.value,
                      10
                    );

                } else {

                  const title =
                    ui.querySelector(
                      '.ui-datepicker-title'
                    );

                  if(title){

                    const text =
                      title.innerText ||
                      '';

                    const ym =
                      text.match(
                        /\d{4}/
                      );

                    if(ym){

                      currentYear =
                        parseInt(
                          ym[0],
                          10
                        );

                      const months = [
                        'January',
                        'February',
                        'March',
                        'April',
                        'May',
                        'June',
                        'July',
                        'August',
                        'September',
                        'October',
                        'November',
                        'December'
                      ];

                      for(
                        let i = 0;
                        i < months.length;
                        i++
                      ){

                        if(
                          text
                            .toLowerCase()
                            .includes(
                              months[i]
                                .toLowerCase()
                            )
                        ){

                          currentMonth =
                            i;

                          break;
                        }
                      }
                    }
                  }
                }

                if(
                  currentMonth >= 0 &&
                  currentYear >= 0
                ){

                  const currentIndex =
                    currentYear * 12 +
                    currentMonth;

                  const targetIndex =
                    wantedYear * 12 +
                    wantedMonth;

                  let nav = null;

                  if(
                    targetIndex >
                    currentIndex
                  ){

                    nav =
                      ui.querySelector(
                        '.ui-datepicker-next' +
                        ':not(.ui-state-disabled)'
                      );

                  } else if(
                    targetIndex <
                    currentIndex
                  ){

                    nav =
                      ui.querySelector(
                        '.ui-datepicker-prev' +
                        ':not(.ui-state-disabled)'
                      );
                  }

                  if(nav){

                    nav.click();

                    return 'CALENDAR_MOVED';
                  }
                }

                return 'DATE_NOT_VISIBLE';
              }

              /*
               * Generic calendar fallback.
               *
               * Still clicks a real calendar element.
               * Never writes input.value.
               */
              const generic =
                calendars[0];

              const dayElements =
                [
                  ...generic.querySelectorAll(
                    '[role="gridcell"],button,td'
                  )
                ]
                .filter(
                  e => visible(e)
                );

              for(
                const el of dayElements
              ){

                const text =
                  (
                    el.innerText ||
                    el.textContent ||
                    ''
                  ).trim();

                const aria =
                  (
                    el.getAttribute(
                      'aria-label'
                    ) || ''
                  ).trim();

                const dataDate =
                  (
                    el.getAttribute(
                      'data-date'
                    ) || ''
                  ).trim();

                if(
                  aria.includes(target) ||
                  dataDate === target
                ){

                  el.click();

                  return 'DATE_CLICKED';
                }

                if(
                  text ===
                    String(wantedDay)
                ){

                  el.click();

                  return 'DATE_CLICKED';
                }
              }

              return 'DATE_NOT_FOUND';

            })()
            """.trimIndent()

        eval(js) { result ->

            val clean =
                result
                    .trim('"')
                    .replace(
                        "\\\"",
                        "\""
                    )

            when(clean){

                "DATE_CLICKED" -> {

                    append(
                        "✓ Railway calendar date selected: $targetDate"
                    )

                    /*
                     * ONLY AFTER ACTUAL DATE SELECTION:
                     * wait for Search button.
                     */
                    waitForSearchEnabled(
                        from,
                        to,
                        0
                    )
                }

                "CALENDAR_MOVED" -> {

                    handler.postDelayed(
                        {

                            if(running){

                                selectDateFromCalendar(
                                    from,
                                    to,
                                    attempt + 1
                                )
                            }

                        },
                        400
                    )
                }

                else -> {

                    /*
                     * Calendar/date selection failed.
                     *
                     * DO NOT CHANGE ROUTE.
                     */
                    if(
                        attempt % 10 == 0
                    ){

                        append(
                            "Waiting for Railway calendar... ($clean)"
                        )
                    }

                    handler.postDelayed(
                        {

                            if(!running)
                                return@postDelayed

                            reopenDateCalendar(
                                from,
                                to
                            )

                        },
                        700
                    )
                }
            }
        }
    }

    /*
     * Re-open the actual Pick a date field.
     *
     * This is used when the calendar disappears
     * before the requested date is selected.
     */
    private fun reopenDateCalendar(
        from: String,
        to: String
    ) {

        if(!running)
            return

        val js =
            """
            (function(){

              function visible(e){

                return !!(
                  e &&
                  (
                    e.offsetWidth ||
                    e.offsetHeight ||
                    e.getClientRects().length
                  )
                );
              }

              const inputs =
                [
                  ...document.querySelectorAll(
                    'input'
                  )
                ]
                .filter(
                  e => visible(e)
                );

              const dateField =
                inputs.find(e => {

                  const placeholder =
                    (
                      e.getAttribute(
                        'placeholder'
                      ) || ''
                    )
                    .trim()
                    .toLowerCase();

                  const value =
                    (
                      e.value ||
                      ''
                    )
                    .trim()
                    .toLowerCase();

                  return (
                    placeholder ===
                      'pick a date' ||

                    value ===
                      'pick a date'
                  );

                }) ||

                inputs.find(
                  e =>
                    e.matches(
                      'input[formcontrolname="doj"]'
                    )
                ) ||

                document.querySelector(
                  'input#doj'
                );

              if(!dateField)
                return 'NO_DATE_FIELD';

              /*
               * Only click the field.
               */
              dateField.focus();

              dateField.click();

              return 'DATE_FIELD_CLICKED';

            })()
            """.trimIndent()

        eval(js) {

            handler.postDelayed(
                {

                    if(running){

                        selectDateFromCalendar(
                            from,
                            to,
                            0
                        )
                    }

                },
                500
            )
        }
    }

    /*
     * Search is allowed ONLY after:
     *
     * 1. Date is no longer "Pick a date"
     * 2. Main Search button is enabled
     */
    private fun waitForSearchEnabled(
        from: String,
        to: String,
        attempt: Int
    ) {

        if(!running)
            return

        val js =
            """
            (function(){

              function visible(e){

                return !!(
                  e &&
                  (
                    e.offsetWidth ||
                    e.offsetHeight ||
                    e.getClientRects().length
                  )
                );
              }

              /*
               * Verify date field.
               */
              const inputs =
                [
                  ...document.querySelectorAll(
                    'input'
                  )
                ]
                .filter(
                  e => visible(e)
                );

              const dateField =
                inputs.find(e => {

                  const p =
                    (
                      e.getAttribute(
                        'placeholder'
                      ) || ''
                    )
                    .trim()
                    .toLowerCase();

                  const v =
                    (
                      e.value ||
                      ''
                    )
                    .trim()
                    .toLowerCase();

                  return (
                    p === 'pick a date' ||
                    v === 'pick a date'
                  );

                }) ||

                inputs.find(
                  e =>
                    e.matches(
                      'input[formcontrolname="doj"]'
                    )
                ) ||

                document.querySelector(
                  'input#doj'
                );

              if(!dateField)
                return 'WAIT_DATE_FIELD';

              const dateValue =
                (
                  dateField.value ||
                  ''
                ).trim();

              /*
               * Date not selected yet.
               */
              if(
                !dateValue ||
                dateValue.toLowerCase() ===
                  'pick a date'
              ){

                return 'WAIT_DATE';
              }

              /*
               * Find Search buttons.
               */
              const buttons =
                [
                  ...document.querySelectorAll(
                    'button'
                  )
                ]
                .filter(
                  b =>
                    visible(b) &&
                    (
                      b.innerText ||
                      ''
                    ).trim() ===
                      'Search'
                );

              /*
               * At Home page there should be an
               * enabled Search button.
               */
              const enabled =
                buttons.find(
                  b => !b.disabled
                );

              if(enabled)
                return 'READY';

              return 'WAIT_SEARCH';

            })()
            """.trimIndent()

        eval(js) { result ->

            val clean =
                result
                    .trim('"')
                    .replace(
                        "\\\"",
                        "\""
                    )

            if(
                clean ==
                    "READY"
            ){

                append(
                    "✓ All fields filled. Main Search ENABLED."
                )

                handler.postDelayed(
                    {

                        if(running)
                            clickFirstSearch()

                    },
                    300
                )

            } else {

                /*
                 * CRITICAL:
                 *
                 * Search disabled:
                 * DO NOT move route.
                 * DO NOT start search.
                 */
                if(
                    attempt % 10 == 0
                ){

                    append(
                        "Waiting for Search to enable: $clean"
                    )
                }

                handler.postDelayed(
                    {

                        if(running){

                            waitForSearchEnabled(
                                from,
                                to,
                                attempt + 1
                            )
                        }

                    },
                    500
                )
            }
        }
    }

    /*
     * Click ONLY enabled main Search.
     */
    private fun clickFirstSearch() {

        if(!running)
            return

        val js =
            """
            (function(){

              const buttons =
                [
                  ...document.querySelectorAll(
                    'button'
                  )
                ]
                .filter(
                  b =>
                    (
                      b.innerText ||
                      ''
                    ).trim() ===
                      'Search'
                );

              const b =
                buttons.find(
                  x =>
                    !x.disabled
                );

              if(
                b &&
                !b.disabled
              ){

                b.click();

                return 'CLICKED';
              }

              return 'NOT_ENABLED';

            })()
            """.trimIndent()

        eval(js) { result ->

            val clean =
                result.trim('"')

            if(
                clean ==
                    "CLICKED"
            ){

                append(
                    "✓ Main Search clicked."
                )

                /*
                 * Wait for actual result page.
                 */
                waitResult(0)

            } else {

                /*
                 * Search did not click.
                 *
                 * Stay on SAME route.
                 */
                append(
                    "Search is not enabled. Staying on current route."
                )

                handler.postDelayed(
                    {

                        if(running){

                            waitForSearchEnabled(
                                routes[
                                    routeIndex
                                ].first,

                                routes[
                                    routeIndex
                                ].second,

                                0
                            )
                        }

                    },
                    700
                )
            }
        }
    }

    /*
     * Wait for Railway result URL.
     *
     * No result page = no route change.
     */
    private fun waitResult(
        elapsed: Int
    ) {

        if(!running)
            return

        eval(
            "location.href"
        ) { raw ->

            val url =
                raw.trim('"')

            if(
                url.contains(
                    "/booking/train/search"
                )
            ){

                append(
                    "✓ Result page detected."
                )

                append(
                    "Waiting 2 seconds before result check..."
                )

                /*
                 * Mandatory 2-second pause.
                 */
                handler.postDelayed(
                    {

                        if(running){

                            checkResult(0)
                        }

                    },
                    2000
                )

            } else if(
                elapsed >= 90000
            ){

                /*
                 * IMPORTANT:
                 *
                 * Even timeout does NOT mean
                 * route can be changed immediately.
                 *
                 * We remain on current route and
                 * continue checking.
                 */
                append(
                    "Result page not detected yet. Staying on current route."
                )

                handler.postDelayed(
                    {

                        if(running){

                            waitResult(0)
                        }

                    },
                    2000
                )

            } else {

                handler.postDelayed(
                    {

                        if(running){

                            waitResult(
                                elapsed + 1000
                            )
                        }

                    },
                    1000
                )
            }
        }
    }

    /*
     * Analyze result page.
     */
    private fun checkResult(
        elapsed: Int
    ) {

        if(!running)
            return

        val js =
            """
            (function(){

              function normalize(s){

                return (
                  s || ''
                )
                .replace(
                  /\s+/g,
                  ' '
                )
                .trim();
              }

              const body =
                normalize(
                  document.body?.innerText ||
                  ''
                ).toUpperCase();

              /*
               * EXACT availability label.
               */
              const labels =
                [
                  ...document.querySelectorAll(
                    '*'
                  )
                ].filter(
                  e =>
                    normalize(
                      e.innerText ||
                      ''
                    ) ===
                    'Available Tickets(Counter + Online)'
                );

              const results = [];

              labels.forEach(
                label => {

                  /*
                   * Actual ticket card.
                   */
                  const card =
                    label.closest(
                      '.single-seat-class'
                    );

                  const trip =
                    label.closest(
                      '.single-trip-wrapper'
                    );

                  if(!card)
                    return;

                  const lines =
                    (
                      card.innerText ||
                      ''
                    )
                    .split(
                      /\n+/
                    )
                    .map(
                      x => x.trim()
                    )
                    .filter(
                      Boolean
                    );

                  const index =
                    lines.findIndex(
                      x =>
                        normalize(x) ===
                        'Available Tickets(Counter + Online)'
                    );

                  if(
                    index < 0 ||
                    index + 1 >=
                      lines.length
                  )
                    return;

                  /*
                   * THE NEXT LINE ONLY.
                   */
                  const nextLine =
                    lines[
                      index + 1
                    ];

                  const match =
                    nextLine.match(
                      /^\s*(\d+)\s*$/
                    );

                  if(!match)
                    return;

                  const available =
                    parseInt(
                      match[1],
                      10
                    );

                  let train =
                    'UNKNOWN TRAIN';

                  if(trip){

                    const tripLines =
                      (
                        trip.innerText ||
                        ''
                      )
                      .split(
                        /\n+/
                      )
                      .map(
                        x => x.trim()
                      )
                      .filter(
                        Boolean
                      );

                    if(
                      tripLines.length
                    ){

                      train =
                        tripLines[0];
                    }
                  }

                  let className =
                    'S_CHAIR';

                  for(
                    const line of lines
                  ){

                    const upper =
                      line.toUpperCase();

                    if(
                      upper.includes(
                        'S_CHAIR'
                      ) ||
                      upper.includes(
                        'SHOVAN CHAIR'
                      )
                    ){

                      className =
                        line;

                      break;
                    }
                  }

                  /*
                   * Only actual positive counts
                   * enter results.
                   */
                  if(
                    available > 0
                  ){

                    results.push({

                      train:train,

                      class_name:
                        className,

                      available:
                        available
                    });
                  }
                });

              /*
               * POSITIVE RESULT.
               */
              if(
                results.length > 0
              ){

                return JSON.stringify({

                  type:
                    'AVAILABLE',

                  items:
                    results

                });
              }

              /*
               * If exact availability labels exist
               * and all their following-line counts
               * are zero, NO TICKET.
               */
              if(
                labels.length > 0
              ){

                let validZero =
                  false;

                labels.forEach(
                  label => {

                    const card =
                      label.closest(
                        '.single-seat-class'
                      );

                    if(!card)
                      return;

                    const lines =
                      (
                        card.innerText ||
                        ''
                      )
                      .split(
                        /\n+/
                      )
                      .map(
                        x => x.trim()
                      )
                      .filter(
                        Boolean
                      );

                    const index =
                      lines.findIndex(
                        x =>
                          normalize(x) ===
                          'Available Tickets(Counter + Online)'
                      );

                    if(
                      index >= 0 &&
                      index + 1 <
                        lines.length
                    ){

                      const nextLine =
                        lines[
                          index + 1
                        ];

                      const match =
                        nextLine.match(
                          /^\s*(\d+)\s*$/
                        );

                      if(match){

                        const n =
                          parseInt(
                            match[1],
                            10
                          );

                        if(
                          n === 0
                        ){

                          validZero =
                            true;
                        }
                      }
                    }
                  }
                );

                if(validZero){

                  return JSON.stringify({

                    type:
                      'NO_TICKET'
                  });
                }
              }

              /*
               * Explicit Railway no-ticket message.
               */
              if(
                body.includes(
                  'NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE'
                )
              ){

                return JSON.stringify({

                  type:
                    'NO_TICKET'
                });
              }

              /*
               * Three visible Search buttons
               * = NO TICKET.
               */
              const searchButtons =
                [
                  ...document.querySelectorAll(
                    'button'
                  )
                ]
                .filter(
                  b =>
                    normalize(
                      b.innerText ||
                      ''
                    ) ===
                      'Search' &&
                    (
                      b.offsetWidth ||
                      b.offsetHeight ||
                      b.getClientRects().length
                    )
                );

              if(
                searchButtons.length === 3
              ){

                return JSON.stringify({

                  type:
                    'NO_TICKET',

                  searchButtons:
                    3
                });
              }

              return JSON.stringify({

                type:
                  'WAIT'
              });

            })()
            """.trimIndent()

        eval(js) { raw ->

            try {

                val clean =
                    raw
                        .trim('"')
                        .replace(
                            "\\\"",
                            "\""
                        )

                val o =
                    JSONObject(
                        clean
                    )

                when(
                    o.optString(
                        "type"
                    )
                ){

                    "NO_TICKET" -> {

                        append(
                            "✓ No ticket: " +
                                    routes[
                                        routeIndex
                                    ].first +
                                    " → " +
                                    routes[
                                        routeIndex
                                    ].second
                        )

                        /*
                         * Result page has already
                         * had the mandatory pause.
                         */
                        continueAfterNoTicket()
                    }

                    "AVAILABLE" -> {

                        val arr =
                            o.getJSONArray(
                                "items"
                            )

                        handleAvailable(
                            arr
                        )
                    }

                    else -> {

                        /*
                         * Unknown/incomplete result:
                         * do NOT assume no ticket.
                         */
                        handler.postDelayed(
                            {

                                if(running){

                                    checkResult(
                                        elapsed + 1000
                                    )
                                }

                            },
                            1000
                        )
                    }
                }

            } catch(
                e: Exception
            ){

                handler.postDelayed(
                    {

                        if(running){

                            checkResult(
                                elapsed + 1000
                            )
                        }

                    },
                    1000
                )
            }
        }
    }

    /*
     * Positive ticket result.
     */
    private fun handleAvailable(
        arr: JSONArray
    ) {

        val (
            from,
            to
        ) =
            routes[
                routeIndex
            ]

        val sb =
            StringBuilder(

                "🎫 BANGLADESH RAILWAY TICKET AVAILABLE\n\n" +

                "Route: $from → $to\n" +

                "Date: $targetDate\n\n"
            )

        var positiveCount =
            0

        for(
            i in 0 until arr.length()
        ){

            val x =
                arr.getJSONObject(
                    i
                )

            val count =
                x.optInt(
                    "available",
                    0
                )

            if(
                count <= 0
            )
                continue

            positiveCount++

            sb.append(
                "🚆 " +
                        x.optString(
                            "train"
                        ) +
                        "\n"
            )

            sb.append(
                "💺 Class: " +
                        x.optString(
                            "class_name"
                        ) +
                        "\n"
            )

            sb.append(
                "🎟 Tickets: " +
                        count +
                        "\n\n"
            )
        }

        /*
         * Extra safety:
         * no positive number = no Telegram.
         */
        if(
            positiveCount == 0
        ){

            append(
                "No positive ticket count found. No Telegram sent."
            )

            continueAfterNoTicket()

            return
        }

        val message =
            sb.toString()

        append(
            message
        )

        /*
         * Send Telegram, then continue from NEXT route.
         */
        sendTelegram(
            message
        ) {

            if(!running)
                return@sendTelegram

            append(
                "Ticket handled. Continuing from next route."
            )

            nextRoute()
        }
    }

    /*
     * Handle NO TICKET.
     *
     * Odd round  -> Search #1
     * Even round -> Search #2
     */
    private fun continueAfterNoTicket() {

        if(!running)
            return

        /*
         * Route 6 completed.
         */
        if(
            routeIndex == 5
        ){

            finishSixRouteRound()

            return
        }

        /*
         * Next route.
         */
        val next =
            routeIndex + 1

        /*
         * Odd = 1
         * Even = 2
         */
        val buttonNumber =
            if(
                cycle % 2 == 1
            )
                1
            else
                2

        routeIndex =
            next

        append(
            "No ticket confirmed."
        )

        append(
            "Using secondary Search button #$buttonNumber for next route."
        )

        clickSuggested(
            buttonNumber,
            next,
            0
        )
    }

    /*
     * Click secondary Search button.
     */
    private fun clickSuggested(
        buttonNumber: Int,
        nextIndex: Int,
        attempt: Int
    ) {

        if(!running)
            return

        val js =
            """
            (function(){

              const bs =
                [
                  ...document.querySelectorAll(
                    'button'
                  )
                ]
                .filter(
                  b =>
                    (
                      b.innerText ||
                      ''
                    ).trim() ===
                      'Search' &&

                    (
                      b.offsetWidth ||
                      b.offsetHeight ||
                      b.getClientRects().length
                    )
                );

              if(
                bs.length >=
                  $buttonNumber
              ){

                bs[
                  ${buttonNumber - 1}
                ].click();

                return 'CLICKED';
              }

              return 'NO_BUTTON';

            })()
            """.trimIndent()

        eval(js) { result ->

            val clean =
                result.trim('"')

            if(
                clean ==
                    "CLICKED"
            ){

                val (
                    from,
                    to
                ) =
                    routes[
                        nextIndex
                    ]

                append(
                    "✓ Secondary Search #$buttonNumber clicked."
                )

                append(
                    "Next route: $from → $to"
                )

                setStatus(
                    "Searching ${nextIndex + 1}/6: $from → $to"
                )

                /*
                 * Secondary Search produces a result page.
                 */
                handler.postDelayed(
                    {

                        if(running){

                            waitResult(0)
                        }

                    },
                    500
                )

            } else {

                /*
                 * Do NOT change route.
                 *
                 * Keep trying the same secondary
                 * Search button.
                 */
                if(
                    attempt % 10 == 0
                ){

                    append(
                        "Waiting for secondary Search #$buttonNumber..."
                    )
                }

                handler.postDelayed(
                    {

                        if(running){

                            clickSuggested(
                                buttonNumber,
                                nextIndex,
                                attempt + 1
                            )
                        }

                    },
                    500
                )
            }
        }
    }

    /*
     * Six routes completed.
     *
     * Odd  -> 13 seconds
     * Even -> 14 seconds
     *
     * Then fresh Home -> Route 1.
     */
    private fun finishSixRouteRound() {

        if(!running)
            return

        val breakTime =
            if(
                cycle % 2 == 1
            )
                13000L
            else
                14000L

        append(
            "=== ROUND $cycle COMPLETED: FIRST 6 ROUTES ==="
        )

        if(
            cycle % 2 == 1
        ){

            append(
                "Odd round: 13-second break..."
            )

        } else {

            append(
                "Even round: 14-second break..."
            )
        }

        setStatus(
            "Round $cycle complete"
        )

        handler.postDelayed(
            {

                if(!running)
                    return@postDelayed

                cycle++

                routeIndex = 0

                append(
                    "=== ROUND $cycle STARTED ==="
                )

                if(
                    cycle % 2 == 1
                ){

                    append(
                        "Odd round → secondary Search #1"
                    )

                } else {

                    append(
                        "Even round → secondary Search #2"
                    )
                }

                /*
                 * IMPORTANT:
                 *
                 * New round always starts
                 * from a fresh Home page.
                 */
                runRoute(0)

            },
            breakTime
        )
    }

    /*
     * Move to NEXT route after positive result.
     *
     * Route 6 → finish round.
     *
     * Otherwise:
     * next route starts from fresh Home.
     */
    private fun nextRoute() {

        if(!running)
            return

        if(
            routeIndex >= 5
        ){

            finishSixRouteRound()

            return
        }

        val next =
            routeIndex + 1

        handler.postDelayed(
            {

                if(running){

                    runRoute(
                        next
                    )
                }

            },
            500
        )
    }

    /*
     * Telegram sender.
     */
    private fun sendTelegram(
        message: String,
        onDone: () -> Unit
    ) {

        val token =
            tokenEdit.text
                .toString()
                .trim()

        val chat =
            chatEdit.text
                .toString()
                .trim()

        if(
            token.isEmpty() ||
            chat.isEmpty()
        ){

            append(
                "Telegram skipped: token/chat ID not set."
            )

            handler.post {

                onDone()
            }

            return
        }

        executor.execute {

            try {

                val url =
                    URL(
                        "https://api.telegram.org/bot" +
                                token +
                                "/sendMessage"
                    )

                val c =
                    url.openConnection()
                        as HttpURLConnection

                c.requestMethod =
                    "POST"

                c.doOutput =
                    true

                c.connectTimeout =
                    15000

                c.readTimeout =
                    15000

                val data =
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

                c.outputStream.use {

                    it.write(
                        data.toByteArray()
                    )
                }

                val response =
                    c.responseCode

                val ok =
                    response in 200..299

                c.disconnect()

                handler.post {

                    if(ok){

                        append(
                            "✓ Telegram notification sent."
                        )

                    } else {

                        append(
                            "✗ Telegram HTTP $response"
                        )
                    }

                    onDone()
                }

            } catch(
                e: Exception
            ){

                handler.post {

                    append(
                        "✗ Telegram failed: ${e.message}"
                    )

                    onDone()
                }
            }
        }
    }

    /*
     * Evaluate JavaScript inside WebView.
     */
    private fun eval(
        js: String,
        cb: (String) -> Unit
    ) {

        web.evaluateJavascript(
            js
        ) { result ->

            cb(
                result ?: ""
            )
        }
    }

    /*
     * Append log.
     */
    private fun append(
        s: String
    ) {

        runOnUiThread {

            log.append(
                "\n" + s
            )

            (
                log.parent as?
                    ScrollView
            )?.fullScroll(
                View.FOCUS_DOWN
            )
        }
    }

    /*
     * Status.
     */
    private fun setStatus(
        s: String
    ) {

        runOnUiThread {

            status.text =
                "Status: $s"
        }
    }

    override fun onDestroy() {

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        executor.shutdownNow()

        super.onDestroy()
    }
}
