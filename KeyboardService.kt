package com.example.fullkeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

private sealed class KeyDef(val label: String, val weight: Float)
private class Ch(lower: String, val upper: String, w: Float = 1f) : KeyDef(lower, w)
private class Code(label: String, val code: Int, w: Float = 1f, val repeat: Boolean = false) : KeyDef(label, w)
private class Mod(label: String, val id: String, w: Float = 1f) : KeyDef(label, w)

/**
 * Tam klavye: sürüklenebilir, şeffaflığı ayarlanabilir kayan panel + aç/kapat balonu.
 * Panel overlay penceresi olarak çizilir, tuşlar aktif InputConnection'a gönderilir.
 */
class KeyboardService : InputMethodService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var panel: LinearLayout? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var bubble: TextView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null

    private var ctrl = false
    private var alt = false
    private var shift = false
    private var caps = false

    private val charButtons = mutableListOf<Pair<Button, Ch>>()
    private val modButtons = mutableMapOf<String, Button>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureViews()
    }

    // Sistemin kendi klavyesi görünmesin; tüm iş kayan panelde.
    override fun onEvaluateInputViewShown(): Boolean = false

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        ensureViews()
    }

    override fun onDestroy() {
        panel?.let { runCatching { wm.removeView(it) } }
        bubble?.let { runCatching { wm.removeView(it) } }
        panel = null
        bubble = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- pencereler

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun ensureViews() {
        if (panel != null && bubble != null) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return
        runCatching {
            if (panel == null) createPanel()
            if (bubble == null) createBubble()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        val size = dp(52)
        val tv = TextView(this).apply {
            text = "⌨"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(200, 30, 90, 200))
            }
        }
        val lp = WindowManager.LayoutParams(
            size, size, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(8)
            y = resources.displayMetrics.heightPixels / 3
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        var longPressed = false
        val longRun = Runnable {
            longPressed = true
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y
                    moved = false; longPressed = false
                    handler.postDelayed(longRun, 700)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        handler.removeCallbacks(longRun)
                    }
                    if (moved) {
                        lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt()
                        wm.updateViewLayout(tv, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longRun)
                    if (!moved && !longPressed) togglePanel()
                }
                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longRun)
            }
            true
        }
        wm.addView(tv, lp)
        bubble = tv
        bubbleLp = lp
    }

    private fun togglePanel() {
        val p = panel ?: return
        p.visibility = if (p.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createPanel() {
        val dm = resources.displayMetrics
        val width = minOf((dm.widthPixels * 0.98f).toInt(), dp(980))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(235, 20, 22, 28))
            }
            visibility = View.GONE
        }

        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - width) / 2
            y = dm.heightPixels / 2
            alpha = 0.85f
        }

        // ---- başlık: sürükleme tutacağı + şeffaflık + kapat
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val handle = TextView(this).apply {
            text = "≡  FullKeyboard  (sürükle)"
            setTextColor(Color.LTGRAY)
            textSize = 12f
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        val seek = SeekBar(this).apply {
            max = 75
            progress = 60
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    lp.alpha = (0.25f + p / 100f).coerceIn(0.25f, 1f)
                    if (root.isAttachedToWindow) wm.updateViewLayout(root, lp)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        val imeBtn = headerButton("🌐") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        val closeBtn = headerButton("✕") { togglePanel() }

        header.addView(handle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(seek, LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT))
        header.addView(imeBtn)
        header.addView(closeBtn)
        root.addView(header)

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = ox + (e.rawX - sx).toInt()
                    lp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(root, lp)
                }
            }
            true
        }

        // ---- tuş satırları
        for (row in layout()) {
            val rowView = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in row) rowView.addView(makeKey(k),
                LinearLayout.LayoutParams(0, dp(40), k.weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            root.addView(rowView)
        }

        wm.addView(root, lp)
        panel = root
        panelLp = lp
        refreshKeys()
    }

    private fun headerButton(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 16f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- düzen

    private fun layout(): List<List<KeyDef>> {
        val letters = { s: String -> s.map { Ch(it.toString(), it.uppercase()) } }
        val fRow = mutableListOf<KeyDef>(Code("Esc", KeyEvent.KEYCODE_ESCAPE, 1.3f))
        for (i in 1..12) fRow.add(Code("F$i", KeyEvent.KEYCODE_F1 + i - 1))
        fRow.add(Code("Ins", KeyEvent.KEYCODE_INSERT, 1.2f))
        fRow.add(Code("Del", KeyEvent.KEYCODE_FORWARD_DEL, 1.2f, true))

        val sym1 = listOf("@", "#", "$", "€", "{", "}", "[", "]", "\\", "|", "~", "`", "£", "§")
            .map { Ch(it, it) }
        val sym2 = listOf("<", ">", ":", "\"", "'", ";", "_", "+", "=", "-", "*", "/", "&", "%", "^")
            .map { Ch(it, it) }

        val numbers = mutableListOf<KeyDef>()
        listOf("1" to "!", "2" to "'", "3" to "^", "4" to "+", "5" to "%", "6" to "&",
            "7" to "/", "8" to "(", "9" to ")", "0" to "=", "*" to "?", "-" to "_")
            .forEach { numbers.add(Ch(it.first, it.second)) }
        numbers.add(0, Ch("\"", "é"))
        numbers.add(Code("⌫", KeyEvent.KEYCODE_DEL, 1.8f, true))

        val r3 = mutableListOf<KeyDef>(Code("Tab", KeyEvent.KEYCODE_TAB, 1.5f))
        r3.addAll(letters("qwertyu"))
        r3.add(Ch("ı", "I"))
        r3.addAll(letters("op"))
        r3.add(Ch("ğ", "Ğ")); r3.add(Ch("ü", "Ü"))

        val r4 = mutableListOf<KeyDef>(Mod("Caps", "caps", 1.8f))
        r4.addAll(letters("asdfghjkl"))
        r4.add(Ch("ş", "Ş")); r4.add(Ch("i", "İ"))
        r4.add(Code("Enter", KeyEvent.KEYCODE_ENTER, 2f))

        val r5 = mutableListOf<KeyDef>(Mod("Shift", "shift", 2f), Ch("<", ">"))
        r5.addAll(letters("zxcvbnm"))
        r5.add(Ch("ö", "Ö")); r5.add(Ch("ç", "Ç"))
        r5.add(Ch(".", ":")); r5.add(Ch(",", ";"))
        r5.add(Mod("Shift", "shift", 2f))

        val r6 = listOf<KeyDef>(
            Mod("Ctrl", "ctrl", 1.4f), Mod("Alt", "alt", 1.4f),
            Code("␣", KeyEvent.KEYCODE_SPACE, 5f, true),
            Code("←", KeyEvent.KEYCODE_DPAD_LEFT, 1f, true),
            Code("↑", KeyEvent.KEYCODE_DPAD_UP, 1f, true),
            Code("↓", KeyEvent.KEYCODE_DPAD_DOWN, 1f, true),
            Code("→", KeyEvent.KEYCODE_DPAD_RIGHT, 1f, true),
            Code("Home", KeyEvent.KEYCODE_MOVE_HOME, 1.3f),
            Code("End", KeyEvent.KEYCODE_MOVE_END, 1.3f),
            Code("PgUp", KeyEvent.KEYCODE_PAGE_UP, 1.3f, true),
            Code("PgDn", KeyEvent.KEYCODE_PAGE_DOWN, 1.3f, true),
        )
        return listOf(fRow, sym1, sym2, numbers, r3, r4, r5, r6)
    }

    // ---------------------------------------------------------------- tuş görünümü

    @SuppressLint("ClickableViewAccessibility")
    private fun makeKey(k: KeyDef): Button {
        val b = Button(this).apply {
            text = k.label
            isAllCaps = false
            textSize = if (k.label.length > 2) 11f else 14f
            setTextColor(Color.WHITE)
            minWidth = 0; minHeight = 0; minimumWidth = 0; minimumHeight = 0
            setPadding(0, 0, 0, 0)
            stateListAnimator = null
            isFocusable = false
            background = keyBg(false)
        }
        when (k) {
            is Ch -> {
                charButtons.add(b to k)
                b.setOnClickListener { onChar(k) }
            }
            is Mod -> {
                modButtons[k.id] = b
                b.setOnClickListener { onMod(k.id) }
            }
            is Code -> {
                if (k.repeat) {
                    var run: Runnable? = null
                    b.setOnTouchListener { v, e ->
                        when (e.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.isPressed = true
                                sendKey(k.code)
                                run = object : Runnable {
                                    override fun run() { sendKey(k.code); handler.postDelayed(this, 45) }
                                }
                                handler.postDelayed(run!!, 400)
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.isPressed = false
                                run?.let { handler.removeCallbacks(it) }
                            }
                        }
                        true
                    }
                } else {
                    b.setOnClickListener { sendKey(k.code) }
                }
            }
        }
        return b
    }

    private fun keyBg(active: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(6).toFloat()
        setColor(if (active) Color.argb(255, 40, 110, 220) else Color.argb(230, 58, 62, 72))
    }

    private fun refreshKeys() {
        val upperOn = shift != caps
        for ((b, k) in charButtons) {
            val isLetter = k.label[0].isLetter()
            val up = if (isLetter) upperOn else shift
            b.text = if (up) k.upper else k.label
        }
        modButtons["ctrl"]?.background = keyBg(ctrl)
        modButtons["alt"]?.background = keyBg(alt)
        modButtons["caps"]?.background = keyBg(caps)
        panel?.let { p ->
            // iki Shift tuşu da aynı durumu gösterir
            for (i in 0 until (p as LinearLayout).childCount) {
                val row = p.getChildAt(i) as? LinearLayout ?: continue
                for (j in 0 until row.childCount) {
                    val v = row.getChildAt(j) as? Button ?: continue
                    if (v.text == "Shift") v.background = keyBg(shift)
                }
            }
        }
    }

    // ---------------------------------------------------------------- girdi

    private fun onMod(id: String) {
        when (id) {
            "ctrl" -> ctrl = !ctrl
            "alt" -> alt = !alt
            "shift" -> shift = !shift
            "caps" -> caps = !caps
        }
        refreshKeys()
    }

    private fun onChar(k: Ch) {
        val isLetter = k.label[0].isLetter()
        val upperOn = if (isLetter) (shift != caps) else shift
        val text = if (upperOn) k.upper else k.label
        val c = k.label[0]
        val keyCode = when (c) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            else -> 0
        }
        if ((ctrl || alt) && keyCode != 0) {
            sendKey(keyCode)
        } else {
            currentInputConnection?.commitText(text, 1)
            releaseOneShot()
        }
    }

    private fun metaState(): Int {
        var m = 0
        if (ctrl) m = m or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (alt) m = m or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        if (shift) m = m or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        return m
    }

    private fun sendKey(code: Int) {
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        val meta = metaState()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
        releaseOneShot()
    }

    // Ctrl / Alt / Shift tek kullanımlık; Caps kalıcı.
    private fun releaseOneShot() {
        if (ctrl || alt || shift) {
            ctrl = false; alt = false; shift = false
            refreshKeys()
        }
    }
}
