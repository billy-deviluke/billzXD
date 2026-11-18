package com.example.pbmacro

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import java.io.DataOutputStream

class FloatingPanelService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var prefs: SharedPreferences
    private var panel: View? = null
    private val targetPins = mutableListOf<View>()
    private val channelId = "pb_macro_panel"

    // Persistent root shell for zero-latency execution
    private var suProcess: Process? = null
    private var suOut: DataOutputStream? = null
    private var getEventProcess: Process? = null

    @Volatile private var isMacroArmed = false
    @Volatile private var isTappingActive = false
    @Volatile private var tapIntervalMs = 50
    @Volatile private var qqDelayMs = 35
    @Volatile private var pinsVisible = true

    // Screen coordinates for Fire (T), Weapon Slot 1 (1), Weapon Slot 2 (2)
    private var fireX = 1800
    private var fireY = 850
    private var slot1X = 1500
    private var slot1Y = 950
    private var slot2X = 1650
    private var slot2Y = 950

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("pb_macro_prefs", Context.MODE_PRIVATE)
        loadSavedCoordinates()

        createChannel()
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channelId)
                .setContentTitle("PB Macro Panel (Root)")
                .setContentText("Macro OTG siap digunakan")
                .setSmallIcon(android.R.drawable.ic_media_play).build()
        else Notification.Builder(this)
            .setContentTitle("PB Macro Panel (Root)")
            .setSmallIcon(android.R.drawable.ic_media_play).build()

        startForeground(7, notification)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        initRootShell()
        showPanel()
        spawnTargetPins()
        startOtgEventListener()
    }

    private fun loadSavedCoordinates() {
        fireX = prefs.getInt("fireX", 1800)
        fireY = prefs.getInt("fireY", 850)
        slot1X = prefs.getInt("slot1X", 1500)
        slot1Y = prefs.getInt("slot1Y", 950)
        slot2X = prefs.getInt("slot2X", 1650)
        slot2Y = prefs.getInt("slot2Y", 950)
        tapIntervalMs = prefs.getInt("tapIntervalMs", 50)
        qqDelayMs = prefs.getInt("qqDelayMs", 35)
    }

    private fun saveCoordinates() {
        prefs.edit()
            .putInt("fireX", fireX).putInt("fireY", fireY)
            .putInt("slot1X", slot1X).putInt("slot1Y", slot1Y)
            .putInt("slot2X", slot2X).putInt("slot2Y", slot2Y)
            .putInt("tapIntervalMs", tapIntervalMs)
            .putInt("qqDelayMs", qqDelayMs)
            .apply()
    }

    private fun initRootShell() {
        try {
            suProcess = Runtime.getRuntime().exec("su")
            suOut = DataOutputStream(suProcess!!.outputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    private fun execRoot(cmd: String) {
        try {
            if (suOut == null) initRootShell()
            suOut?.apply {
                writeBytes("$cmd\n")
                flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            suOut = null
        }
    }

    private fun showPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            background = GradientDrawable().apply {
                setColor(0xEE1A1D24.toInt())
                cornerRadius = 18f
                setStroke(2, 0xFF3E4451.toInt())
            }
        }

        val header = TextView(this).apply {
            text = "PB • ROOT OTG MACRO (Geser)"
            textSize = 14f
            setTextColor(0xFF61AFEF.toInt())
            setPadding(0, 0, 0, 8)
        }
        box.addView(header)

        val status = TextView(this).apply {
            text = "Status: STANDBY"
            textSize = 13f
            setTextColor(0xFFE5C07B.toInt())
        }
        box.addView(status)

        val lastKeyText = TextView(this).apply {
            text = "Trigger: [Q / Klik Kanan]=QQ | [E / MB4]=Tap"
            textSize = 11f
            setTextColor(0xFFABB2BF.toInt())
            setPadding(0, 2, 0, 8)
        }
        box.addView(lastKeyText)

        // Slider 1: Tapping Interval
        val tapLabel = TextView(this).apply {
            text = "Tap Interval: ${tapIntervalMs} ms"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
        }
        box.addView(tapLabel)
        val seekTap = SeekBar(this).apply {
            max = 280
            progress = (tapIntervalMs - 20).coerceIn(0, 280)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    tapIntervalMs = p + 20
                    tapLabel.text = "Tap Interval: ${tapIntervalMs} ms"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCoordinates() }
            })
        }
        box.addView(seekTap)

        // Slider 2: Auto QQ Switch Delay
        val qqLabel = TextView(this).apply {
            text = "Delay QQ (Switch): ${qqDelayMs} ms"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
        }
        box.addView(qqLabel)
        val seekQq = SeekBar(this).apply {
            max = 185
            progress = (qqDelayMs - 15).coerceIn(0, 185)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                    qqDelayMs = p + 15
                    qqLabel.text = "Delay QQ (Switch): ${qqDelayMs} ms"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { saveCoordinates() }
            })
        }
        box.addView(seekQq)

        // Control Buttons Row 1
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(Button(this).apply {
            text = "START"
            textSize = 11f
            setOnClickListener {
                isMacroArmed = true
                status.text = "Status: AKTIF (Siap OTG)"
                status.setTextColor(0xFF98C379.toInt())
            }
        })
        row1.addView(Button(this).apply {
            text = "STOP"
            textSize = 11f
            setOnClickListener {
                isMacroArmed = false
                isTappingActive = false
                status.text = "Status: STANDBY"
                status.setTextColor(0xFFE5C07B.toInt())
            }
        })
        row1.addView(Button(this).apply {
            text = "PIN: ON"
            textSize = 11f
            setOnClickListener {
                pinsVisible = !pinsVisible
                text = if (pinsVisible) "PIN: ON" else "PIN: OFF"
                val vis = if (pinsVisible) View.VISIBLE else View.GONE
                targetPins.forEach { it.visibility = vis }
            }
        })
        box.addView(row1)

        // Control Buttons Row 2 (Manual Test & Close)
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(Button(this).apply {
            text = "TEST QQ"
            textSize = 11f
            setOnClickListener { executeAutoQQ() }
        })
        row2.addView(Button(this).apply {
            text = "TUTUP"
            textSize = 11f
            setOnClickListener { stopSelf() }
        })
        box.addView(row2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 30; y = 120 }

        header.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var wx = 0; var wy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx = e.rawX.toInt(); sy = e.rawY.toInt(); wx = params.x; wy = params.y; return true }
                    MotionEvent.ACTION_MOVE -> { params.x = wx + e.rawX.toInt() - sx; params.y = wy + e.rawY.toInt() - sy; wm.updateViewLayout(box, params); return true }
                }
                return false
            }
        })
        panel = box
        wm.addView(box, params)
    }

    private fun spawnTargetPins() {
        // Pin T = Tombol Tembak (Merah)
        createDraggablePin("T", 0xAAFF3B30.toInt(), fireX - 45, fireY - 45) { cx, cy ->
            fireX = cx; fireY = cy; saveCoordinates()
        }
        // Pin 1 = Slot Senjata 1 (Biru)
        createDraggablePin("1", 0xAA007AFF.toInt(), slot1X - 45, slot1Y - 45) { cx, cy ->
            slot1X = cx; slot1Y = cy; saveCoordinates()
        }
        // Pin 2 = Slot Senjata 2 / Quick Switch (Hijau)
        createDraggablePin("2", 0xAA34C759.toInt(), slot2X - 45, slot2Y - 45) { cx, cy ->
            slot2X = cx; slot2Y = cy; saveCoordinates()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDraggablePin(
        label: String,
        colorHex: Int,
        initX: Int,
        initY: Int,
        onMoved: (centerX: Int, centerY: Int) -> Unit
    ) {
        val pinSize = 90
        val pin = TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorHex)
                setStroke(3, 0xFFFFFFFF.toInt())
            }
        }

        val params = WindowManager.LayoutParams(
            pinSize,
            pinSize,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initX.coerceAtLeast(0)
            y = initY.coerceAtLeast(0)
        }

        pin.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0; var sy = 0; var wx = 0; var wy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = e.rawX.toInt(); sy = e.rawY.toInt()
                        wx = params.x; wy = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = wx + e.rawX.toInt() - sx
                        params.y = wy + e.rawY.toInt() - sy
                        wm.updateViewLayout(pin, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val loc = IntArray(2)
                        pin.getLocationOnScreen(loc)
                        val cx = loc[0] + (pin.width / 2)
                        val cy = loc[1] + (pin.height / 2)
                        onMoved(cx, cy)
                        return true
                    }
                }
                return false
            }
        })

        wm.addView(pin, params)
        targetPins.add(pin)
    }

    /**
     * Membaca event keyboard & mouse OTG langsung dari kernel Linux (/dev/input/event*) via root getevent -l
     */
    private fun startOtgEventListener() {
        Thread {
            try {
                getEventProcess = Runtime.getRuntime().exec(arrayOf("su", "-c", "getevent -l"))
                val reader = getEventProcess!!.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (!isMacroArmed) continue
                    val ev = line ?: continue

                    // 1. AUTO CHANGE WEAPON / QQ MACRO
                    // Dipicu oleh tombol 'Q' di keyboard OTG atau Klik Kanan Mouse (BTN_RIGHT)
                    if ((ev.contains("KEY_Q") || ev.contains("BTN_RIGHT")) && ev.contains("DOWN")) {
                        executeAutoQQ()
                    }

                    // 2. AUTO TAPPING MACRO (Rapid Fire)
                    // Dipicu saat menahan tombol 'E' di keyboard OTG atau tombol samping mouse (BTN_SIDE / BTN_EXTRA)
                    if (ev.contains("KEY_E") || ev.contains("BTN_SIDE") || ev.contains("BTN_EXTRA")) {
                        if (ev.contains("DOWN") && !isTappingActive) {
                            isTappingActive = true
                            startAutoTapLoop()
                        } else if (ev.contains("UP")) {
                            isTappingActive = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Eksekusi Macro SG/Sniper Auto QQ:
     * Tap Tembak (T) -> Delay -> Tap Slot 2 (2) -> Delay -> Tap Slot 1 (1)
     */
    private fun executeAutoQQ() {
        val delayUs = qqDelayMs * 1000L
        val cmd = "input tap $fireX $fireY; usleep $delayUs; input tap $slot2X $slot2Y; usleep $delayUs; input tap $slot1X $slot1Y"
        execRoot(cmd)
    }

    /**
     * Loop Auto Tapping selama tombol ditahan
     */
    private fun startAutoTapLoop() {
        Thread {
            while (isTappingActive && isMacroArmed) {
                execRoot("input tap $fireX $fireY")
                try {
                    Thread.sleep(tapIntervalMs.toLong())
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.start()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(channelId, "Floating panel", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        isMacroArmed = false
        isTappingActive = false
        panel?.let { wm.removeView(it) }
        panel = null
        targetPins.forEach { wm.removeView(it) }
        targetPins.clear()
        getEventProcess?.destroy()
        execRoot("exit")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
