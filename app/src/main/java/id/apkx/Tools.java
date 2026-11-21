package id.apkx;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class Tools {
    private static File files;
    private static File work;
    private static File inbox;

    private Tools() {}

    public static void init(Context ctx) {
        files = ctx.getFilesDir();
        work = new File(files, "work");
        inbox = new File(files, "in");
        work.mkdirs();
        inbox.mkdirs();
    }

    public static void clearWorkspace(Context ctx) {
        init(ctx);
        delete(work);
        delete(inbox);
        work.mkdirs();
        inbox.mkdirs();
    }

    public static JSONArray schemas() throws JSONException {
        JSONArray tools = new JSONArray();

        tools.put(schema("ping", "Cek apakah server apkx hidup", new JSONObject()));

        JSONObject infoArgs = new JSONObject();
        infoArgs.put("apk", str("APK sebagai base64 atau path relatif di inbox"));
        tools.put(schema("apk_info", "Ukuran, jumlah entry, dan daftar dex APK", infoArgs));

        JSONObject decArgs = new JSONObject();
        decArgs.put("apk", str("APK sebagai base64 atau path relatif di inbox"));
        decArgs.put("name", str("Nama folder hasil decode"));
        decArgs.put("jobs", num("Jumlah thread smali, default 4"));
        tools.put(schema("apk_decode", "Decode APK menjadi smali tanpa mendekode resource", decArgs));

        JSONObject modArgs = new JSONObject();
        modArgs.put("dir", str("Nama folder hasil decode"));
        modArgs.put("path", str("Path relatif file di dalam folder tersebut"));
        modArgs.put("op", str("read, write, atau delete"));
        modArgs.put("contentB64", str("Isi file untuk op write"));
        tools.put(schema("apk_modify", "Baca, tulis, atau hapus file hasil decode", modArgs));

        JSONObject buildArgs = new JSONObject();
        buildArgs.put("dir", str("Nama folder hasil decode"));
        buildArgs.put("outName", str("Nama APK hasil"));
        buildArgs.put("api", num("API level dex, default 26"));
        buildArgs.put("outB64", str("true untuk mengembalikan isi APK sebagai base64"));
        tools.put(schema("apk_build", "Assemble smali menjadi dex dan bungkus ulang APK", buildArgs));

        return tools;
    }

    public static JSONObject call(String name, JSONObject args) {
        try {
            switch (name) {
                case "ping": return text("pong");
                case "apk_info": return info(args);
                case "apk_decode": return decode(args);
                case "apk_modify": return modify(args);
                case "apk_build": return build(args);
                default: return error("Tool tidak dikenal: " + name);
            }
        } catch (Throwable t) {
            return error(String.valueOf(t));
        }
    }

    private static JSONObject info(JSONObject a) throws Exception {
        File apk = writeInput(a);
        JSONObject r = new JSONObject();
        r.put("path", rel(apk));
        r.put("size", apk.length());
        List<String> dex = new ArrayList<>();
        List<String> libs = new ArrayList<>();
        int entries = 0;
        try (ZipFile z = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> en = z.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                entries++;
                String n = e.getName();
                if (n.endsWith(".dex")) dex.add(n);
                if (n.startsWith("lib/")) libs.add(n);
            }
        }
        r.put("entries", entries);
        r.put("dex", new JSONArray(dex));
        r.put("libs", new JSONArray(libs));
        return ok(r.toString(2));
    }

    private static JSONObject decode(JSONObject a) throws Exception {
        File apk = writeInput(a);
        String name = safe(a.optString("name", strip(apk.getName())));
        File out = new File(work, name);
        int jobs = a.optInt("jobs", 4);
        if (jobs < 1 || jobs > 16) jobs = 4;
        String log = ApkEngine.decode(apk.getAbsolutePath(), out.getAbsolutePath(), jobs);
        long files = count(out);
        JSONObject r = new JSONObject();
        r.put("dir", name);
        r.put("fileCount", files);
        r.put("log", tail(log));
        return ok(r.toString(2));
    }

    private static JSONObject modify(JSONObject a) throws Exception {
        String dir = safe(a.optString("dir", ""));
        if (dir.isEmpty()) throw new IllegalArgumentException("dir kosong");
        File base = resolve(work, dir);
        String path = a.optString("path", "");
        if (path.isEmpty()) throw new IllegalArgumentException("path kosong");
        File target = resolve(base, path);
        String op = a.optString("op", "read");
        switch (op) {
            case "read": {
                byte[] data = readAll(new FileInputStream(target));
                JSONObject r = new JSONObject();
                r.put("contentB64", Base64.encodeToString(data, Base64.NO_WRAP));
                r.put("size", data.length);
                return ok(r.toString(2));
            }
            case "write": {
                byte[] data = Base64.decode(a.optString("contentB64", ""), Base64.DEFAULT);
                target.getParentFile().mkdirs();
                try (FileOutputStream os = new FileOutputStream(target)) { os.write(data); }
                return ok("{\"written\":" + data.length + "}");
            }
            case "delete": {
                if (!target.delete()) throw new IOException("Gagal hapus " + path);
                return ok("{\"deleted\":true}");
            }
            case "list": {
                File[] kids = target.isDirectory() ? target.listFiles() : new File[0];
                JSONArray arr = new JSONArray();
                if (kids != null) for (File k : kids) arr.put(rel(k));
                return ok(arr.toString(2));
            }
            default: throw new IllegalArgumentException("op tidak dikenal: " + op);
        }
    }

    private static JSONObject build(JSONObject a) throws Exception {
        String dir = safe(a.optString("dir", ""));
        if (dir.isEmpty()) throw new IllegalArgumentException("dir kosong");
        File decoded = resolve(work, dir);
        File original = new File(inbox, strip(new File(decoded, "apktool.yml").exists()
                ? originalName(decoded) : dir) + ".apk");
        if (!original.exists()) {
            File[] candidates = inbox.listFiles((d, n) -> n.endsWith(".apk"));
            if (candidates != null && candidates.length == 1) original = candidates[0];
        }
        if (!original.exists()) throw new IOException("APK asal tidak ditemukan di inbox");

        String outName = safe(a.optString("outName", dir + "-rebuilt.apk"));
        if (!outName.endsWith(".apk")) outName += ".apk";
        File out = new File(files, outName);
        int api = a.optInt("api", 26);
        Repack.build(decoded, original, out, api);

        JSONObject r = new JSONObject();
        r.put("path", rel(out));
        r.put("size", out.length());
        if (a.optBoolean("outB64", false)) {
            r.put("contentB64", Base64.encodeToString(readAll(new FileInputStream(out)), Base64.NO_WRAP));
        }
        return ok(r.toString(2));
    }

    private static File writeInput(JSONObject a) throws Exception {
        String b64 = a.optString("contentB64", "");
        String path = a.optString("path", "");
        if (!b64.isEmpty()) {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            String name = safe(a.optString("name", "input.apk"));
            if (!name.endsWith(".apk")) name += ".apk";
            File f = new File(inbox, name);
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
            return f;
        }
        if (!path.isEmpty()) return resolve(inbox, path);
        throw new IllegalArgumentException("Butuh contentB64 atau path");
    }

    private static String originalName(File decoded) {
        try {
            for (String line : new String(readAll(new FileInputStream(new File(decoded, "apktool.yml")))).split("\n")) {
                String s = line.trim();
                if (s.startsWith("apkFileName:")) return strip(s.substring(12).trim());
            }
        } catch (Exception ignored) {}
        return decoded.getName();
    }

    private static File resolve(File base, String rel) throws Exception {
        File f = new File(base, rel);
        String root = base.getCanonicalPath() + File.separator;
        String target = f.getCanonicalPath();
        if (!target.startsWith(root)) throw new IllegalArgumentException("Path keluar dari workspace");
        return f;
    }

    private static String safe(String s) {
        if (s == null) return "output";
        String r = s.replaceAll("[^A-Za-z0-9._-]", "_");
        return r.isEmpty() ? "output" : r;
    }

    private static String strip(String s) {
        if (s == null) return "input";
        String r = s.replace(".apk", "");
        return new File(r).getName().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String rel(File f) {
        try {
            String root = files.getCanonicalPath() + File.separator;
            String p = f.getCanonicalPath();
            return p.startsWith(root) ? p.substring(root.length()) : p;
        } catch (IOException e) { return f.getAbsolutePath(); }
    }

    private static long count(File dir) {
        if (dir.isFile()) return 1;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        long n = 0;
        for (File k : kids) n += count(k);
        return n;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static void delete(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) delete(k);
        }
        f.delete();
    }

    private static JSONObject str(String desc) throws JSONException {
        return new JSONObject().put("type", "string").put("description", desc);
    }

    private static JSONObject num(String desc) throws JSONException {
        return new JSONObject().put("type", "integer").put("description", desc);
    }

    private static JSONObject schema(String name, String desc, JSONObject props) throws JSONException {
        return new JSONObject()
                .put("name", name)
                .put("description", desc)
                .put("inputSchema", new JSONObject()
                        .put("type", "object")
                        .put("properties", props));
    }

    private static JSONObject text(String msg) throws JSONException {
        return ok(msg);
    }

    private static JSONObject ok(String text) throws JSONException {
        return new JSONObject()
                .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", text)))
                .put("isError", false);
    }

    private static JSONObject error(String msg) {
        try {
            return new JSONObject()
                    .put("content", new JSONArray().put(new JSONObject().put("type", "text").put("text", msg)))
                    .put("isError", true);
        } catch (JSONException e) { throw new RuntimeException(e); }
    }

    private static String tail(String s) {
        int max = 4000;
        return s.length() > max ? s.substring(s.length() - max) : s;
    }
}
