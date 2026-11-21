# `apkx` — rencana implementasi

## Keputusan final

- Build tidak dijalankan di HP. Repo GitHub + GitHub Actions yang build APK, lalu APK diunduh dan dipasang di perangkat.
- Server MCP dan client agent sama-sama berjalan di HP. Endpoint: `http://127.0.0.1:8080/mcp`. Tidak ada jembatan PC di versi pertama.
- Fondasi dulu: APK + server MCP + decode + build. Zipalign, auto sign, dan edit resource menyusul.

Perangkat uji: TECNO LJ6, Android 16 (SDK 36), arm64-v8a. Environment dev berada di dalam proot di perangkat yang sama, jadi STM tidak dipakai untuk build maupun runtime tool.

## Bentuk akhir fondasi

```
[agent AI di HP]
        |  MCP stdio/HTTP lokal
[apkx APK]
  NanoHTTPD -> McpHandler -> Tools
        |  in-process
[apktool.dex]  -> decode smali, -r supaya lewat AWT dan aapt2
[smali/dexlib2] -> assemble ulang dex
```

 Kenapa `-r`: apktool memakai `ImageIO`/`BufferedImage` untuk 9-patch dan aapt2 x86-64 untuk build resource. Android tidak punya AWT dan tidak bisa jalankan aapt2 x86-64. Fondasi mempertahankan resource apa adanya, jadi dua masalah itu tidak muncul.

## Tool MCP fondasi

| tool | fungsi |
| --- | --- |
| `ping` | health check |
| `apk_info` | ukuran, jumlah entry, daftar dex, kelengkapan file |
| `apk_decode` | Input base64 atau path di `filesDir`, output folder hasil decode |
| `apk_modify` | baca/tulis/hapus file di folder hasil decode, path dibatasi agar tidak keluar dari `filesDir` |
| `apk_build` | smali di-assemble jadi dex, resource disalin utuh, hasil APK dikembalikan sebagai base64 atau path |

Argumen file memakai `contentB64`. Karena agent dan server satu perangkat, tidak ada masalah izin lintas aplikasi.

Protokol: Streamable HTTP di `/mcp`, balasan `application/json`. Method yang ditangani: `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`. GET `/mcp` mengembalikan 204.

## Struktur repo

```
app/
  build.gradle
  src/main/AndroidManifest.xml
  src/main/java/id/apkx/
    MainActivity.java
    ApkxService.java
    MiniHttp.java
    McpHandler.java
    Tools.java
    ApkEngine.java
    Repack.java
  src/main/assets/apktool.dex
tools/
  prepare-assets.sh      unduh apktool jar, d8 jadi apktool.dex
.github/workflows/build.yml
selftest/smoke.sh
README.md
```

`prepare-assets.sh` dijalankan di CI runner x86-64. Script itu juga menyimpan apktool.dex hasil sebagai artifact supaya build berikutnya tidak mengunduh ulang.

## Fase

1. Skeleton APK + server MCP + tool `ping`. Uji pasang di HP lalu connect dari agent lokal.
2. `apktool.dex` dan `apk_decode`. Uji dengan APK nyata yang punya smali.
3. `apk_modify` dan `apk_build`. Uji pasang APK hasil rebuild.
4. Zipalign native `.so` dari sumber AOSP + `apk_sign` dengan keystore otomatis v1/v2/v3.
5. Edit resource penuh: bawa aapt2 ARM64 beserta library Termux-nya, set `LD_LIBRARY_PATH`, patch decoder 9-patch.
6. Jembatan MCP stdio di dalam APK kalau agent lokal tidak bisa HTTP.

## Alternatif lebih malas

`apktool d -r`, Ubah smali, `apktool b --aapt ...`, `zipalign`, `apksigner` lewat terminal di PC. Itu cukup kalau tidak butuh agent di HP.

## Yang tidak dibuat di fondasi

Edit resource XML, zipalign, signing, split APK, dan UI editor.
