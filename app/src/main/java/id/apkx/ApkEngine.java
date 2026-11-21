package id.apkx;

import android.content.Context;
import dalvik.system.InMemoryDexClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public final class ApkEngine {
    private static ClassLoader loader;
    private static Method apktoolMain;
    private static Constructor<?> opcodesCtor, dexBuilderCtor, storeCtor, smaliCtor;
    private static Class<?> dexBuilderClass, storeClass;
    private static Method writeTo, buildFile;

    private ApkEngine() {}

    public static void init(Context ctx) throws Exception {
        byte[] dex;
        try (InputStream in = ctx.getAssets().open("apktool.dex")) {
            dex = readAll(in);
        }
        File home = new File(ctx.getFilesDir(), "apktool-home");
        File tmp = new File(home, "tmp");
        home.mkdirs();
        tmp.mkdirs();
        System.setProperty("user.home", home.getAbsolutePath());
        System.setProperty("java.io.tmpdir", tmp.getAbsolutePath());

        loader = new InMemoryDexClassLoader(ByteBuffer.wrap(dex), ApkEngine.class.getClassLoader());
        apktoolMain = loader.loadClass("brut.apktool.Main").getMethod("main", String[].class);

        Class<?> opcodes = loader.loadClass("com.android.tools.smali.dexlib2.Opcodes");
        opcodesCtor = opcodes.getConstructor(int.class);
        dexBuilderClass = loader.loadClass("com.android.tools.smali.dexlib2.writer.builder.DexBuilder");
        dexBuilderCtor = dexBuilderClass.getConstructor(opcodes);
        storeClass = loader.loadClass("com.android.tools.smali.dexlib2.writer.io.FileDataStore");
        storeCtor = storeClass.getConstructor(File.class);
        writeTo = dexBuilderClass.getMethod("writeTo", storeClass);

        Class<?> smaliBuilder = loader.loadClass("brut.androlib.smali.SmaliBuilder");
        smaliCtor = smaliBuilder.getConstructor(int.class);
        buildFile = smaliBuilder.getMethod("buildFile", File.class, dexBuilderClass);
    }

    public static synchronized String decode(String apk, String out, int jobs) throws Exception {
        PrintStream oldOut = System.out;
        PrintStream oldErr = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(buf, true, "UTF-8")) {
            System.setOut(ps);
            System.setErr(ps);
            String[] args = {"d", "-f", "-r", "-j", String.valueOf(jobs), "-o", out, apk};
            try {
                apktoolMain.invoke(null, (Object) args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new Exception("apktool gagal: " + cause, cause);
            }
        } finally {
            System.setOut(oldOut);
            System.setErr(oldErr);
        }
        return buf.toString("UTF-8");
    }

    public static synchronized void buildDex(File smaliDir, File outDex, int api) throws Exception {
        Object opcodes = opcodesCtor.newInstance(api);
        Object builder = dexBuilderCtor.newInstance(opcodes);
        Object smali = smaliCtor.newInstance(api);
        Object ok = buildFile.invoke(smali, smaliDir, builder);
        if (ok == null || !Boolean.TRUE.equals(ok)) {
            throw new Exception("Gagal assemble smali di " + smaliDir.getName());
        }
        writeTo.invoke(builder, storeCtor.newInstance(outDex));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
