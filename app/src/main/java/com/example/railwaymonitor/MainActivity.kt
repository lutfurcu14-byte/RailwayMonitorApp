package com.example.railwaymonitor

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
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

    private val railwayHome = "https://eticket.railway.gov.bd/"

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
            android.os.Build.VERSION.SDK_INT >= 33 &&
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
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true

        web.settings.userAgentString =
            web.settings.userAgentString +
                    " RailwayMonitorApp/1.0"

        web.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                append(
                    "Page loaded: ${url ?: ""}"
                )
            }
        }

        web.webChromeClient = WebChromeClient()

        web.loadUrl(railwayHome)
    }

    private fun pickDate() {

        val calendar = Calendar.getInstance()

        val current = dateEdit.text.toString().trim()

        try {
            val sdf = SimpleDateFormat(
                "dd-MM-yyyy",
                Locale.US
            )

            val date = sdf.parse(current)

            if (date != null) {
                calendar.time = date
            }

        } catch (_: Exception) {
        }

        DatePickerDialog(
            this,
            { _, year, month, day ->

                val value = String.format(
                    Locale.US,
                    "%02d-%02d-%04d",
                    day,
                    month + 1,
                    year
                )

                dateEdit.setText(value)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun startMonitor() {

        if (running) {
            return
        }

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
                tokenEdit.text.toString().trim()
            )
            .putString(
                "chat",
                chatEdit.text.toString().trim()
            )
            .apply()

        running = true
        routeIndex = 0
        cycle = 1

        append("")
        append("==============================")
        append("MONITOR STARTED")
        append(
            "Date: $targetDate"
        )
        append(
            "Class: S_CHAIR"
        )
        append(
            "Routes: 12"
        )
        append("==============================")

        setStatus("Running")

        startFirstRoute()
    }

    private fun stopMonitor() {

        running = false

        handler.removeCallbacksAndMessages(null)

        append("")
        append("=== MONITOR STOPPED ===")

        setStatus("Stopped")
    }

    private fun startFirstRoute() {

        if (!running) {
            return
        }

        routeIndex = 0

        append("")
        append(
            "Cycle $cycle → starting Route 1"
        )

        loadHomeForRoute()
    }

    private fun loadHomeForRoute() {
