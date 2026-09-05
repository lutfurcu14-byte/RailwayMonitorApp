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
        "Shaistaganj" to "Dhaka",

        "Sylhet" to "Biman_Bandar",
        "Maijgaon" to "Biman_Bandar",
        "Kulaura" to "Biman_Bandar",
        "Shamshernagar" to "Biman_Bandar",
        "Sreemangal" to "Biman_Bandar",
        "Shaistaganj" to "Biman_Bandar"
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

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        dateEdit.setText(
            prefs.getString("date", "07-09-2026")
        )

        tokenEdit.setText(
            prefs.getString("token", "")
        )

        chatEdit.setText(
            prefs.getString("chat", "")
        )

        setupWebView()

        findViewById<Button>(R.id.dateButton).setOnClickListener {
            pickDate()
        }

        findViewById<Button>(R.id.startButton).setOnClickListener {
            startMonitor()
        }

        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopMonitor()
        }

        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                55
            )
        }
    }

    private fun setupWebView() {

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.loadsImagesAutomatically = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true

        web.settings.userAgentString =
            web.settings.userAgentString + " RailwayMonitorApp/1.0"

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                append("Page loaded: ${url ?: ""}")
            }
        }

        web.webChromeClient = WebChromeClient()

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )
    }

    private fun pickDate() {

        val c = Calendar.getInstance()

        val current =
            dateEdit.text.toString().split("-")

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

        if (running) return

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
            .putString("date", targetDate)
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

        append("=== MONITOR STARTED ===")
        append(
            "Date: $targetDate | Class: S_CHAIR | Routes: 12"
        )

        setStatus("Running")

        runRoute(0)
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(null)

        setStatus("Stopped")

        append("=== MONITOR STOPPED ===")
    }

    private fun runRoute(index: Int) {

        if (!running) return

        routeIndex = index

        val (from, to) = routes[index]

        append(
            "[${index + 1}/12] $from → $to"
        )

        setStatus(
            "Searching ${index + 1}/12: $from → $to"
        )

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )

        handler.postDelayed(
            {
                prepareRoute(from, to, 0)
            },
            3000
        )
    }

    /*
     * Repeatedly prepares the complete form.
     *
     * IMPORTANT:
     * City input is not considered complete merely because
     * its text contains the requested station.
     *
     * We also try to select the actual autocomplete item.
     */
    private fun prepareRoute(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running) return

        if (attempt >= 35) {

            append(
                "Form preparation timeout for $from → $to"
            )

            nextRoute()
            return
        }

        val js = """
        (function(){

          function visible(e){
            if(!e) return false;

            const r = e.getBoundingClientRect();

            return (
              r.width > 0 &&
              r.height > 0 &&
              getComputedStyle(e).visibility !== 'hidden' &&
              getComputedStyle(e).display !== 'none'
            );
          }

          function normalize(s){
            return (s || '')
              .trim()
              .toUpperCase()
              .replace(/_/g,' ')
              .replace(/\s+/g,' ');
          }

          function nativeValue(e,value){

            if(!e) return false;

            try{

              const setter =
                Object.getOwnPropertyDescriptor(
                  HTMLInputElement.prototype,
                  'value'
                )?.set;

              if(setter){
                setter.call(e,value);
              }else{
                e.value = value;
              }

              ['input','change'].forEach(function(type){

                e.dispatchEvent(
                  new Event(
                    type,
                    {
                      bubbles:true,
                      cancelable:true
                    }
                  )
                );

              });

              return true;

            }catch(err){

              return false;
            }
          }

          function findInput(control){

            const selectors = [

              'input[formcontrolname="' + control + '"]',

              'input[name="' + control + '"]',

              'input[id="' + control + '"]',

              'input[placeholder*="From"]',

              'input[placeholder*="To"]'

            ];

            for(const selector of selectors){

              const all =
                [...document.querySelectorAll(selector)];

              const e =
                all.find(visible);

              if(e) return e;
            }

            return null;
          }

          /*
           * Select the real Angular autocomplete option.
           */
          function selectAutocomplete(name){

            const wanted = normalize(name);

            const elements =
              [
                ...document.querySelectorAll(
                  '[role="option"],' +
                  '.mat-option,' +
                  '.ng-option,' +
                  '.autocomplete-option,' +
                  'li,' +
                  'div,' +
                  'span'
                )
              ];

            const candidates = [];

            for(const e of elements){

              if(!visible(e)) continue;

              const text =
                normalize(
                  e.innerText ||
                  e.textContent ||
                  ''
                );

              if(!text) continue;

              /*
               * Exact match is preferred.
               */
              if(text === wanted){

                candidates.push(e);
              }
            }

            /*
             * Choose the smallest exact visible element.
             * This prevents clicking a large parent container
             * when the actual option is a child element.
             */
            candidates.sort(function(a,b){

              const ar =
                a.getBoundingClientRect();

              const br =
                b.getBoundingClientRect();

              return (
                (ar.width * ar.height) -
                (br.width * br.height)
              );
            });

            if(candidates.length){

              const e = candidates[0];

              e.scrollIntoView({
                block:'nearest',
                inline:'nearest'
              });

              /*
               * Native click.
               */
              e.click();

              /*
               * Additional pointer events.
               */
              ['mousedown','mouseup','click']
                .forEach(function(type){

                  try{

                    e.dispatchEvent(
                      new MouseEvent(
                        type,
                        {
                          bubbles:true,
                          cancelable:true,
                          view:window
                        }
                      )
                    );

                  }catch(err){}
                });

              return true;
            }

            return false;
          }

          function setCity(name,control){

            const e = findInput(control);

            if(!e){

              return {
                input:false,
                selected:false
              };
            }

            e.focus();

            /*
             * Clear old station first.
             */
            nativeValue(e,'');

            /*
             * Type requested station.
             */
            nativeValue(e,name);

            /*
             * Keyboard/input events help Angular autocomplete.
             */
            try{

              e.dispatchEvent(
                new KeyboardEvent(
                  'keydown',
                  {
                    bubbles:true,
                    key:'ArrowDown'
                  }
                )
              );

              e.dispatchEvent(
                new KeyboardEvent(
                  'keyup',
                  {
                    bubbles:true,
                    key:'ArrowDown'
                  }
                )
              );

            }catch(err){}

            /*
             * Give autocomplete a chance to render.
             */
            const selected =
              selectAutocomplete(name);

            /*
             * If the option was not available yet,
             * the next prepareRoute attempt will try again.
             */
            return {
              input:true,
              selected:selected
            };
          }

          function clickAgree(){

            [
              ...document.querySelectorAll(
                'button,[role="button"],a'
              )
            ].forEach(function(e){

              if(!visible(e)) return;

              const t =
                normalize(
                  e.innerText ||
                  e.textContent ||
                  ''
                );

              if(t === 'I AGREE'){

                try{
                  e.click();
                }catch(err){}

              }

            });
          }

          function setDate(){

            const iso =
              ${JSONObject.quote(toIso(targetDate))};

            const inputs =
              [...document.querySelectorAll('input')]
              .filter(visible);

            for(const e of inputs){

              const id =
                (e.id || '').toLowerCase();

              const name =
                (e.getAttribute('name') || '')
                .toLowerCase();

              const fc =
                (e.getAttribute('formcontrolname') || '')
                .toLowerCase();

              if(
                id === 'doj' ||
                name === 'doj' ||
                fc === 'doj' ||
                e.classList.contains('hasDatepicker')
              ){

                nativeValue(e,iso);

                try{

                  e.dispatchEvent(
                    new Event(
                      'blur',
                      {
                        bubbles:true
                      }
                    )
                  );

                }catch(err){}
              }
            }

            const hidden =
              document.querySelector(
                'input[type="hidden"][formcontrolname="doj"]'
              );

            if(hidden){

              try{

                const setter =
                  Object.getOwnPropertyDescriptor(
                    HTMLInputElement.prototype,
                    'value'
                  )?.set;

                if(setter){
                  setter.call(hidden,iso);
                }else{
                  hidden.value = iso;
                }

                hidden.dispatchEvent(
                  new Event(
                    'change',
                    {
                      bubbles:true
                    }
                  )
                );

              }catch(err){}
            }
          }

          function chooseClass(){

            let found = false;

            /*
             * SELECT.
             */
            [...document.querySelectorAll('select')]
              .forEach(function(select){

                [...select.options]
                  .forEach(function(option){

                    const text =
                      normalize(
                        option.textContent || ''
                      );

                    const value =
                      normalize(
                        option.value || ''
                      );

                    if(
                      text === 'S CHAIR' ||
                      value === 'S CHAIR'
                    ){

                      option.selected = true;

                      select.dispatchEvent(
                        new Event(
                          'change',
                          {
                            bubbles:true
                          }
                        )
                      );

                      select.dispatchEvent(
                        new Event(
                          'input',
                          {
                            bubbles:true
                          }
                        )
                      );

                      found = true;
                    }
                  });
              });

            /*
             * Clickable class option.
             */
            [
              ...document.querySelectorAll(
                'button,[role="button"],' +
                'label,div,span'
              )
            ].forEach(function(e){

              if(!visible(e)) return;

              const text =
                normalize(
                  e.innerText ||
                  e.textContent ||
                  ''
                );

              if(
                text === 'S CHAIR' ||
                text === 'S_CHAIR'
              ){

                try{
                  e.click();
                  found = true;
                }catch(err){}

              }

            });

            /*
             * Radio / checkbox.
             */
            [
              ...document.querySelectorAll(
                'input[type="radio"],' +
                'input[type="checkbox"]'
              )
            ].forEach(function(input){

              const parent =
                input.parentElement;

              const text =
                normalize(
                  (input.value || '') +
                  ' ' +
                  (parent?.innerText || '')
                );

              if(
                text.includes('S CHAIR') ||
                text.includes('S_CHAIR')
              ){

                try{
                  input.click();
                  found = true;
                }catch(err){}

              }
            });

            return found;
          }

          clickAgree();

          const fromResult =
            setCity(
              ${JSONObject.quote(from)},
              'fromcity'
            );

          const toResult =
            setCity(
              ${JSONObject.quote(to)},
              'tocity'
            );

          setDate();

          const classOK =
            chooseClass();

          return JSON.stringify({

            fromInput:fromResult.input,

            fromSelected:fromResult.selected,

            toInput:toResult.input,

            toSelected:toResult.selected,

            classOK:classOK,

            requestedFrom:${JSONObject.quote(from)},

            requestedTo:${JSONObject.quote(to)},

            url:location.href

          });

        })()
        """.trimIndent()

        eval(js) { raw ->

            append(
                "Form attempt ${attempt + 1}: $raw"
            )

            handler.postDelayed(
                {

                    if (!running)
                        return@postDelayed

                    /*
                     * Give autocomplete several attempts to
                     * actually select both stations.
                     */
                    if(attempt >= 4){

                        waitForSearchButton(
                            from,
                            to,
                            0
                        )

                    }else{

                        prepareRoute(
                            from,
                            to,
                            attempt + 1
                        )
                    }

                },
                800
            )
        }
    }

    private fun waitForSearchButton(
        from: String,
        to: String,
        attempt: Int
    ) {

        if (!running) return

        if(attempt >= 50){

            append(
                "Search button timeout for $from → $to"
            )

            nextRoute()
            return
        }

        val js = """
        (function(){

          function visible(e){

            if(!e) return false;

            const r =
              e.getBoundingClientRect();

            return (
              r.width > 0 &&
              r.height > 0 &&
              getComputedStyle(e).visibility !== 'hidden' &&
              getComputedStyle(e).display !== 'none'
            );
          }

          function text(e){

            return (
              e.innerText ||
              e.textContent ||
              e.value ||
              e.getAttribute('aria-label') ||
              ''
            )
            .trim()
            .replace(/\s+/g,' ')
            .toUpperCase();
          }

          const all =
            [
              ...document.querySelectorAll(
                'button,' +
                'input[type="button"],' +
                'input[type="submit"],' +
                '[role="button"]'
              )
            ];

          const candidates =
            all.filter(function(e){

              if(!visible(e))
                return false;

              const t = text(e);

              return (
                t === 'SEARCH' ||
                t === 'SEARCH TRAIN' ||
                t.includes('SEARCH')
              );
            });

          const enabled =
            candidates.find(function(e){

              return (
                !e.disabled &&
                e.getAttribute(
                  'aria-disabled'
                ) !== 'true' &&
                !e.classList.contains('disabled') &&
                !e.classList.contains('mat-button-disabled')
              );

            });

          if(enabled){

            enabled.scrollIntoView({
              block:'center',
              inline:'center'
            });

            /*
             * Focus first.
             */
            try{
              enabled.focus();
            }catch(err){}

            /*
             * Native DOM click.
             */
            try{
              enabled.click();
            }catch(err){}

            /*
             * Mouse events.
             */
            [
              'mousedown',
              'mouseup',
              'click'
            ].forEach(function(type){

              try{

                enabled.dispatchEvent(
                  new MouseEvent(
                    type,
                    {
                      bubbles:true,
                      cancelable:true,
                      view:window
                    }
                  )
                );

              }catch(err){}

            });

            return JSON.stringify({

              result:'CLICKED',

              text:text(enabled),

              candidates:candidates.length,

              from:${JSONObject.quote(from)},

              to:${JSONObject.quote(to)}

            });
          }

          /*
           * Diagnostic information.
           */
          return JSON.stringify({

            result:'WAIT',

            candidates:candidates.length,

            visibleSearches:
              candidates.map(function(e){
                return text(e);
              })

          });

        })()
        """.trimIndent()

        eval(js) { raw ->

            append(
                "Search attempt ${attempt + 1}: $raw"
            )

            if(
                raw.contains("\"CLICKED\"")
            ){

                append(
                    "Search clicked; waiting for Railway result..."
                )

                handler.postDelayed(
                    {
                        waitResult(0)
                    },
                    1000
                )

            }else{

                handler.postDelayed(
                    {
                        waitForSearchButton(
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

    private fun waitResult(elapsed: Int) {

        if (!running) return

        eval("location.href") { raw ->

            val url =
                raw.trim('"')

            if(
                url.contains(
                    "/booking/train/search"
                )
            ){

                append(
                    "Result page detected. Waiting for result data (5 sec initial)..."
                )

                handler.postDelayed(
                    {
                        checkResult(0)
                    },
                    5000
                )

            } else if(elapsed >= 90000){

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

    private fun checkResult(elapsed: Int) {

        if (!running) return

        val js = """
        (function(){

          const body =
            (document.body?.innerText || '')
            .replace(/\s+/g,' ')
            .toUpperCase();

          if(
            body.includes(
              'NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE'
            )
          ){

            return JSON.stringify({
              type:'NO_TICKET'
            });
          }

          const out = [];

          document
            .querySelectorAll('.available-text')
            .forEach(label => {

              const card =
                label.closest(
                  '.single-seat-class'
                );

              const trip =
                label.closest(
                  '.single-trip-wrapper'
                );

              if(!card || !trip) return;

              const tl =
                (trip.innerText || '')
                .split(/\n+/)
                .map(x => x.trim())
                .filter(Boolean);

              const cl =
                (card.innerText || '')
                .split(/\n+/)
                .map(x => x.trim())
                .filter(Boolean);

              const ll =
                (label.innerText || '')
                .split(/\n+/)
                .map(x => x.trim())
                .filter(Boolean);

              let n =
                parseInt(
                  ll[ll.length-1] || '0',
                  10
                );

              if(
                !Number.isNaN(n) &&
                n > 0
              ){

                out.push({
                  train:
                    tl[0] ||
                    'UNKNOWN TRAIN',

                  class_name:
                    cl[0] ||
                    'S_CHAIR',

                  available:n
                });
              }
            });

          if(out.length){

            return JSON.stringify({
              type:'AVAILABLE',
              items:out
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
                        .replace("\\\"", "\"")

                val o =
                    JSONObject(clean)

                val type =
                    o.optString("type")

                when(type){

                    "NO_TICKET" -> {

                        append(
                            "No ticket: ${routes[routeIndex].first} → ${routes[routeIndex].second}"
                        )

                        continueAfterNoTicket()
                    }

                    "AVAILABLE" -> {

                        val arr =
                            o.getJSONArray("items")

                        handleAvailable(arr)
                    }

                    else -> {

                        if(elapsed >= 90000){

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

                if(elapsed >= 90000){

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

        for(i in 0 until arr.length()){

            val x =
                arr.getJSONObject(i)

            sb.append(
                "🚆 ${x.optString("train")}\n"
            )

            sb.append(
                "💺 Class: ${x.optString("class_name")}\n"
            )

            sb.append(
                "🎟 Tickets: ${x.optInt("available")}\n\n"
            )
        }

        append(sb.toString())

        sendTelegram(
            sb.toString()
        )

        nextRoute()
    }

    private fun continueAfterNoTicket() {

        if (!running) return

        if(routeIndex == 5){

            append(
                "First 6 routes completed. 30-second group break..."
            )

            handler.postDelayed(
                {
                    if(running){

                        routeIndex = 6

                        runRoute(6)
                    }
                },
                30000
            )

            return
        }

        if(routeIndex < 5){

            val next =
                routeIndex + 1

            val button = 1

            routeIndex = next

            append(
                "Using suggested Search button #$button for next route."
            )

            clickSuggested(
                button,
                routes[next].first,
                routes[next].second
            )

            return
        }

        if(routeIndex < 11){

            val next =
                routeIndex + 1

            val button = 2

            routeIndex = next

            append(
                "Using suggested Search button #$button for next route."
            )

            clickSuggested(
                button,
                routes[next].first,
                routes[next].second
            )

            return
        }

        nextRoute()
    }

    private fun clickSuggested(
        buttonNumber: Int,
        from: String,
        to: String
    ) {

        val js = """
        (function(){

          function visible(e){

            if(!e) return false;

            const r =
              e.getBoundingClientRect();

            return (
              r.width > 0 &&
              r.height > 0 &&
              getComputedStyle(e).visibility !== 'hidden' &&
              getComputedStyle(e).display !== 'none'
            );
          }

          function text(e){

            return (
              e.innerText ||
              e.textContent ||
              e.value ||
              ''
            )
            .trim()
            .replace(/\s+/g,' ')
            .toUpperCase();
          }

          const bs =
            [
              ...document.querySelectorAll(
                'button,' +
                'input[type="button"],' +
                'input[type="submit"],' +
                '[role="button"]'
              )
            ]
            .filter(function(b){

              if(!visible(b))
                return false;

              const t = text(b);

              return (
                (
                  t === 'SEARCH' ||
                  t === 'SEARCH TRAIN' ||
                  t.includes('SEARCH')
                ) &&
                !b.disabled &&
                b.getAttribute(
                  'aria-disabled'
                ) !== 'true' &&
                !b.classList.contains('disabled') &&
                !b.classList.contains(
                  'mat-button-disabled'
                )
              );
            });

          if(
            bs.length >= ${buttonNumber}
          ){

            const b =
              bs[${buttonNumber - 1}];

            b.scrollIntoView({
              block:'center',
              inline:'center'
            });

            try{
              b.focus();
            }catch(err){}

            try{
              b.click();
            }catch(err){}

            [
              'mousedown',
              'mouseup',
              'click'
            ]
              .forEach(function(type){

                try{

                  b.dispatchEvent(
                    new MouseEvent(
                      type,
                      {
                        bubbles:true,
                        cancelable:true,
                        view:window
                      }
                    )
                  );

                }catch(err){}

              });

            return JSON.stringify({
              result:'CLICKED',
              button:${buttonNumber},
              from:${JSONObject.quote(from)},
              to:${JSONObject.quote(to)}
            });
          }

          return JSON.stringify({
            result:'NO_BUTTON',
            count:bs.length
          });

        })()
        """.trimIndent()

        eval(js) { result ->

            append(
                "Suggested Search result: $result"
            )

            if(result.contains("CLICKED")){

                handler.postDelayed(
                    {
                        waitResult(0)
                    },
                    1000
                )

            }else{

                /*
                 * Do not silently skip the route.
                 * Retry the requested Search button.
                 */
                handler.postDelayed(
                    {
                        if(running){

                            clickSuggested(
                                buttonNumber,
                                from,
                                to
                            )
                        }
                    },
                    1000
                )
            }
        }
    }

    private fun nextRoute() {

        if (!running) return

        if(routeIndex == 5){

            append(
                "First 6 routes completed. 30-second group break..."
            )

            handler.postDelayed(
                {
                    if(running){
                        runRoute(6)
                    }
                },
                30000
            )

            return
        }

        if(routeIndex < 11){

            handler.postDelayed(
                {
                    runRoute(
                        routeIndex + 1
                    )
                },
                500
            )

            return
        }

        append(
            "=== ALL 12 ROUTES CHECKED ==="
        )

        handler.postDelayed(
            {

                if (!running)
                    return@postDelayed

                cycle++

                append(
                    "30-second cycle break completed; restarting from first route."
                )

                runRoute(0)

            },
            30000
        )
    }

    private fun sendTelegram(
        message: String
    ){

        val token =
            tokenEdit.text.toString().trim()

        val chat =
            chatEdit.text.toString().trim()

        if(
            token.isEmpty() ||
            chat.isEmpty()
        ){

            append(
                "Telegram skipped: token/chat ID not set."
            )

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

                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 15000

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

                val ok =
                    c.responseCode in 200..299

                handler.post {

                    append(
                        if(ok)
                            "✓ Telegram notification sent."
                        else
                            "✗ Telegram HTTP ${c.responseCode}"
                    )
                }

                c.disconnect()

            } catch(e: Exception){

                handler.post {

                    append(
                        "✗ Telegram failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun eval(
        js: String,
        cb: (String) -> Unit
    ){

        web.evaluateJavascript(js) { r ->
            cb(r ?: "")
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

            (log.parent as? ScrollView)
                ?.fullScroll(
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

    override fun onDestroy(){

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        executor.shutdownNow()

        super.onDestroy()
    }
}
