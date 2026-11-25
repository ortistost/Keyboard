# FullKeyboard

Kotlin ile Android klavye: sürüklenebilir, şeffaflığı ayarlanabilir kayan panel + aç/kapat balonu.

## Özellikler
- Esc, F1–F12, Ins, Del
- Sayı satırı + iki satır özel karakter (@ # $ € { } [ ] \ | ~ ` < > vb.)
- Türkçe Q harfleri (ı ğ ü ş i ö ç), Shift / Caps
- Ctrl, Alt (tek kullanımlık; Ctrl+C, Ctrl+V, Ctrl+A vb. çalışır)
- Ok tuşları, Home, End, PgUp, PgDn, Tab, Enter, Backspace (basılı tutunca tekrar)
- ⌨ balonu: dokun = paneli aç/kapat, sürükle = taşı, uzun bas = klavye seçici
- Panel başlığından sürüklenir, kaydırıcıyla şeffaflık ayarlanır

## Kurulum
1. GitHub'a yükle; Actions sekmesinde "Build APK" çalışır, APK artifact olarak iner.
2. Uygulamayı aç: klavyeyi etkinleştir, overlay iznini ver, klavyeyi seç.

Yerelde derlemek için: `gradle assembleDebug` (Gradle 8.7, JDK 17).
