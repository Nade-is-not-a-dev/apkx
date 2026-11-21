package id.apkx;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class Repack {
    private Repack() {}

    public static void build(File decoded, File original, File out, int api) throws Exception {
        File tmp = new File(decoded, ".dexbuild");
        tmp.mkdirs();

        Map<String, File> replace = new HashMap<>();
        File smali = new File(decoded, "smali");
        if (smali.isDirectory()) {
            replace.put("classes.dex", assemble(smali, new File(tmp, "classes.dex"), api));
        }
        for (int i = 2; ; i++) {
            File dir = new File(decoded, "smali_classes" + i);
            if (!dir.isDirectory()) break;
            replace.put("classes" + i + ".dex", assemble(dir, new File(tmp, "classes" + i + ".dex"), api));
        }
        if (replace.isEmpty()) throw new Exception("Tidak ada folder smali di " + decoded.getName());

        try (ZipFile in = new ZipFile(original);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            Set<String> seen = new HashSet<>();
            Enumeration<? extends ZipEntry> entries = in.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("META-INF/") &&
                        (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA"))) continue;

                byte[] data;
                File dex = replace.get(name);
                if (dex != null) {
                    data = readAll(new FileInputStream(dex));
                } else {
                    try (InputStream is = in.getInputStream(entry)) { data = readAll(is); }
                }

                int method = name.equals("resources.arsc") || name.endsWith(".so")
                        ? ZipEntry.STORED : ZipEntry.DEFLATED;
                ZipEntry next = new ZipEntry(name);
                next.setMethod(method);
                if (method == ZipEntry.STORED) {
                    CRC32 crc = new CRC32();
                    crc.update(data);
                    next.setSize(data.length);
                    next.setCompressedSize(data.length);
                    next.setCrc(crc.getValue());
                }
                zos.putNextEntry(next);
                zos.write(data);
                zos.closeEntry();
                seen.add(name);
            }

            for (Map.Entry<String, File> extra : replace.entrySet()) {
                if (seen.contains(extra.getKey())) continue;
                byte[] data = readAll(new FileInputStream(extra.getValue()));
                ZipEntry next = new ZipEntry(extra.getKey());
                next.setMethod(ZipEntry.DEFLATED);
                zos.putNextEntry(next);
                zos.write(data);
                zos.closeEntry();
            }
        }
        delete(tmp);
    }

    private static File assemble(File smali, File out, int api) throws Exception {
        ApkEngine.buildDex(smali, out, api);
        return out;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
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
}
