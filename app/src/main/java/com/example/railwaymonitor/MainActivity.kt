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

    private val handler = Handler(Looper.getMainLooper())

    private var running = false
    private var routeIndex = 0
    private var cycle = 1
    private var targetDate = ""

    private val routes = listOf(
        "Sylhet" to "Dhaka",
        "Maijgaon" to "Dhaka",
        "Kulaura" to "Dhaka",
        "Shamshernagar" to "Dhaka",
        "Sreemangal" to "Dhaka",
        "Shaistaganj" to "Dhaka"
    )

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        web = findViewById(R.id.webView)
        log = findViewById(R.id.logText)
        status = findViewById(R.id.statusText)
        dateEdit = findViewById(R.id.dateEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        chatEdit = findViewById(R.id.chatEdit)

        val prefs =
            getSharedPreferences("settings", MODE_PRIVATE)

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

        findViewById<Button>(R.id.dateButton)
            .setOnClickListener {
                pickDate()
            }

        findViewById<Button>(R.id.startButton)
            .setOnClickListener {
                startMonitor()
            }

        findViewById<Button>(R.id.stopButton)
            .setOnClickListener {
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

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true

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

    private fun pickDate() {

        val c = Calendar.getInstance()

        val current =
            dateEdit.text.toString()
                .split("-")

        if (current.size == 3) {

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
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startMonitor() {

        if (running)
            return

        targetDate =
            dateEdit.text.toString().trim()

        if (
            !Regex("\\d{2}-\\d{2}-\\d{4}")
                .matches(targetDate)
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
            "Round 1 started (Odd → Secondary Search #1)"
        )

        setStatus("Running")

        runRoute(0)
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        setStatus("Stopped")

        append(
            "=== MONITOR STOPPED ==="
        )
    }

    private fun runRoute(index: Int) {

        if (!running)
            return

        if (index !in routes.indices)
            return

        routeIndex = index

        val (from, to) =
            routes[index]

        append(
            "[${index + 1}/6] $from → $to"
        )

        setStatus(
            "Searching ${index + 1}/6: $from → $to"
        )

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )

        handler.postDelayed(
            {

                if (running) {
                    fillAndSearch(
                        from,
                        to,
                        0
                    )
                }

            },
            2200
        )
    }

    /*
     * Fill From / To / Class.
     *
     * IMPORTANT:
     * Date is NOT directly assigned to the input.
     * The real website calendar is opened and the requested
     * day is clicked from that calendar.
     */
    private fun fillAndSearch(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running)
            return

        if (attempt >= 60) {

            append(
                "Could not complete form selection safely."
            )

            nextRoute()

            return
        }

        val js = """
        (function(){

          const from = ${JSONObject.quote(from)};
          const to = ${JSONObject.quote(to)};
          const target = ${JSONObject.quote(targetDate)};

          function visible(e){
            if(!e) return false;

            return !!(
              e.offsetWidth ||
              e.offsetHeight ||
              e.getClientRects().length
            );
          }

          function vis(selector){

            return [...document.querySelectorAll(selector)]
              .find(e => visible(e));
          }

          function fire(e){

            ['input','change','blur'].forEach(type => {

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

          function setInput(name, ctrl){

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

          function clickAgree(){

            [...document.querySelectorAll(
              'button,[role="button"],select,option'
            )].forEach(e => {

              const text =
                (
                  e.innerText ||
                  e.textContent ||
                  ''
                ).trim();

              if(text === 'I AGREE'){
                e.click();
              }

            });

          }

          function setClass(){

            const selects =
              [...document.querySelectorAll('select')];

            selects.forEach(s => {

              [...s.options].forEach(o => {

                const value =
                  (o.value || '').trim();

                const text =
                  (o.textContent || '').trim();

                if(
                  value === 'S_CHAIR' ||
                  text === 'S_CHAIR'
                ){

                  o.selected = true;

                  fire(s);
                }

              });

            });

            const textElement =
              [...document.querySelectorAll('*')]
                .find(e =>
                  e.childElementCount === 0 &&
                  (
                    e.textContent ||
                    ''
                  ).trim() === 'S_CHAIR' &&
                  visible(e)
                );

            if(textElement)
              textElement.click();
          }

          function findDateInput(){

            return (
              vis('input.datepicker.hasDatepicker') ||
              vis('input[formcontrolname="doj"]') ||
              vis('input#doj')
            );

          }

          function findCalendar(){

            const candidates =
              [
                ...document.querySelectorAll(
                  '.ui-datepicker'
                )
              ];

            return candidates.find(
              e => visible(e)
            );
          }

          function targetParts(){

            const p =
              target.split('-');

            return {
              day:parseInt(p[0],10),
              month:parseInt(p[1],10) - 1,
              year:parseInt(p[2],10)
            };
          }

          function calendarParts(dp){

            const monthSelect =
              dp.querySelector(
                'select.ui-datepicker-month'
              );

            const yearSelect =
              dp.querySelector(
                'select.ui-datepicker-year'
              );

            if(
              monthSelect &&
              yearSelect
            ){

              return {
                month:parseInt(
                  monthSelect.value,
                  10
                ),

                year:parseInt(
                  yearSelect.value,
                  10
                )
              };

            }

            const title =
              dp.querySelector(
                '.ui-datepicker-title'
              );

            if(!title)
              return null;

            const titleText =
              title.innerText || '';

            const yearMatch =
              titleText.match(/\d{4}/);

            if(!yearMatch)
              return null;

            const names = [
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

            let month = -1;

            for(
              let i = 0;
              i < names.length;
              i++
            ){

              if(
                titleText
                  .toLowerCase()
                  .includes(
                    names[i].toLowerCase()
                  )
              ){

                month = i;
                break;
              }

            }

            if(month < 0)
              return null;

            return {
              month:month,
              year:parseInt(
                yearMatch[0],
                10
              )
            };
          }

          function clickRequestedDay(){

            const dp =
              findCalendar();

            if(!dp)
              return 'NO_CALENDAR';

            const parts =
              targetParts();

            const days =
              [
                ...dp.querySelectorAll(
                  'td[data-handler="selectDay"]'
                )
              ];

            for(
              const td of days
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
                y === parts.year &&
                m === parts.month &&
                (
                  a.textContent ||
                  ''
                ).trim() ===
                String(parts.day)
              ){

                a.click();

                return 'DATE_CLICKED';
              }
            }

            return 'DAY_NOT_FOUND';
          }

          function moveCalendar(){

            const dp =
              findCalendar();

            if(!dp)
              return 'NO_CALENDAR';

            const targetPartsValue =
              targetParts();

            const current =
              calendarParts(dp);

            if(!current)
              return 'NO_CALENDAR_INFO';

            if(
              current.year ===
                targetPartsValue.year &&
              current.month ===
                targetPartsValue.month
            ){

              return clickRequestedDay();
            }

            const currentIndex =
              current.year * 12 +
              current.month;

            const targetIndex =
              targetPartsValue.year * 12 +
              targetPartsValue.month;

            let button;

            if(targetIndex > currentIndex){

              button =
                dp.querySelector(
                  '.ui-datepicker-next:not(.ui-state-disabled)'
                );

            } else {

              button =
                dp.querySelector(
                  '.ui-datepicker-prev:not(.ui-state-disabled)'
                );

            }

            if(!button)
              return 'NO_NAV_BUTTON';

            button.click();

            return 'MOVED';
          }

          clickAgree();

          setInput(
            from,
            'fromcity'
          );

          setInput(
            to,
            'tocity'
          );

          setClass();

          const dateInput =
            findDateInput();

          if(!dateInput)
            return 'WAIT_DATE_INPUT';

          /*
           * Do NOT assign targetDate to dateInput.value.
           * Open the actual Railway website calendar.
           */
          dateInput.focus();
          dateInput.click();

          return 'CALENDAR_OPENING';

        })()
        """.trimIndent()

        eval(js) {

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
                500
            )
        }
    }

    /*
     * Select the requested date by navigating the actual
     * website calendar and clicking its day.
     */
    private fun selectDateFromCalendar(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running)
            return

        if (attempt >= 40) {

            append(
                "Date calendar selection timeout."
            )

            nextRoute()

            return
        }

        val js = """
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

          const dp =
            [...document.querySelectorAll(
              '.ui-datepicker'
            )]
            .find(e => visible(e));

          if(!dp)
            return 'NO_CALENDAR';

          const p =
            target.split('-');

          const day =
            parseInt(p[0],10);

          const month =
            parseInt(p[1],10) - 1;

          const year =
            parseInt(p[2],10);

          const monthSelect =
            dp.querySelector(
              'select.ui-datepicker-month'
            );

          const yearSelect =
            dp.querySelector(
              'select.ui-datepicker-year'
            );

          let currentMonth = -1;
          let currentYear = -1;

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
              dp.querySelector(
                '.ui-datepicker-title'
              );

            if(!title)
              return 'NO_TITLE';

            const text =
              title.innerText || '';

            const ym =
              text.match(/\d{4}/);

            if(!ym)
              return 'NO_YEAR';

            currentYear =
              parseInt(
                ym[0],
                10
              );

            const names = [
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
              let i=0;
              i<names.length;
              i++
            ){

              if(
                text
                  .toLowerCase()
                  .includes(
                    names[i].toLowerCase()
                  )
              ){

                currentMonth = i;
                break;
              }

            }
          }

          if(
            currentMonth === month &&
            currentYear === year
          ){

            const cells =
              [
                ...dp.querySelectorAll(
                  'td[data-handler="selectDay"]'
                )
              ];

            for(
              const td of cells
            ){

              const tdYear =
                parseInt(
                  td.getAttribute(
                    'data-year'
                  ),
                  10
                );

              const tdMonth =
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
                tdYear === year &&
                tdMonth === month &&
                (
                  a.textContent ||
                  ''
                ).trim() ===
                String(day)
              ){

                a.click();

                return 'DATE_CLICKED';
              }
            }

            return 'DAY_NOT_FOUND';
          }

          const currentIndex =
            currentYear * 12 +
            currentMonth;

          const targetIndex =
            year * 12 +
            month;

          let nav = null;

          if(
            targetIndex >
            currentIndex
          ){

            nav =
              dp.querySelector(
                '.ui-datepicker-next:not(.ui-state-disabled)'
              );

          } else {

            nav =
              dp.querySelector(
                '.ui-datepicker-prev:not(.ui-state-disabled)'
              );
          }

          if(!nav)
            return 'NO_NAV';

          nav.click();

          return 'MOVED';

        })()
        """.trimIndent()

        eval(js) { result ->

            val clean =
                result
                    .trim('"')
                    .replace("\\\"", "\"")

            when {

                clean == "DATE_CLICKED" -> {

                    append(
                        "Date selected from Railway calendar: $targetDate"
                    )

                    waitForSearchEnabled(
                        from,
                        to,
                        0
                    )
                }

                clean == "MOVED" -> {

                    handler.postDelayed(
                        {

                            selectDateFromCalendar(
                                from,
                                to,
                                attempt + 1
                            )

                        },
                        250
                    )
                }

                else -> {

                    handler.postDelayed(
                        {

                            selectDateFromCalendar(
                                from,
                                to,
                                attempt + 1
                            )

                        },
                        400
                    )
                }
            }
        }
    }

    /*
     * Wait until the MAIN home-page Search button becomes enabled.
     */
    private fun waitForSearchEnabled(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running)
            return

        if (attempt >= 60) {

            append(
                "Main Search button did not become enabled."
            )

            nextRoute()

            return
        }

        val js = """
        (function(){

          const buttons =
            [...document.querySelectorAll('button')];

          const b =
            buttons.find(
              x =>
                (
                  x.innerText ||
                  ''
                ).trim() === 'Search'
            );

          if(
            b &&
            !b.disabled
          ){

            return 'READY';
          }

          return 'WAIT';

        })()
        """.trimIndent()

        eval(js) { result ->

            val clean =
                result
                    .trim('"')

            if(clean == "READY") {

                append(
                    "Main Search enabled."
                )

                handler.postDelayed(
                    {
                        clickFirstSearch()
                    },
                    300
                )

            } else {

                handler.postDelayed(
                    {

                        waitForSearchEnabled(
                            from,
                            to,
                            attempt + 1
                        )

                    },
                    500
                )
            }
        }
    }

    private fun clickFirstSearch() {

        if (!running)
            return

        val js = """
        (function(){

          const b =
            [...document.querySelectorAll(
              'button'
            )]
            .find(
              x =>
                (
                  x.innerText ||
                  ''
                ).trim() === 'Search' &&
                !x.disabled
            );

          if(b){

            b.click();

            return 'CLICKED';
          }

          return 'NO';

        })()
        """.trimIndent()

        eval(js) { result ->

            val clean =
                result.trim('"')

            if(clean == "CLICKED") {

                append(
                    "Main Search clicked; waiting for Railway result..."
                )

                waitResult(0)

            } else {

                handler.postDelayed(
                    {
                        clickFirstSearch()
                    },
                    500
                )
            }
        }
    }

    private fun waitResult(
        elapsed: Int
    ) {

        if (!running)
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
                    "Result page detected. Waiting 2 seconds before checking result..."
                )

                /*
                 * Mandatory minimum 2-second pause
                 * whenever a result page is detected.
                 */
                handler.postDelayed(
                    {
                        checkResult(0)
                    },
                    2000
                )

            } else if(
                elapsed >= 90000
            ){

                append(
                    "Result page timeout; moving safely to next route."
                )

                nextRoute()

            } else {

                handler.postDelayed(
                    {
                        waitResult(
                            elapsed + 1000
                        )
                    },
                    1000
                )
            }
        }
    }

    private fun checkResult(
        elapsed: Int
    ) {

        if (!running)
            return

        val js = """
        (function(){

          const normalize = s =>
            (s || '')
              .replace(/\s+/g,' ')
              .trim();

          const body =
            normalize(
              document.body?.innerText || ''
            ).toUpperCase();

          /*
           * First check the actual ticket cards.
           *
           * Availability is determined ONLY from:
           *
           * Available Tickets(Counter + Online)
           *
           * and the number immediately following it.
           */
          const results = [];

          const all =
            [...document.querySelectorAll('*')];

          const labels =
            all.filter(
              e =>
                normalize(
                  e.innerText ||
                  ''
                ) ===
                'Available Tickets(Counter + Online)'
            );

          labels.forEach(label => {

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

            const cardLines =
              (card.innerText || '')
                .split(/\n+/)
                .map(x => x.trim())
                .filter(Boolean);

            const labelIndex =
              cardLines.findIndex(
                x =>
                  normalize(x) ===
                  'Available Tickets(Counter + Online)'
              );

            if(
              labelIndex < 0 ||
              labelIndex + 1 >=
              cardLines.length
            )
              return;

            /*
             * The number on the following line is the
             * ONLY value used for availability.
             */
            const countText =
              cardLines[
                labelIndex + 1
              ];

            const match =
              countText.match(/\d+/);

            if(!match)
              return;

            const available =
              parseInt(
                match[0],
                10
              );

            let train =
              'UNKNOWN TRAIN';

            if(trip){

              const tripLines =
                (trip.innerText || '')
                  .split(/\n+/)
                  .map(x => x.trim())
                  .filter(Boolean);

              if(tripLines.length)
                train = tripLines[0];
            }

            let className =
              'S_CHAIR';

            /*
             * Try to identify the seat class from the
             * same ticket card.
             */
            for(
              const line of cardLines
            ){

              const upper =
                line.toUpperCase();

              if(
                upper.includes('S_CHAIR') ||
                upper.includes('SHOVAN CHAIR')
              ){

                className = line;
                break;
              }
            }

            results.push({
              train:train,
              class_name:className,
              available:available
            });

          });

          /*
           * Positive result:
           * only count > 0 is treated as available.
           */
          const positive =
            results.filter(
              x => x.available > 0
            );

          if(positive.length){

            return JSON.stringify({
              type:'AVAILABLE',
              items:positive
            });

          }

          /*
           * If actual ticket cards exist and all their
           * following-line counts are zero, this is
           * definitely no ticket.
           */
          if(labels.length){

            let validCount = 0;

            labels.forEach(label => {

              const card =
                label.closest(
                  '.single-seat-class'
                );

              if(!card)
                return;

              const lines =
                (card.innerText || '')
                  .split(/\n+/)
                  .map(x => x.trim())
                  .filter(Boolean);

              const idx =
                lines.findIndex(
                  x =>
                    normalize(x) ===
                    'Available Tickets(Counter + Online)'
                );

              if(
                idx >= 0 &&
                idx + 1 < lines.length
              ){

                const m =
                  lines[
                    idx + 1
                  ].match(/\d+/);

                if(m){

                  validCount++;

                  const n =
                    parseInt(
                      m[0],
                      10
                    );

                  if(n > 0){
                    return;
                  }
                }
              }
            });

            if(validCount > 0){

              return JSON.stringify({
                type:'NO_TICKET'
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
              type:'NO_TICKET'
            });

          }

          /*
           * Three Search buttons on a result page
           * means NO TICKET.
           */
          const searchButtons =
            [...document.querySelectorAll(
              'button'
            )]
            .filter(
              b =>
                normalize(
                  b.innerText ||
                  ''
                ) === 'Search' &&
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
              type:'NO_TICKET',
              searchButtons:3
            });
          }

          return JSON.stringify({
            type:'WAIT'
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
                    JSONObject(clean)

                when(
                    o.optString("type")
                ){

                    "NO_TICKET" -> {

                        append(
                            "No ticket: " +
                                    routes[routeIndex].first +
                                    " → " +
                                    routes[routeIndex].second
                        )

                        /*
                         * Result page has already been
                         * visible for at least 2 seconds.
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

                        if(
                            elapsed >= 90000
                        ){

                            append(
                                "Result data timeout; NOT treating empty page as no-ticket."
                            )

                            nextRoute()

                        } else {

                            handler.postDelayed(
                                {

                                    checkResult(
                                        elapsed + 1000
                                    )

                                },
                                1000
                            )
                        }
                    }
                }

            } catch(e: Exception) {

                if(
                    elapsed >= 90000
                ){

                    append(
                        "Result parsing timeout; moving safely to next route."
                    )

                    nextRoute()

                } else {

                    handler.postDelayed(
                        {

                            checkResult(
                                elapsed + 1000
                            )

                        },
                        1000
                    )
                }
            }
        }
    }

    private fun handleAvailable(
        arr: JSONArray
    ) {

        val (from, to) =
            routes[routeIndex]

        val sb =
            StringBuilder(
                "🎫 BANGLADESH RAILWAY TICKET AVAILABLE\n\n" +
                        "Route: $from → $to\n" +
                        "Date: $targetDate\n\n"
            )

        for(
            i in 0 until arr.length()
        ){

            val x =
                arr.getJSONObject(i)

            val count =
                x.optInt(
                    "available",
                    0
                )

            /*
             * Extra safety:
             * Telegram is only prepared for count > 0.
             */
            if(count <= 0)
                continue

            sb.append(
                "🚆 ${x.optString("train")}\n"
            )

            sb.append(
                "💺 Class: ${
                    x.optString(
                        "class_name"
                    )
                }\n"
            )

            sb.append(
                "🎟 Tickets: $count\n\n"
            )
        }

        /*
         * Do not send anything if there is no
         * actual positive count.
         */
        if(
            !sb.contains(
                "🎟 Tickets:"
            )
        ){

            continueAfterNoTicket()

            return
        }

        append(
            sb.toString()
        )

        sendTelegram(
            sb.toString()
        ) {

            if(running){

                append(
                    "Ticket handled. Going Home and continuing from the next route."
                )

                nextRoute()
            }
        }
    }

    /*
     * Negative result handling.
     *
     * Odd round  -> Search button #1
     * Even round -> Search button #2
     *
     * The secondary Search button is used to go to
     * the NEXT route without restarting the round.
     */
    private fun continueAfterNoTicket() {

        if (!running)
            return

        /*
         * Route 6 completed.
         * Do NOT use a secondary button for a non-existent
         * route 7. Complete the six-route round first.
         */
        if(routeIndex == 5){

            finishSixRouteRound()

            return
        }

        val next =
            routeIndex + 1

        val buttonNumber =
            if(cycle % 2 == 1)
                1
            else
                2

        routeIndex = next

        append(
            "No ticket confirmed. Using secondary Search button #$buttonNumber for next route."
        )

        clickSuggested(
            buttonNumber,
            next,
            0
        )
    }

    private fun clickSuggested(
        buttonNumber: Int,
        nextIndex: Int,
        attempt: Int
    ) {

        if (!running)
            return

        if(attempt >= 30){

            append(
                "Secondary Search button #$buttonNumber not available."
            )

            /*
             * Safety fallback: restart next route from Home.
             */
            runRoute(nextIndex)

            return
        }

        val js = """
        (function(){

          const bs =
            [...document.querySelectorAll(
              'button'
            )]
            .filter(
              b =>
                (
                  b.innerText ||
                  ''
                ).trim() === 'Search' &&
                (
                  b.offsetWidth ||
                  b.offsetHeight ||
                  b.getClientRects().length
                )
            );

          if(
            bs.length >= $buttonNumber
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

            if(clean == "CLICKED"){

                val (from, to) =
                    routes[nextIndex]

                append(
                    "Secondary Search #$buttonNumber clicked for $from → $to."
                )

                setStatus(
                    "Searching ${nextIndex + 1}/6: $from → $to"
                )

                /*
                 * The secondary Search opens another result page.
                 * waitResult() will again enforce the 2-second
                 * result-page pause.
                 */
                handler.postDelayed(
                    {

                        if(running)
                            waitResult(0)

                    },
                    500
                )

            } else {

                handler.postDelayed(
                    {

                        clickSuggested(
                            buttonNumber,
                            nextIndex,
                            attempt + 1
                        )

                    },
                    500
                )
            }
        }
    }

    /*
     * Finish exactly six routes.
     *
     * Odd round:
     *     13-second break
     *
     * Even round:
     *     14-second break
     *
     * Then always start the next round from a fresh Home page.
     */
    private fun finishSixRouteRound() {

        if (!running)
            return

        val breakTime =
            if(cycle % 2 == 1)
                13000L
            else
                14000L

        append(
            "=== ROUND $cycle COMPLETED: FIRST 6 ROUTES ==="
        )

        append(
            if(cycle % 2 == 1)
                "Odd round: 13-second break..."
            else
                "Even round: 14-second break..."
        )

        setStatus(
            "Round $cycle complete"
        )

        handler.postDelayed(
            {

                if(!running)
                    return@postDelayed

                cycle++

                append(
                    "=== ROUND $cycle STARTED ==="
                )

                if(cycle % 2 == 1){

                    append(
                        "Odd round: secondary Search #1 will be used."
                    )

                } else {

                    append(
                        "Even round: secondary Search #2 will be used."
                    )
                }

                /*
                 * IMPORTANT:
                 * Every new round starts from fresh Home.
                 */
                routeIndex = 0

                runRoute(0)

            },
            breakTime
        )
    }

    /*
     * Move to the next route after a positive ticket result
     * or another safe completion.
     *
     * If route 6 was the last route, complete the round.
     * Otherwise restart that NEXT route from fresh Home.
     */
    private fun nextRoute() {

        if (!running)
            return

        if(routeIndex >= 5){

            finishSixRouteRound()

            return
        }

        val next =
            routeIndex + 1

        handler.postDelayed(
            {

                if(running)
                    runRoute(next)

            },
            500
        )
    }

    private fun sendTelegram(
        message: String,
        onDone: () -> Unit
    ){

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
                        "https://api.telegram.org/bot$token/sendMessage"
                    )

                val c =
                    url.openConnection()
                        as HttpURLConnection

                c.requestMethod =
                    "POST"

                c.doOutput = true

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

                    append(
                        if(ok)
                            "✓ Telegram notification sent."
                        else
                            "✗ Telegram HTTP $response"
                    )

                    onDone()
                }

            } catch(e: Exception){

                handler.post {

                    append(
                        "✗ Telegram failed: ${e.message}"
                    )

                    onDone()
                }
            }
        }
    }

    private fun eval(
        js: String,
        cb: (String) -> Unit
    ){

        web.evaluateJavascript(
            js
        ) { result ->

            cb(
                result ?: ""
            )
        }
    }

    private fun toIso(
        s: String
    ): String = try {

        SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.US
        )
            .parse(s)
            ?.let {

                SimpleDateFormat(
                    "yyyy-MM-dd",
                    Locale.US
                ).format(it)

            } ?: s

    } catch(_: Exception){

        s
    }

    private fun append(
        s: String
    ){

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

    private fun setStatus(
        s: String
    ){

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
