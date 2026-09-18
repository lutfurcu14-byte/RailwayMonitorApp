package com.example.railwaymonitor

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * এই স্ক্রিনে WebView স্বাভাবিকভাবেই দেখা যায় (headless না)।
 * ব্যবহারকারী এখানে একবার নিজ হাতে:
 *   - "I Agree" ডিসক্লেইমার
 *   - ফোন নম্বর + পাসওয়ার্ড + "I'm not a robot" চেকবক্স দিয়ে লগইন
 * সম্পন্ন করবেন। এরপর কুকি/সেশন ডিস্কে সংরক্ষিত হয়ে যায়, আর
 * ব্যাকগ্রাউন্ড সার্ভিসের হেডলেস WebView সেই একই সেশন ব্যবহার করে
 * টিকেট চেক করতে পারবে — যতক্ষণ সার্ভিস active থাকবে, সেশনও থাকবে।
 */
class LoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        statusText = TextView(this).apply {
            text = "এখানে সাইটে গিয়ে নিজে হাতে \"I Agree\" চাপুন, তারপর ফোন নম্বর ও পাসওয়ার্ড দিয়ে লগইন করুন।"
            setPadding(24, 48, 24, 16)
        }

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient() // সাইটের ভেতরেই থাকবে, বাইরের ব্রাউজারে যাবে না
        }

        val doneBtn = Button(this).apply {
            text = "লগইন সম্পন্ন হয়েছে, ফিরে যান"
            setOnClickListener {
                // কুকি জোরপূর্বক ডিস্কে ফ্লাশ করা, যাতে সার্ভিস তা সাথে সাথে ব্যবহার করতে পারে
                CookieManager.getInstance().flush()
                statusText.text = "সংরক্ষণ করা হয়েছে। এখন ফিরে যেতে পারেন।"
                finish()
            }
        }

        root.addView(statusText)
        root.addView(
            webView,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        root.addView(doneBtn)

        setContentView(root)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.loadUrl(TicketMonitorService.RAILWAY_HOME)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
