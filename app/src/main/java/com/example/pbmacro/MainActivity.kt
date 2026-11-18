package com.example.pbmacro

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 48, 36, 36)
        }
        layout.addView(TextView(this).apply {
            text = "PB Macro Panel (ROOT OTG)\nFloating Overlay & Hardware Macro"
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })
        layout.addView(TextView(this).apply {
            text = "Kontrol OTG Default:\n• Klik Kanan / Tombol Q = Auto QQ (SG/Sniper)\n• Tombol E (Tahan) / MB4 = Auto Tapping\n• Geser pin T (Tembak), 1 (Slot 1), 2 (Slot 2) ke tombol game."
            textSize = 14f
            setPadding(0, 0, 0, 24)
        })
        layout.addView(Button(this).apply {
            text = "1. Cek & Izinkan Akses Root"
            setOnClickListener {
                try {
                    val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
                    val res = p.waitFor()
                    if (res == 0) {
                        Toast.makeText(this@MainActivity, "Akses Root Diberikan!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Gagal mendapatkan akses Root", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Perangkat tidak ter-root: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
        layout.addView(Button(this).apply {
            text = "2. Izinkan Tampil di Atas Aplikasi Lain"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        })
        layout.addView(Button(this).apply {
            text = "3. Jalankan Floating Panel & Macro"
            setOnClickListener {
                if (Settings.canDrawOverlays(this@MainActivity)) {
                    startForegroundService(Intent(this@MainActivity, FloatingPanelService::class.java))
                } else {
                    Toast.makeText(this@MainActivity, "Aktifkan izin overlay dulu!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
            }
        })
        layout.addView(Button(this).apply {
            text = "4. Hentikan Floating Panel"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FloatingPanelService::class.java))
            }
        })
        setContentView(layout)
    }
}
