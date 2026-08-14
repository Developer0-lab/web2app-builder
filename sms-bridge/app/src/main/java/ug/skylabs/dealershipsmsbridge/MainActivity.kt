package ug.skylabs.dealershipsmsbridge

import android.Manifest
import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val endpoint = "https://rcfjdgusrroiemsdevei.supabase.co/functions/v1/ingest-dealership-payment"
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("bridge", MODE_PRIVATE)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 28) }
        val title = TextView(this).apply { text = "Dealership Payment Bridge"; textSize = 26f; setTextColor(Color.rgb(20,20,20)) }
        val info = TextView(this).apply { text = "Monitors incoming payment SMS and sends only recognized payment records to Dealership OS."; textSize = 15f; setPadding(0,12,0,20) }
        val key = EditText(this).apply { hint = "Bridge device key"; setSingleLine(true); setText(prefs.getString("key", "")) }
        val save = Button(this).apply { text = "Save & Enable Bridge" }
        status = TextView(this).apply { textSize = 16f; setPadding(0,20,0,0) }
        save.setOnClickListener {
            val k = key.text.toString().trim()
            if (k.isBlank()) { status.text = "Enter the bridge device key first."; return@setOnClickListener }
            prefs.edit().putString("key", k).putString("endpoint", endpoint).apply()
            status.text = "Bridge configured. Endpoint connected to Supabase."
        }
        val permissions = Button(this).apply { text = "Allow SMS Access"; setOnClickListener { requestSmsPermissions() } }
        root.addView(title); root.addView(info); root.addView(key); root.addView(save); root.addView(permissions); root.addView(status)
        setContentView(root)
        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) requestSmsPermissions()
        else status.text = "SMS access is enabled."
    }

    private fun requestSmsPermissions() {
        requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 42)
    }
}
