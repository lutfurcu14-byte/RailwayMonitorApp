package com.example.railwaymonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var dateInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var chatIdInput: EditText
    private lateinit var intervalInput: EditText
    private lateinit var logView: TextView
    private lateinit var scrollView: ScrollView

    private val prefs by lazy { getSharedPreferences("railway_monitor_prefs", Context.MODE_PRIVATE) }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val msg = intent?.getStringExtra(TicketMonitorService.EXTRA_LOG_MSG) ?: return
            appendLog(msg)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())

        val root = window.decorView.findViewById<android.view.View>(android.R.id.content)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, bars.top + 32, view.paddingRight, view.paddingBottom)
            insets
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        loadSavedInputs()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(TicketMonitorService.ACTION_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(logReceiver, filter)
        }

        // সার্ভিসে জমে থাকা পুরনো লগ ইতিহাস দেখানো (অ্যাপ বন্ধ থাকা অবস্থায়
        // যা যা ঘটেছিল তা সহ) — নতুন করে খালি থেকে শুরু না করে।
        val history = TicketMonitorService.getLogSnapshot()
        logView.text = history.joinToString("\n")
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(logReceiver) } catch (_: Exception) {}
    }

    // ------------------------------------------------------------
    // UI  (কোনো XML layout ছাড়াই, প্রোগ্রামেটিকভাবে বানানো — GitHub-এ
    // ফাইল সংখ্যা কম রাখার জন্য; চাইলে পরে .xml layout-এ সরানো যাবে)
    // ------------------------------------------------------------

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }

        dateInput = EditText(this).apply {
            hint = "তারিখ (DD-MM-YYYY), যেমন: 06-09-2026"
        }
        tokenInput = EditText(this).apply {
            hint = "Telegram Bot Token"
        }
        chatIdInput = EditText(this).apply {
            hint = "Telegram Chat ID"
        }
        intervalInput = EditText(this).apply {
            hint = "প্রতি সাইকেলের মাঝে বিরতি (সেকেন্ড), যেমন: 30"
        }

        val loginBtn = Button(this).apply {
            text = "লগইন করুন (প্রথমবার / সেশন শেষ হয়ে গেলে)"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
            }
        }
        val startBtn = Button(this).apply {
            text = "মনিটরিং শুরু করুন"
            setOnClickListener { startMonitoring() }
        }
        val stopBtn = Button(this).apply {
            text = "মনিটরিং বন্ধ করুন"
            setOnClickListener { stopMonitoring() }
        }

        logView = TextView(this).apply {
            movementMethod = ScrollingMovementMethod()
            setPadding(0, 24, 0, 0)
        }
        scrollView = ScrollView(this).apply {
            addView(logView)
        }

        root.addView(dateInput)
        root.addView(tokenInput)
        root.addView(chatIdInput)
        root.addView(intervalInput)
        root.addView(loginBtn)
        root.addView(startBtn)
        root.addView(stopBtn)
        root.addView(
            scrollView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        return root
    }

    private fun loadSavedInputs() {
        dateInput.setText(prefs.getString("date", "06-09-2026"))
        tokenInput.setText(prefs.getString("token", ""))
        chatIdInput.setText(prefs.getString("chat_id", ""))
        intervalInput.setText(prefs.getString("interval", "30"))
    }

    private fun saveInputs() {
        prefs.edit()
            .putString("date", dateInput.text.toString())
            .putString("token", tokenInput.text.toString())
            .putString("chat_id", chatIdInput.text.toString())
            .putString("interval", intervalInput.text.toString())
            .apply()
    }

    private fun appendLog(msg: String) {
        logView.append("\n$msg")
        scrollView.post { scrollView.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    private fun startMonitoring() {
        saveInputs()
        val interval = intervalInput.text.toString().toIntOrNull() ?: 30
        val intent = Intent(this, TicketMonitorService::class.java).apply {
            action = TicketMonitorService.ACTION_START
            putExtra(TicketMonitorService.EXTRA_DATE, dateInput.text.toString())
            putExtra(TicketMonitorService.EXTRA_BOT_TOKEN, tokenInput.text.toString())
            putExtra(TicketMonitorService.EXTRA_CHAT_ID, chatIdInput.text.toString())
            putExtra(TicketMonitorService.EXTRA_INTERVAL_SEC, interval)
        }
        ContextCompat.startForegroundService(this, intent)
        appendLog("সার্ভিস শুরু করার অনুরোধ পাঠানো হয়েছে...")
    }

    private fun stopMonitoring() {
        val intent = Intent(this, TicketMonitorService::class.java).apply {
            action = TicketMonitorService.ACTION_STOP
        }
        startService(intent)
        appendLog("সার্ভিস বন্ধ করার অনুরোধ পাঠানো হয়েছে...")
    }
}
