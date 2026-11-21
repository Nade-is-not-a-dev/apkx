# apkx

Tool APK on-device yang menyediakan server MCP lokal. Semua engine dibungkus dalam satu APK. Fondasi saat ini mencakup decode smali, modifikasi file, dan rebuild dengan resource dipertahankan apa adanya.

## Build

Build dijalankan GitHub Actions. Commit ke branch `main`, lalu unduh artifact `apkx` dari halaman Actions.

Build lokal juga bisa:

```
export ANDROID_SDK_ROOT=/root/Android/Sdk
bash tools/prepare-assets.sh
./gradlew assembleRelease
```

## Pasang

```
pm install -r apkx.apk
am start -n id.apkx/.MainActivity
```

Tekan **Start server**, lalu agent di perangkat connect ke `http://127.0.0.1:8080/mcp`.

## Tool MCP

- `ping`
- `apk_info`
- `apk_decode`
- `apk_modify`
- `apk_build`

Lihat [PLAN.md](PLAN.md) untuk arsitektur dan fase berikutnya.
