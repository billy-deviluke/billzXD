# PB Macro Controller v3 (Root + OTG Edition)

Aplikasi Floating Overlay Android berbasis **Root Access (`su`)** untuk menjalankan macro **Auto Tapping** dan **Auto Change Weapon (QQ)** menggunakan **Mouse & Keyboard OTG**.

## Fitur Utama
1. **Persistent Root Shell (`su`):** Eksekusi sentuhan layar (`input tap`) langsung melalui root shell tanpa membuka proses berulang kali sehingga minim latensi.
2. **OTG Hardware Listener (`getevent -l`):** Membaca langsung input fisik dari mouse dan keyboard OTG di level kernel Linux.
   - **Auto Change Weapon / QQ:** Tekan tombol **`Q`** di keyboard OTG atau **Klik Kanan Mouse (`BTN_RIGHT`)**.
   - **Auto Tapping (Rapid Fire):** Tahan tombol **`E`** di keyboard OTG atau tombol samping mouse (**`BTN_SIDE` / `BTN_EXTRA`**).
3. **3 Draggable Target Pins:** Geser pin **`T`** (Tembak), **`1`** (Slot 1), dan **`2`** (Slot 2) langsung di atas tombol game. Koordinat otomatis tersimpan.
4. **Pengaturan Kecepatan Real-time:** Slider untuk mengatur **Tap Interval** (ms) dan **Delay QQ Switch** (ms) langsung dari panel melayang.

## Cara Build APK di GitHub
1. Ekstrak ZIP ini dan unggah seluruh isinya ke repositori GitHub kamu.
2. Buka tab **Actions** -> pilih **Build Android APK** -> klik **Run workflow**.
3. Unduh file APK pada bagian **Artifacts** (`PBMacroController-v3-Root-APK`).
