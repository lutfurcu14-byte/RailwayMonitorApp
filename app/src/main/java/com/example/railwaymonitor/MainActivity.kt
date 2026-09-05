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
    private var phase = 0
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
        phase = 0

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
                fillAndSearch(from, to)
            },
            2200
        )
    }

    private fun fillAndSearch(
        from: String,
        to: String
    ) {

        if (!running) return

        val js = """
            (function(){
              const from=${JSONObject.quote(from)}, 
                    to=${JSONObject.quote(to)};
              
              const date=${JSONObject.quote(toIso(targetDate))};

              function vis(sel){
                return [...document.querySelectorAll(sel)]
                  .find(e =>
                    e.offsetWidth ||
                    e.offsetHeight ||
                    e.getClientRects().length
                  );
              }

              function fire(e){
                ['input','change','blur'].forEach(t =>
                  e.dispatchEvent(
                    new Event(t,{bubbles:true})
                  )
                );
              }

              function city(name, ctrl){

                const e =
                  vis('input[formcontrolname="'+ctrl+'"]');

                if(!e) return false;

                e.focus();

                const s =
                  Object.getOwnPropertyDescriptor(
                    HTMLInputElement.prototype,
                    'value'
                  ).set;

                s.call(e,name);

                fire(e);

                return true;
              }

              function dateSet(){

                const v =
                  vis('input.datepicker.hasDatepicker') ||
                  vis('input#doj');

                if(v){

                  const s =
                    Object.getOwnPropertyDescriptor(
                      HTMLInputElement.prototype,
                      'value'
                    ).set;

                  s.call(v,date);

                  fire(v);
                }

                const h =
                  document.querySelector(
                    'input[type="hidden"][formcontrolname="doj"]'
                  );

                if(h){

                  const s =
                    Object.getOwnPropertyDescriptor(
                      HTMLInputElement.prototype,
                      'value'
                    ).set;

                  s.call(h,date);

                  fire(h);
                }

                return true;
              }

              [
                ...document.querySelectorAll(
                  'button,[role="button"],select,option'
                )
              ].forEach(e => {

                if(
                  (e.innerText ||
                   e.textContent ||
                   '').trim() === 'I AGREE'
                ){
                  e.click();
                }

              });

              city(from,'fromcity');
              city(to,'tocity');
              dateSet();

              const sels =
                [...document.querySelectorAll('select')];

              sels.forEach(s =>
                [...s.options].forEach(o => {

                  if(
                    o.value === 'S_CHAIR' ||
                    o.textContent.trim() === 'S_CHAIR'
                  ){
                    o.selected = true;
                    fire(s);
                  }

                })
              );

              const txt =
                [...document.querySelectorAll('*')]
                .find(e =>
                  e.childElementCount === 0 &&
                  (e.textContent || '').trim() === 'S_CHAIR' &&
                  (e.offsetWidth || e.offsetHeight)
                );

              if(txt) txt.click();

              return [...document.querySelectorAll('button')]
                .find(b =>
                  (b.innerText || '').trim() === 'Search' &&
                  !b.disabled
                )
                ? 'READY'
                : 'WAIT';

            })()
        """.trimIndent()

        eval(js) {

            handler.postDelayed(
                {
                    clickFirstSearch()
                },
                800
            )
        }
    }

    private fun clickFirstSearch() {

        if (!running) return

        eval(
            """
                (function(){

                  const buttons =
                    [...document.querySelectorAll('button')]
                    .filter(b =>
                      (b.innerText || '').trim() === 'Search' &&
                      !b.disabled &&
                      b.offsetWidth > 0 &&
                      b.offsetHeight > 0
                    );

                  if(buttons.length > 0){

                    const b = buttons[0];

                    b.scrollIntoView({
                      behavior: 'auto',
                      block: 'center'
                    });

                    b.focus();

                    try {
                      b.click();
                    } catch(e) {

                      ['mousedown','mouseup','click'].forEach(type => {
                        b.dispatchEvent(
                          new MouseEvent(type, {
                            bubbles: true,
                            cancelable: true,
                            view: window
                          })
                        );
                      });

                    }

                    return 'CLICKED';
                  }

                  return 'WAIT';

                })()
            """.trimIndent()
        ) { result ->

            if (!running) return@eval

            if (result.contains("CLICKED")) {

                append(
                    "Search clicked; waiting for Railway result..."
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

                val o =
                    JSONObject(
                        raw
                            .trim('"')
                            .replace("\\\"", "\"")
                    )

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

              const bs =
                [...document.querySelectorAll('button')]
                .filter(b =>
                  (b.innerText || '').trim() === 'Search' &&
                  !b.disabled
                );

              if(
                bs.length >= $buttonNumber
              ){

                bs[$buttonNumber-1].click();

                return 'CLICKED';
              }

              return 'NO_BUTTON';

            })()
        """.trimIndent()

        eval(js) { result ->

            append(
                "Suggested Search result: $result"
            )

            handler.postDelayed(
                {
                    waitResult(0)
                },
                1000
            )
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
