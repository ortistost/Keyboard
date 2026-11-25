package com.example.fullkeyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            gravity = Gravity.TOP
        }
        fun label(t: String) = TextView(this).apply {
            text = t; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, pad, 0, pad / 2)
        }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; isAllCaps = false; setOnClickListener { onClick() }
        }

        root.addView(TextView(this).apply { text = "FullKeyboard"; textSize = 26f })
        root.addView(label("1) Klavyeyi etkinleştir:"))
        root.addView(button("Klavye ayarlarını aç") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(label("2) Diğer uygulamaların üzerinde göster iznini ver (panel için gerekli):"))
        root.addView(button("Overlay iznini aç") {
            if (Build.VERSION.SDK_INT >= 23) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            }
        })
        root.addView(label("3) Klavyeyi seç:"))
        root.addView(button("Klavye seçiciyi aç") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })
        root.addView(label(
            "Kullanım: Ekranda ⌨ balonu belirir. Dokununca panel açılır/kapanır, " +
                "sürükleyerek taşırsın. Panelde ≡ başlığından sürükle, kaydırıcıyla şeffaflığı ayarla. " +
                "Balona uzun basınca klavye seçici açılır."))
        root.addView(label("Deneme alanı:"))
        root.addView(EditText(this).apply { hint = "Buraya yaz..." })
        setContentView(root)
    }
}
