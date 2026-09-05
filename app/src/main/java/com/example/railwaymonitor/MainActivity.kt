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
          const from=${JSONObject.quote(from)}, to=${JSONObject.quote(to)};
          const date=${JSONObject.quote(toIso(targetDate))};

          function vis(sel){
            return [...document.querySelectorAll(sel)]
              .find(e =>
                e.offsetWidth ||
                e.offsetHeight ||
                e.getClientRects().length
              )
          }

          function fire(e){
