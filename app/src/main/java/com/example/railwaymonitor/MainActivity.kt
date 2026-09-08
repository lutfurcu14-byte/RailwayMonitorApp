package com.example.railwaymonitor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

    private lateinit var webView: WebView
    private lateinit var logText: TextView
    private lateinit var statusText: TextView
    private lateinit var dateEdit: EditText
    private lateinit var tokenEdit: EditText
    private lateinit var chatEdit: EditText
    private lateinit var dateButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private val handler = Handler(Looper.getMainLooper())

    private val telegramExecutor =
        Executors.newSingleThreadExecutor()

    private val prefs by lazy {
        getSharedPreferences(
            "railway_monitor_settings",
            MODE_PRIVATE
        )
    }

    private var monitoring = false
    private var routeIndex = 0
    private var operationId = 0L

    private val homeUrl =
        "https://eticket.railway.gov.bd/"

    private val noTicketPhrase =
        "NOT FINDING ANY TICKET FOR YOUR DESIRED ROUTE ?"

    private val trainClass =
        "S_CHAIR"

    private data class Route(
        val from: String,
        val to: String,
        val suggestion: String
    )

    private val routes = listOf(
        Route("Sylhet", "Dhaka", "Maijgaon - Dhaka Search"),
        Route("Maijgaon", "Dhaka", "Kulaura - Dhaka Search"),
        Route("Kulaura", "Dhaka", "Shamshernagar - Dhaka Search"),
        Route("Shamshernagar", "Dhaka", "Sreemangal - Dhaka Search"),
        Route("Sreemangal", "Dhaka", "Shaistaganj - Dhaka Search"),
        Route("Shaistaganj", "Dhaka", ""),
        Route("Sylhet", "Biman_Bandar", "Maijgaon - Biman_Bandar Search"),
        Route("Maijgaon", "Biman_Bandar", "Kulaura - Biman_Bandar Search"),
        Route("Kulaura", "Biman_Bandar", "Shamshernagar - Biman_Bandar Search"),
        Route("Shamshernagar", "Biman_Bandar", "Sreemangal - Biman_Bandar Search"),
        Route("Sreemangal", "Biman_Bandar", "Shaistaganj - Biman_Bandar Search"),
        Route("Shaistaganj", "Biman_Bandar", "")
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        logText = findViewById(R.id.logText)
        statusText = findViewById(R.id.statusText)
        dateEdit = findViewById(R.id.dateEdit)
        tokenEdit = findViewById(R.id.tokenEdit)
        chatEdit = findViewById(R.id.chatEdit)
        dateButton = findViewById(R.id.dateButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        setupWebView()
        loadSettings()
        setupButtons()

        statusText.text = "Ready"
        startButton.isEnabled = true
        stopButton.isEnabled = false
        appendLog("Railway Monitor ready.")
    }

    private fun setupButtons() {
        dateButton.setOnClickListener { showNativeDatePicker