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
    private var currentGroup = 1 // 1 = Dhaka routes, 2 = Biman_Bandar routes
    private var routeIndex = 0
    private var targetDate = ""

    // Group 1: Dhaka Destination Routes
    private val dhakaRoutes = listOf(
        "Sylhet" to "Dhaka",
        "Maijgaon" to "Dhaka",
        "Kulaura" to "Dhaka",
        "Shamshernagar" to "Dhaka",
        "Sreemangal" to "Dhaka",
        "Shaistaganj" to "Dhaka"
    )

    // Group 2: Biman_Bandar Destination Routes
    private val bimanRoutes = listOf(
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
        dateEdit.setText(prefs.getString("date", "07-09-2026"))
        tokenEdit.setText(prefs.getString("token", ""))
        chatEdit.setText(prefs.getString("chat", ""))

        setupWebView()

        findViewById<Button>(R.id.dateButton).setOnClickListener { pickDate() }
        findViewById<Button>(R.id.startButton).setOnClickListener { startMonitor() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopMonitor() }

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 55)
        }
    }

    private fun setupWebView() {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.databaseEnabled = true
        web.settings.userAgentString = web.settings.userAgentString + " RailwayMonitorApp/1.0"
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Page loaded
            }
        }
        web.webChromeClient = WebChromeClient()
        web.loadUrl("https://eticket.railway.gov.bd/")
    }

    private fun pickDate() {
        val c = Calendar.getInstance()
        val current = dateEdit.text.toString().split("-")
        if (current.size == 3) {
            try {
                c.set(current[2].toInt(), current[1].toInt() - 1, current[0].toInt())
            } catch (_: Exception) {
            }
        }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                dateEdit.setText(String.format(Locale.US, "%02d-%02d-%04d", d, m + 1, y))
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startMonitor() {
        if (running) return
        targetDate = dateEdit.text.toString().trim()
        if (!Regex("\\d{2}-\\d{2}-\\d{4}").matches(targetDate)) {
            Toast.makeText(this, "Date must be DD-MM-YYYY", Toast.LENGTH_LONG).show()
            return
        }

        getSharedPreferences("settings", MODE_PRIVATE)
            .edit()
            .putString("date", targetDate)
            .putString("token", tokenEdit.text.toString())
            .putString("chat", chatEdit.text.toString())
            .apply()

        running = true
        currentGroup = 1
        routeIndex = 0
        append("=== MONITOR STARTED ===")
        append("Date: $targetDate | Class: S_CHAIR")
        setStatus("Running - Dhaka Routes")

        runCurrentRoute()
    }

    private fun stopMonitor() {
        running = false
        handler.removeCallbacksAndMessages(null)
        setStatus("Stopped")
        append("=== MONITOR STOPPED ===")
    }

    private fun getCurrentRoutes(): List<Pair<String, String>> {
        return if (currentGroup == 1) dhakaRoutes else bimanRoutes
    }

    private fun runCurrentRoute() {
        if (!running) return
        val routes = getCurrentRoutes()
        if (routeIndex !in routes.indices) return

        val (from, to) = routes[routeIndex]
        val groupName = if (currentGroup == 1) "Dhaka" else "Biman_Bandar"
        append("[Group $groupName - Route ${routeIndex + 1}/6] $from → $to")
        setStatus("Searching [$groupName] ${routeIndex + 1}/6: $from → $to")

        web.loadUrl("https://eticket.railway.gov.bd/")
        handler.postDelayed({
            if (!running) return@postDelayed
            fillAndSearch(from, to)
        }, 2200)
    }

    private fun fillAndSearch(from: String, to: String) {
        if (!running) return
        val js = """
            (function(){
                const from = ${JSONObject.quote(from)};
                const to = ${JSONObject.quote(to)};
                function visible(e){ return !!( e && ( e.offsetWidth || e.offsetHeight || e.getClientRects().length ) ); }
                function vis(selector){ return [ ...document.querySelectorAll( selector ) ].find( e => visible(e) ); }
                function fire(e){ [ 'input', 'change', 'blur' ].forEach(type => { e.dispatchEvent( new Event( type, { bubbles:true } ) ); }); }
                
                function setCity( name, ctrl ){
                    const e = vis( 'input[formcontrolname="' + ctrl + '"]' );
                    if(!e) return false;
                    e.focus();
                    const setter = Object.getOwnPropertyDescriptor( HTMLInputElement.prototype, 'value' ).set;
                    setter.call( e, name );
                    fire(e);
                    return true;
                }
                
                function setClass(){
                    const selects = [ ...document.querySelectorAll( 'select' ) ];
                    selects.forEach(s => {
                        [ ...s.options ].forEach(o => {
                            if( o.value === 'S_CHAIR' || ( o.textContent || '' ).trim() === 'S_CHAIR' ){
                                o.selected = true;
                                fire(s);
                            }
                        });
                    });
                    const txt = [ ...document.querySelectorAll( '*' ) ].find( e => e.childElementCount === 0 && ( e.textContent || '' ).trim() === 'S_CHAIR' && visible(e) );
                    if(txt) txt.click();
                }

                function findDateField(){
                    const inputs = [ ...document.querySelectorAll( 'input' ) ].filter( e => visible(e) );
                    return inputs.find(e => {
                        const placeholder = ( e.getAttribute( 'placeholder' ) || '' ).trim().toLowerCase();
                        const value = ( e.value || '' ).trim().toLowerCase();
                        const aria = ( e.getAttribute( 'aria-label' ) || '' ).trim().toLowerCase();
                        return ( placeholder === 'pick a date' || value === 'pick a date' || aria === 'pick a date' );
                    }) || vis( 'input[formcontrolname="doj"]' ) || vis( 'input#doj' );
                }

                [ ...document.querySelectorAll( 'button,[role="button"]' ) ].forEach(e => {
                    const text = ( e.innerText || e.textContent || '' ).trim();
                    if( text === 'I AGREE' ){ e.click(); }
                });

                const fromOK = setCity( from, 'fromcity' );
                const toOK = setCity( to, 'tocity' );
                setClass();

                if( !fromOK || !toOK ) return 'WAIT_CITY';

                const dateField = findDateField();
                if(!dateField) return 'WAIT_DATE_FIELD';

                dateField.focus();
                dateField.click();
                return 'DATE_FIELD_CLICKED';
            })()
        """.trimIndent()

        eval(js) { result ->
            val clean = result.trim('"').replace("\\\"", "\"")
            handler.postDelayed({
                if (!running) return@postDelayed
                selectDateFromCalendar(from, to, 0)
            }, 700)
        }
    }

    private fun selectDateFromCalendar(from: String, to: String, attempt: Int) {
        if (!running) return
        val js = """
            (function(){
                const target = ${JSONObject.quote(targetDate)};
                function visible(e){ return !!( e && ( e.offsetWidth || e.offsetHeight || e.getClientRects().length ) ); }
                
                const calendars = [ ...document.querySelectorAll( '.ui-datepicker,.mat-datepicker-content,[role="dialog"],[role="grid"]' ) ].filter( e => visible(e) );
                if( calendars.length === 0 ) return 'CALENDAR_NOT_OPEN';

                const p = target.split('-');
                const wantedDay = parseInt( p[0], 10 );
                const wantedMonth = parseInt( p[1], 10 ) - 1;
                const wantedYear = parseInt( p[2], 10 );

                const ui = calendars.find( e => e.matches( '.ui-datepicker' ) || e.querySelector( 'td[data-handler="selectDay"]' ) );
                if(ui){
                    const cells = [ ...ui.querySelectorAll( 'td[data-handler="selectDay"]' ) ];
                    for( const td of cells ){
                        const y = parseInt( td.getAttribute( 'data-year' ), 10 );
                        const m = parseInt( td.getAttribute( 'data-month' ), 10 );
                        const a = td.querySelector( 'a.ui-state-default' );
                        if( a && y === wantedYear && m === wantedMonth && ( a.textContent || '' ).trim() === String(wantedDay) ){
                            a.click();
                            return 'DATE_CLICKED';
                        }
                    }

                    const monthSelect = ui.querySelector( 'select.ui-datepicker-month' );
                    const yearSelect = ui.querySelector( 'select.ui-datepicker-year' );
                    let currentMonth = -1;
                    let currentYear = -1;

                    if( monthSelect && yearSelect ){
                        currentMonth = parseInt( monthSelect.value, 10 );
                        currentYear = parseInt( yearSelect.value, 10 );
                    } else {
                        const title = ui.querySelector( '.ui-datepicker-title' );
                        if(title){
                            const text = title.innerText || '';
                            const ym = text.match( /\d{4}/ );
                            if(ym){
                                currentYear = parseInt( ym[0], 10 );
                                const months = [ 'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December' ];
                                for( let i = 0; i < months.length; i++ ){
                                    if( text.toLowerCase().includes( months[i].toLowerCase() ) ){
                                        currentMonth = i;
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    if( currentMonth >= 0 && currentYear >= 0 ){
                        const currentIndex = currentYear * 12 + currentMonth;
                        const targetIndex = wantedYear * 12 + wantedMonth;
                        let nav = null;
                        if( targetIndex > currentIndex ){
                            nav = ui.querySelector( '.ui-datepicker-next:not(.ui-state-disabled)' );
                        } else if( targetIndex < currentIndex ){
                            nav = ui.querySelector( '.ui-datepicker-prev:not(.ui-state-disabled)' );
                        }
                        if(nav){
                            nav.click();
                            return 'CALENDAR_MOVED';
                        }
                    }
                    return 'DATE_NOT_VISIBLE';
                }

                const generic = calendars[0];
                const dayElements = [ ...generic.querySelectorAll( '[role="gridcell"],button,td' ) ].filter( e => visible(e) );
                for( const el of dayElements ){
                    const text = ( el.innerText || el.textContent || '' ).trim();
                    const aria = ( el.getAttribute( 'aria-label' ) || '' ).trim();
                    const dataDate = ( el.getAttribute( 'data-date' ) || '' ).trim();
                    if( aria.includes(target) || dataDate === target ){
                        el.click();
                        return 'DATE_CLICKED';
                    }
                    if( text === String(wantedDay) ){
                        el.click();
                        return 'DATE_CLICKED';
                    }
                }
                return 'DATE_NOT_FOUND';
            })()
        """.trimIndent()

        eval(js) { result ->
            val clean = result.trim('"').replace("\\\"", "\"")
            when (clean) {
                "DATE_CLICKED" -> {
                    append("✓ Date selected: $targetDate")
                    waitForSearchEnabled(from, to, 0)
                }
                "CALENDAR_MOVED" -> {
                    handler.postDelayed({
                        if (running) selectDateFromCalendar(from, to, attempt + 1)
                    }, 400)
                }
                else -> {
                    handler.postDelayed({
                        if (running) reopenDateCalendar(from, to)
                    }, 700)
                }
            }
        }
    }

    private fun reopenDateCalendar(from: String, to: String) {
        if (!running) return
        val js = """
            (function(){
                function visible(e){ return !!( e && ( e.offsetWidth || e.offsetHeight || e.getClientRects().length ) ); }
                const inputs = [ ...document.querySelectorAll( 'input' ) ].filter( e => visible(e) );
                const dateField = inputs.find(e => {
                    const placeholder = ( e.getAttribute( 'placeholder' ) || '' ).trim().toLowerCase();
                    const value = ( e.value || '' ).trim().toLowerCase();
                    return ( placeholder === 'pick a date' || value === 'pick a date' );
                }) || inputs.find( e => e.matches( 'input[formcontrolname="doj"]' ) ) || document.querySelector( 'input#doj' );
                if(!dateField) return 'NO_DATE_FIELD';
                dateField.focus();
                dateField.click();
                return 'DATE_FIELD_CLICKED';
            })()
        """.trimIndent()
        eval(js) {
            handler.postDelayed({
                if (running) selectDateFromCalendar(from, to, 0)
            }, 500)
        }
    }

    private fun waitForSearchEnabled(from: String, to: String, attempt: Int) {
        if (!running) return
        val js = """
            (function(){
                function visible(e){ return !!( e && ( e.offsetWidth || e.offsetHeight || e.getClientRects().length ) ); }
                const buttons = [ ...document.querySelectorAll( 'button' ) ].filter( b => visible(b) && ( b.innerText || '' ).trim() === 'Search' );
                if( buttons.length > 0 ){
                    const enabled = buttons.find( b => !b.disabled );
                    if( enabled ) return 'READY';
                }
                return 'WAIT_SEARCH';
            })()
        """.trimIndent()

        eval(js) { result ->
            val clean = result.trim('"').replace("\\\"", "\"")
            if (clean == "READY") {
                append("✓ Search button is enabled.")
                handler.postDelayed({ if (running) clickFirstSearch() }, 500)
            } else {
                if (attempt > 40) {
                    append("Force clicking search button...")
                    forceClickSearch()
                    return@eval
                }
                if (attempt % 5 == 0) {
                    append("Waiting for Search button to enable...")
                }
                handler.postDelayed({
                    if (running) { waitForSearchEnabled(from, to, attempt + 1) }
                }, 500)
            }
        }
    }

    private fun clickFirstSearch() {
        if (!running) return
        val js = """
            (function(){
                const buttons = [ ...document.querySelectorAll( 'button' ) ].filter( b => ( b.innerText || '' ).trim() === 'Search' );
                const b = buttons.find( x => !x.disabled ) || buttons[0];
                if( b ){
                    b.click();
                    return 'CLICKED';
                }
                return 'NOT_FOUND';
            })()
        """.trimIndent()

        eval(js) { result ->
            val clean = result.trim('"')
            if (clean == "CLICKED") {
                append("✓ Main Search clicked.")
                waitResult(0)
            } else {
                forceClickSearch()
            }
        }
    }

    private fun forceClickSearch() {
        if (!running) return
        val js = """
            (function(){
                const buttons = [ ...document.querySelectorAll( 'button' ) ];
                const b = buttons.find( x => (x.innerText || '').trim() === 'Search' );
                if(b){
                    b.removeAttribute('disabled');
                    b.click();
                    return 'FORCE_CLICKED';
                }
                return 'FAIL';
            })()
        """.trimIndent()

        eval(js) { result ->
            val clean = result.trim('"')
            if (clean == "FORCE_CLICKED") {
                append("✓ Force clicked Search button.")
                waitResult(0)
            } else {
                append("Search button not found, retrying route...")
                handler.postDelayed({
                    if (running) runCurrentRoute()
                }, 1000)
            }
        }
    }

    private fun waitResult(elapsed: Int) {
        if (!running) return
        eval("location.href") { raw ->
            val url = raw.trim('"')
            if (url.contains("/booking/train/search")) {
                append("✓ Result page detected. Checking availability...")
                handler.postDelayed({
                    if (running) checkResult(0)
                }, 2000)
            } else if (elapsed >= 90000) {
                append("Result timeout. Moving to next route.")
                moveToNextRouteOrGroup()
            } else {
                handler.postDelayed({
                    if (running) waitResult(elapsed + 1000)
                }, 1000)
            }
        }
    }

    private fun checkResult(elapsed: Int) {
        if (!running) return
        val js = """
            (function(){
                function normalize(s){ return ( s || '' ).replace( /\s+/g, ' ' ).trim(); }
                const body = normalize( document.body?.innerText || '' ).toUpperCase();

                const labels = [ ...document.querySelectorAll( '*' ) ].filter( e => normalize( e.innerText || '' ) === 'Available Tickets(Counter + Online)' );
                const results = [];

                labels.forEach( label => {
                    const card = label.closest( '.single-seat-class' );
                    const trip = label.closest( '.single-trip-wrapper' );
                    if(!card) return;
                    const lines = ( card.innerText || '' ).split( /\n+/ ).map( x => x.trim() ).filter( Boolean );
                    const index = lines.findIndex( x => normalize(x) === 'Available Tickets(Counter + Online)' );
                    if( index < 0 || index + 1 >= lines.length ) return;

                    const nextLine = lines[ index + 1 ];
                    const match = nextLine.match( /^\s*(\d+)\s*$/ );
                    if(!match) return;
                    const available = parseInt( match[1], 10 );
                    let train = 'UNKNOWN TRAIN';
                    if(trip){
                        const tripLines = ( trip.innerText || '' ).split( /\n+/ ).map( x => x.trim() ).filter( Boolean );
                        if( tripLines.length ) train = tripLines[0];
                    }

                    let className = 'S_CHAIR';
                    for( const line of lines ){
                        const upper = line.toUpperCase();
                        if( upper.includes( 'S_CHAIR' ) || upper.includes( 'SHOVAN CHAIR' ) ){
                            className = line;
                            break;
                        }
                    }

                    if( available > 0 ){
                        results.push({ train: train, class_name: className, available: available });
                    }
                });

                if( results.length > 0 ){
                    return JSON.stringify({ type: 'AVAILABLE', items: results });
                }

                if( body.includes( 'NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE' ) ){
                    return JSON.stringify({ type: 'NO_TICKET' });
                }

                return JSON.stringify({ type: 'WAIT' });
            })()
        """.trimIndent()

        eval(js) { raw ->
            try {
                val clean = raw.trim('"').replace("\\\"", "\"")
                val o = JSONObject(clean)
                when (o.optString("type")) {
                    "NO_TICKET" -> {
                        val (from, to) = getCurrentRoutes()[routeIndex]
                        append("✗ No ticket for $from → $to")
                        moveToNextRouteOrGroup()
                    }
                    "AVAILABLE" -> {
                        val arr = o.getJSONArray("items")
                        handleAvailable(arr)
                    }
                    else -> {
                        if (elapsed >= 30000) {
                            moveToNextRouteOrGroup()
                        } else {
                            handler.postDelayed({
                                if (running) checkResult(elapsed + 1000)
                            }, 1000)
                        }
                    }
                }
            } catch (e: Exception) {
                handler.postDelayed({
                    if (running) checkResult(elapsed + 1000)
                }, 1000)
            }
        }
    }

    private fun handleAvailable(arr: JSONArray) {
        val (from, to) = getCurrentRoutes()[routeIndex]
        val sb = StringBuilder("🎫 BANGLADESH RAILWAY TICKET AVAILABLE!\n\n" +
                "Route: $from → $to\n" +
                "Date: $targetDate\n\n")
        var positiveCount = 0

        for (i in 0 until arr.length()) {
            val x = arr.getJSONObject(i)
            val count = x.optInt("available", 0)
            if (count <= 0) continue
            positiveCount++
            sb.append("🚆 Train: " + x.optString("train") + "\n")
            sb.append("💺 Class: " + x.optString("class_name") + "\n")
            sb.append("🎟 Available: " + count + "\n\n")
        }

        if (positiveCount == 0) {
            moveToNextRouteOrGroup()
            return
        }

        val message = sb.toString()
        append(message)
        sendTelegram(message) {
            if (!running) return@sendTelegram
            moveToNextRouteOrGroup()
        }
    }

    private fun moveToNextRouteOrGroup() {
        if (!running) return
        routeIndex++
        val routes = getCurrentRoutes()

        if (routeIndex < routes.size) {
            runCurrentRoute()
        } else {
            if (currentGroup == 1) {
                append("=== GROUP 1 (Dhaka Routes) COMPLETED ===")
                append("Waiting 13 seconds before starting Group 2 (Biman_Bandar)...")
                setStatus("13s break (Group 2 incoming)")
                handler.postDelayed({
                    if (!running) return@postDelayed
                    currentGroup = 2
                    routeIndex = 0
                    append("=== STARTING GROUP 2 (Biman_Bandar) ===")
                    runCurrentRoute()
                }, 13000L)
            } else {
                append("=== GROUP 2 (Biman_Bandar) COMPLETED ===")
                append("Waiting 14 seconds before restarting full cycle...")
                setStatus("14s break (Restarting Cycle)")
                handler.postDelayed({
                    if (!running) return@postDelayed
                    currentGroup = 1
                    routeIndex = 0
                    append("=== RESTARTING FULL CYCLE FROM ROUTE 1 ===")
                    runCurrentRoute()
                }, 14000L)
            }
        }
    }

    private fun sendTelegram(message: String, onDone: () -> Unit) {
        val token = tokenEdit.text.toString().trim()
        val chat = chatEdit.text.toString().trim()
        if (token.isEmpty() || chat.isEmpty()) {
            append("Telegram skipped: credentials missing.")
            handler.post { onDone() }
            return
        }

        executor.execute {
            try {
                val url = URL("https://api.telegram.org/bot$token/sendMessage")
                val c = url.openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15000
                c.readTimeout = 15000
                val data = "chat_id=" + URLEncoder.encode(chat, "UTF-8") +
                        "&text=" + URLEncoder.encode(message, "UTF-8")
                c.outputStream.use { it.write(data.toByteArray()) }
                val response = c.responseCode
                val ok = response in 200..299
                c.disconnect()
                handler.post {
                    if (ok) append("✓ Telegram notification sent.")
                    else append("✗ Telegram HTTP $response")
                    onDone()
                }
            } catch (e: Exception) {
                handler.post {
                    append("✗ Telegram failed: ${e.message}")
                    onDone()
                }
            }
        }
    }

    private fun eval(js: String, cb: (String) -> Unit) {
        web.evaluateJavascript(js) { result ->
            cb(result ?: "")
        }
    }

    private fun append(s: String) {
        runOnUiThread {
            log.append("\n" + s)
            (log.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setStatus(s: String) {
        runOnUiThread {
            status.text = "Status: $s"
        }
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }
}
