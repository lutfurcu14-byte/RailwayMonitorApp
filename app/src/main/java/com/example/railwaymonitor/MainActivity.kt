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

    private val executor =
        Executors.newSingleThreadExecutor()

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

        val c =
            Calendar.getInstance()

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
            "Date: $targetDate | Class: S_CHAIR | Routes: 12"
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

        if (!running) return

        routeIndex = index

        val (from, to) =
            routes[index]

        append(
            "[${index + 1}/12] $from → $to"
        )

        setStatus(
            "Searching ${index + 1}/12: $from → $to"
        )

        web.loadUrl(
            "https://eticket.railway.gov.bd/"
        )

        waitForHomeAndFill(
            from,
            to,
            0
        )
    }

    private fun waitForHomeAndFill(
        from: String,
        to: String,
        elapsed: Int
    ) {

        if (!running) return

        eval("location.href") { raw ->

            val url =
                raw.trim('"')

            if (
                url.contains(
                    "eticket.railway.gov.bd"
                ) &&
                !url.contains(
                    "/booking/train/search"
                )
            ) {

                if (elapsed >= 2500) {

                    fillAndSearch(
                        from,
                        to
                    )

                } else {

                    handler.postDelayed(
                        {
                            waitForHomeAndFill(
                                from,
                                to,
                                elapsed + 500
                            )
                        },
                        500
                    )
                }

            } else {

                handler.postDelayed(
                    {
                        waitForHomeAndFill(
                            from,
                            to,
                            elapsed + 500
                        )
                    },
                    500
                )
            }
        }
    }

    private fun fillAndSearch(
        from: String,
        to: String
    ) {

        if (!running) return

        val isoDate =
            toIso(targetDate)

        val js = """
        (function(){

            const FROM =
                ${JSONObject.quote(from)};

            const TO =
                ${JSONObject.quote(to)};

            const TARGET =
                ${JSONObject.quote(isoDate)};

            function visible(e){
                return e &&
                    (
                        e.offsetWidth ||
                        e.offsetHeight ||
                        e.getClientRects().length
                    );
            }

            function nativeValue(e,value){

                try{

                    const setter =
                        Object.getOwnPropertyDescriptor(
                            HTMLInputElement.prototype,
                            'value'
                        ).set;

                    setter.call(
                        e,
                        value
                    );

                }catch(err){

                    try{
                        e.value = value;
                    }catch(x){}
                }
            }

            function fire(e){

                if(!e) return;

                [
                    'input',
                    'change',
                    'blur'
                ].forEach(type => {

                    try{

                        e.dispatchEvent(
                            new Event(
                                type,
                                {
                                    bubbles:true
                                }
                            )
                        );

                    }catch(err){}
                });
            }

            function clickAgree(){

                [
                    ...document.querySelectorAll(
                        'button,[role="button"],a'
                    )
                ].forEach(e => {

                    const text =
                        (
                            e.innerText ||
                            e.textContent ||
                            ''
                        )
                        .trim()
                        .toUpperCase();

                    if(
                        text === 'I AGREE' &&
                        visible(e)
                    ){

                        try{
                            e.click();
                        }catch(err){}
                    }
                });
            }

            function city(ctrl,value){

                const inputs =
                    [
                        ...document.querySelectorAll(
                            'input[formcontrolname="' +
                            ctrl +
                            '"]'
                        )
                    ].filter(visible);

                if(!inputs.length)
                    return false;

                const input =
                    inputs[0];

                input.focus();

                nativeValue(
                    input,
                    ''
                );

                fire(input);

                nativeValue(
                    input,
                    value
                );

                fire(input);

                return true;
            }

            function dateSet(){

                const visibleDate =
                    document.querySelector(
                        'input[formcontrolname="doj"]'
                    ) ||
                    document.querySelector(
                        '#doj'
                    ) ||
                    document.querySelector(
                        'input.datepicker.hasDatepicker'
                    );

                const hidden =
                    document.querySelector(
                        'input[type="hidden"][formcontrolname="doj"]'
                    );

                const parts =
                    TARGET.split('-');

                const y =
                    parseInt(parts[0],10);

                const m =
                    parseInt(parts[1],10)-1;

                const d =
                    parseInt(parts[2],10);

                const months = [
                    'Jan','Feb','Mar','Apr',
                    'May','Jun','Jul','Aug',
                    'Sep','Oct','Nov','Dec'
                ];

                const displayDate =
                    String(d).padStart(2,'0') +
                    '-' +
                    months[m] +
                    '-' +
                    y;

                try{

                    if(
                        window.jQuery &&
                        visibleDate &&
                        jQuery.fn.datepicker
                    ){

                        const dt =
                            new Date(y,m,d);

                        jQuery(visibleDate)
                            .datepicker(
                                'setDate',
                                dt
                            );

                        jQuery(visibleDate)
                            .val(
                                displayDate
                            );

                        jQuery(visibleDate)
                            .trigger(
                                'change'
                            );
                    }

                }catch(err){}

                if(visibleDate){

                    nativeValue(
                        visibleDate,
                        displayDate
                    );

                    fire(
                        visibleDate
                    );
                }

                if(hidden){

                    nativeValue(
                        hidden,
                        TARGET
                    );

                    fire(
                        hidden
                    );
                }

                return true;
            }

            clickAgree();

            city(
                'fromcity',
                FROM
            );

            city(
                'tocity',
                TO
            );

            dateSet();

            [
                ...document.querySelectorAll(
                    'select'
                )
            ].forEach(select => {

                [
                    ...select.options
                ].forEach(option => {

                    if(
                        option.value === 'S_CHAIR' ||
                        option.textContent
                            .trim()
                            .toUpperCase() ===
                            'S_CHAIR'
                    ){

                        option.selected = true;

                        fire(select);
                    }
                });
            });

            [
                ...document.querySelectorAll('*')
            ].forEach(e => {

                if(
                    !visible(e) ||
                    e.childElementCount !== 0
                ) return;

                const text =
                    (
                        e.textContent ||
                        ''
                    )
                    .trim()
                    .toUpperCase();

                if(text === 'S_CHAIR'){

                    try{
                        e.click();
                    }catch(err){}
                }
            });

            return 'FORM_PREPARED';

        })()
        """.trimIndent()

        eval(js) {

            handler.postDelayed(
                {
                    forceFinalDateSyncAndSearch()
                },
                300
            )
        }
    }

    private fun forceFinalDateSyncAndSearch() {

        if (!running) return

        val isoDate =
            toIso(targetDate)

        val js = """
        (function(){

            const TARGET =
                ${JSONObject.quote(isoDate)};

            function nativeValue(
                element,
                value
            ){

                if(!element) return;

                try{

                    const setter =
                        Object.getOwnPropertyDescriptor(
                            HTMLInputElement.prototype,
                            'value'
                        ).set;

                    setter.call(
                        element,
                        value
                    );

                }catch(err){

                    try{
                        element.value = value;
                    }catch(x){}
                }
            }

            function fire(element){

                if(!element) return;

                [
                    'input',
                    'change',
                    'blur'
                ].forEach(type => {

                    try{

                        element.dispatchEvent(
                            new Event(
                                type,
                                {
                                    bubbles:true
                                }
                            )
                        );

                    }catch(err){}
                });
            }

            const parts =
                TARGET.split('-');

            if(parts.length !== 3){
                return 'BAD_DATE';
            }

            const y =
                parseInt(parts[0],10);

            const m =
                parseInt(parts[1],10)-1;

            const d =
                parseInt(parts[2],10);

            const months = [
                'Jan','Feb','Mar','Apr',
                'May','Jun','Jul','Aug',
                'Sep','Oct','Nov','Dec'
            ];

            const displayDate =
                String(d).padStart(2,'0') +
                '-' +
                months[m] +
                '-' +
                y;

            const visibleDate =
                document.querySelector(
                    'input[formcontrolname="doj"]'
                ) ||
                document.querySelector(
                    '#doj'
                ) ||
                document.querySelector(
                    'input.datepicker.hasDatepicker'
                );

            const hiddenDate =
                document.querySelector(
                    'input[type="hidden"][formcontrolname="doj"]'
                );

            /*
             * jQuery UI Datepicker
             */
            try{

                if(
                    window.jQuery &&
                    visibleDate &&
                    jQuery.fn.datepicker
                ){

                    const dt =
                        new Date(y,m,d);

                    jQuery(visibleDate)
                        .datepicker(
                            'setDate',
                            dt
                        );

                    jQuery(visibleDate)
                        .val(
                            displayDate
                        );

                    jQuery(visibleDate)
                        .trigger(
                            'change'
                        );
                }

            }catch(err){}

            /*
             * Visible input
             */
            if(visibleDate){

                nativeValue(
                    visibleDate,
                    displayDate
                );

                fire(
                    visibleDate
                );
            }

            /*
             * Hidden input
             */
            if(hiddenDate){

                nativeValue(
                    hiddenDate,
                    TARGET
                );

                fire(
                    hiddenDate
                );
            }

            /*
             * Find Angular context
             */
            const roots = [];

            [
                visibleDate,
                hiddenDate
            ].forEach(input => {

                let node = input;

                for(
                    let i=0;
                    node && i<20;
                    i++,
                    node=node.parentElement
                ){

                    try{

                        if(node.__ngContext__){

                            roots.push(
                                node.__ngContext__
                            );
                        }

                    }catch(err){}
                }
            });

            const seen =
                new WeakSet();

            const queue =
                roots.map(r => ({
                    obj:r,
                    depth:0
                }));

            let controlValue = null;

            while(queue.length){

                const item =
                    queue.shift();

                const obj =
                    item.obj;

                if(
                    !obj ||
                    (
                        typeof obj !== 'object' &&
                        typeof obj !== 'function'
                    )
                ){
                    continue;
                }

                if(seen.has(obj)){
                    continue;
                }

                seen.add(obj);

                try{

                    if(
                        obj.controls &&
                        obj.controls.doj &&
                        typeof
                            obj.controls.doj.setValue ===
                            'function'
                    ){

                        const control =
                            obj.controls.doj;

                        control.setValue(
                            TARGET
                        );

                        control.markAsDirty();

                        control.markAsTouched();

                        control.updateValueAndValidity();

                        controlValue =
                            control.value;

                        break;
                    }

                }catch(err){}

                if(item.depth >= 8){
                    continue;
                }

                let keys = [];

                try{

                    keys =
                        Object.keys(obj)
                            .slice(0,150);

                }catch(err){}

                for(const key of keys){

                    if(
                        key === 'nativeElement' ||
                        key === 'renderer' ||
                        key === 'elementRef' ||
                        key === 'ownerDocument' ||
                        key === 'parentNode'
                    ){
                        continue;
                    }

                    let value;

                    try{
                        value = obj[key];
                    }catch(err){
                        continue;
                    }

                    if(
                        value &&
                        (
                            typeof value === 'object' ||
                            typeof value === 'function'
                        )
                    ){

                        queue.push({
                            obj:value,
                            depth:item.depth+1
                        });
                    }
                }
            }

            return JSON.stringify({

                target:
                    TARGET,

                visible:
                    visibleDate ?
                    visibleDate.value :
                    null,

                hidden:
                    hiddenDate ?
                    hiddenDate.value :
                    null,

                controlValue:
                    controlValue

            });

        })()
        """.trimIndent()

        eval(js) { result ->

            append(
                "Final DOJ sync: $result"
            )

            val clean =
                result
                    .trim('"')
                    .replace(
                        "\\\"",
                        "\""
                    )

            val dateConfirmed =
                clean.contains(
                    "\"controlValue\":\"$isoDate\""
                )

            if(dateConfirmed){

                append(
                    "✓ Target date confirmed: $targetDate"
                )

                handler.postDelayed(
                    {
                        clickFirstSearch()
                    },
                    200
                )

            }else{

                handler.postDelayed(
                    {
                        forceFinalDateSyncAndSearch()
                    },
                    500
                )
            }
        }
    }

    private fun clickFirstSearch() {

        if (!running) return

        eval(
            """
            (function(){

                const buttons =
                    [
                        ...document.querySelectorAll(
                            'button'
                        )
                    ].filter(b => {

                        const text =
                            (
                                b.innerText ||
                                b.textContent ||
                                ''
                            ).trim();

                        return (
                            text === 'Search' &&
                            !b.disabled &&
                            (
                                b.offsetWidth ||
                                b.offsetHeight ||
                                b.getClientRects().length
                            )
                        );
                    });

                if(!buttons.length)
                    return 'NO_BUTTON';

                const b =
                    buttons[0];

                try{

                    b.scrollIntoView({
                        block:'center',
                        inline:'center'
                    });

                }catch(err){}

                try{

                    b.click();

                    return 'CLICKED';

                }catch(err){

                    try{

                        b.dispatchEvent(
                            new MouseEvent(
                                'click',
                                {
                                    bubbles:true,
                                    cancelable:true,
                                    view:window
                                }
                            )
                        );

                        return 'DISPATCHED';

                    }catch(x){

                        return 'ERROR';
                    }
                }

            })()
            """.trimIndent()
        ) { result ->

            append(
                "Search button: $result"
            )

            if(
                result.contains("CLICKED") ||
                result.contains("DISPATCHED")
            ){

                append(
                    "Search clicked; waiting for Railway result..."
                )

                waitResult(0)

            }else{

                handler.postDelayed(
                    {
                        retryFirstSearch(0)
                    },
                    500
                )
            }
        }
    }

    private fun retryFirstSearch(
        elapsed: Int
    ) {

        if (!running) return

        if(elapsed >= 30000){

            append(
                "Search button timeout; restarting current group route."
            )

            runRoute(
                routeIndex
            )

            return
        }

        eval(
            """
            (function(){

                const b =
                    [
                        ...document.querySelectorAll(
                            'button'
                        )
                    ].find(x => {

                        const text =
                            (
                                x.innerText ||
                                x.textContent ||
                                ''
                            ).trim();

                        return (
                            text === 'Search' &&
                            !x.disabled &&
                            (
                                x.offsetWidth ||
                                x.offsetHeight ||
                                x.getClientRects().length
                            )
                        );
                    });

                if(!b)
                    return 'NO';

                try{

                    b.scrollIntoView({
                        block:'center',
                        inline:'center'
                    });

                }catch(e){}

                try{

                    b.click();

                    return 'CLICKED';

                }catch(e){

                    try{

                        b.dispatchEvent(
                            new MouseEvent(
                                'click',
                                {
                                    bubbles:true,
                                    cancelable:true,
                                    view:window
                                }
                            )
                        );

                        return 'DISPATCHED';

                    }catch(x){

                        return 'ERROR';
                    }
                }

            })()
            """.trimIndent()
        ) { result ->

            if(
                result.contains("CLICKED") ||
                result.contains("DISPATCHED")
            ){

                append(
                    "Search clicked successfully."
                )

                waitResult(0)

            }else{

                handler.postDelayed(
                    {
                        retryFirstSearch(
                            elapsed + 500
                        )
                    },
                    500
                )
            }
        }
    }

    private fun waitResult(
        elapsed: Int
    ) {

        if (!running) return

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

                checkResult(0)

            }else if(
                elapsed >= 90000
            ){

                append(
                    "Result page timeout; moving safely to next route."
                )

                nextRoute()

            }else{

                handler.postDelayed(
                    {
                        waitResult(
                            elapsed + 500
                        )
                    },
                    500
                )
            }
        }
    }

    private fun checkResult(
        elapsed: Int
    ) {

        if (!running) return

        val js = """
        (function(){

            const body =
                (
                    document.body?.innerText ||
                    ''
                )
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
                .querySelectorAll(
                    '.available-text'
                )
                .forEach(label => {

                    const card =
                        label.closest(
                            '.single-seat-class'
                        );

                    const trip =
                        label.closest(
                            '.single-trip-wrapper'
                        );

                    if(!card || !trip)
                        return;

                    const tripLines =
                        (trip.innerText || '')
                        .split(/\n+/)
                        .map(
                            x => x.trim()
                        )
                        .filter(Boolean);

                    const cardLines =
                        (card.innerText || '')
                        .split(/\n+/)
                        .map(
                            x => x.trim()
                        )
                        .filter(Boolean);

                    const labelLines =
                        (label.innerText || '')
                        .split(/\n+/)
                        .map(
                            x => x.trim()
                        )
                        .filter(Boolean);

                    let n = 0;

                    if(labelLines.length){

                        n =
                            parseInt(
                                labelLines[
                                    labelLines.length - 1
                                ],
                                10
                            );
                    }

                    if(
                        !Number.isNaN(n) &&
                        n > 0
                    ){

                        out.push({

                            train:
                                tripLines[0] ||
                                'UNKNOWN TRAIN',

                            class_name:
                                cardLines[0] ||
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

            const cards =
                document.querySelectorAll(
                    '.single-seat-class'
                );

            if(cards.length > 0){

                return JSON.stringify({
                    type:'RESULT_READY'
                });
            }

            return JSON.stringify({
                type:'WAIT'
            });

        })()
        """.trimIndent()

        eval(js) { raw ->

            try{

                val cleaned =
                    raw
                        .trim('"')
                        .replace(
                            "\\\"",
                            "\""
                        )

                val o =
                    JSONObject(cleaned)

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

                        continueAfterNoTicket()
                    }

                    "AVAILABLE" -> {

                        val arr =
                            o.getJSONArray(
                                "items"
                            )

                        handleAvailable(arr)
                    }

                    "RESULT_READY" -> {

                        append(
                            "Result data loaded; no available S_CHAIR tickets."
                        )

                        continueAfterNoTicket()
                    }

                    else -> {

                        if(
                            elapsed >= 90000
                        ){

                            append(
                                "Result data timeout; NOT treating empty page as no-ticket."
                            )

                            nextRoute()

                        }else{

                            handler.postDelayed(
                                {
                                    checkResult(
                                        elapsed + 500
                                    )
                                },
                                500
                            )
                        }
                    }
                }

            }catch(e: Exception){

                if(
                    elapsed >= 90000
                ){

                    append(
                        "Result parsing timeout; moving safely."
                    )

                    nextRoute()

                }else{

                    handler.postDelayed(
                        {
                            checkResult(
                                elapsed + 500
                            )
                        },
                        500
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

            sb.append(
                "🚆 " +
                x.optString("train") +
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
                x.optInt("available") +
                "\n\n"
            )
        }

        append(
            sb.toString()
        )

        sendTelegram(
            sb.toString()
        )

        nextRoute()
    }

    private fun continueAfterNoTicket() {

        if (!running) return

        if(routeIndex == 5){

            append(
                "=== FIRST 6 ROUTES COMPLETED ==="
            )

            append(
                "13-second group break..."
            )

            handler.postDelayed(
                {

                    if(running){

                        routeIndex = 6

                        runRoute(6)
                    }

                },
                13000
            )

            return
        }

        if(routeIndex < 5){

            val next =
                routeIndex + 1

            routeIndex = next

            append(
                "Using suggested Search button #1 for next route."
            )

            clickSuggested(
                1,
                next
            )

            return
        }

        if(routeIndex < 11){

            val next =
                routeIndex + 1

            routeIndex = next

            append(
                "Using suggested Search button #2 for next route."
            )

            clickSuggested(
                2,
                next
            )

            return
        }

        append(
            "=== ALL 12 ROUTES CHECKED ==="
        )

        append(
            "14-second cycle break..."
        )

        handler.postDelayed(
            {

                if(!running)
                    return@postDelayed

                cycle++

                append(
                    "=== STARTING CYCLE $cycle ==="
                )

                runRoute(0)

            },
            14000
        )
    }

    /*
     * IMPORTANT:
     *
     * After NO TICKET:
     * wait until TWO Search buttons exist,
     * then ALWAYS click the FIRST one.
     *
     * No route text is examined.
     */
    private fun clickSuggested(
        buttonNumber: Int,
        nextIndex: Int
    ) {

        if (!running) return

        val nextFrom =
            routes[nextIndex].first

        val nextTo =
            routes[nextIndex].second

        append(
            "Next route: $nextFrom → $nextTo"
        )

        val js = """
        (function(){

            const buttons =
                [
                    ...document.querySelectorAll(
                        'button'
                    )
                ].filter(b => {

                    const text =
                        (
                            b.innerText ||
                            b.textContent ||
                            ''
                        ).trim();

                    return (
                        text === 'Search' &&
                        !b.disabled &&
                        (
                            b.offsetWidth ||
                            b.offsetHeight ||
                            b.getClientRects().length
                        )
                    );
                });

            /*
             * Do not examine route text.
             * Simply wait for TWO Search buttons.
             */
            if(buttons.length < 2){

                return 'WAIT_TWO_BUTTONS';
            }

            /*
             * Always FIRST Search button.
             */
            const b =
                buttons[0];

            try{

                b.scrollIntoView({
                    block:'center',
                    inline:'center'
                });

            }catch(err){}

            try{

                b.click();

                return 'CLICKED_FIRST_SEARCH';

            }catch(err){

                try{

                    b.dispatchEvent(
                        new MouseEvent(
                            'click',
                            {
                                bubbles:true,
                                cancelable:true,
                                view:window
                            }
                        )
                    );

                    return 'DISPATCHED_FIRST_SEARCH';

                }catch(x){

                    return 'ERROR';
                }
            }

        })()
        """.trimIndent()

        eval(js) { result ->

            append(
                "Suggested Search: $result"
            )

            if(
                result.contains(
                    "CLICKED_FIRST_SEARCH"
                ) ||
                result.contains(
                    "DISPATCHED_FIRST_SEARCH"
                )
            ){

                append(
                    "✓ First suggested Search clicked."
                )

                waitResult(0)

            }else{

                retrySuggested(
                    1,
                    0
                )
            }
        }
    }

    private fun retrySuggested(
        buttonNumber: Int,
        elapsed: Int
    ) {

        if (!running) return

        if(elapsed >= 15000){

            append(
                "Suggested Search buttons timeout."
            )

            append(
                "Safe recovery: returning to current route."
            )

            runRoute(
                routeIndex
            )

            return
        }

        eval(
            """
            (function(){

                const buttons =
                    [
                        ...document.querySelectorAll(
                            'button'
                        )
                    ].filter(b => {

                        const text =
                            (
                                b.innerText ||
                                b.textContent ||
                                ''
                            ).trim();

                        return (
                            text === 'Search' &&
                            !b.disabled &&
                            (
                                b.offsetWidth ||
                                b.offsetHeight ||
                                b.getClientRects().length
                            )
                        );
                    });

                /*
                 * Wait for TWO Search buttons.
                 */
                if(buttons.length < 2){

                    return 'WAIT';
                }

                /*
                 * ALWAYS FIRST.
                 */
                const b =
                    buttons[0];

                try{

                    b.scrollIntoView({
                        block:'center',
                        inline:'center'
                    });

                }catch(e){}

                try{

                    b.click();

                    return 'CLICKED';

                }catch(e){

                    try{

                        b.dispatchEvent(
                            new MouseEvent(
                                'click',
                                {
                                    bubbles:true,
                                    cancelable:true,
                                    view:window
                                }
                            )
                        );

                        return 'DISPATCHED';

                    }catch(x){

                        return 'ERROR';
                    }
                }

            })()
            """.trimIndent()
        ) { result ->

            if(
                result.contains("CLICKED") ||
                result.contains("DISPATCHED")
            ){

                append(
                    "✓ First suggested Search clicked."
                )

                waitResult(0)

            }else{

                handler.postDelayed(
                    {

                        retrySuggested(
                            1,
                            elapsed + 500
                        )

                    },
                    500
                )
            }
        }
    }

    private fun nextRoute() {

        if (!running) return

        if(routeIndex == 5){

            append(
                "=== FIRST 6 ROUTES COMPLETED ==="
            )

            append(
                "13-second group break..."
            )

            handler.postDelayed(
                {

                    if(running){

                        routeIndex = 6

                        runRoute(6)
                    }

                },
                13000
            )

            return
        }

        if(
            routeIndex < 5
        ){

            routeIndex++

            append(
                "Moving to next route using Search #1."
            )

            clickSuggested(
                1,
                routeIndex
            )

            return
        }

        if(
            routeIndex < 11
        ){

            routeIndex++

            append(
                "Moving to next route using Search #2."
            )

            clickSuggested(
                2,
                routeIndex
            )

            return
        }

        append(
            "=== ALL 12 ROUTES CHECKED ==="
        )

        append(
            "14-second cycle break..."
        )

        handler.postDelayed(
            {

                if(!running)
                    return@postDelayed

                cycle++

                append(
                    "=== STARTING CYCLE $cycle ==="
                )

                runRoute(0)

            },
            14000
        )
    }

    private fun sendTelegram(
        message: String
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

            return
        }

        executor.execute {

            try{

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

                val code =
                    c.responseCode

                val ok =
                    code in 200..299

                handler.post {

                    append(
                        if(ok)
                            "✓ Telegram notification sent."
                        else
                            "✗ Telegram HTTP $code"
                    )
                }

                c.disconnect()

            }catch(e: Exception){

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

        runOnUiThread {

            web.evaluateJavascript(
                js
            ) { result ->

                cb(
                    result ?: ""
                )
            }
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
                )
                    .format(it)

            } ?: s

    }catch(_: Exception){

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

    override fun onDestroy(){

        running = false

        handler.removeCallbacksAndMessages(
            null
        )

        executor.shutdownNow()

        super.onDestroy()
    }
}

এটাই পুরো ফাইল। আগের "MainActivity.kt" সম্পূর্ণ মুছে এই কোডটি বসিয়ে Build করবেন।
