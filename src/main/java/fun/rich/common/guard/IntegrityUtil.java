package fun.rich.common.guard;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Locale;

public final class IntegrityUtil {
    private IntegrityUtil() {
    }

    public static boolean hasResource(String path) {
        return readResource(path) != null;
    }

    public static byte[] readResource(String path) {
        String p = normalizePath(path);
        if (p.isEmpty()) return null;

        try (InputStream in = IntegrityUtil.class.getResourceAsStream(p)) {
            if (in == null) return null;

            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) != -1) {
                if (r > 0) out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static byte[] readClassBytes(Class<?> cls) {
        if (cls == null) return null;
        String path = classResourcePath(cls);
        return readResource(path);
    }

    public static byte[] readClassBytes(String className) {
        if (className == null || className.trim().isEmpty()) return null;
        String path = "/" + className.trim().replace('.', '/') + ".class";
        return readResource(path);
    }

    public static String classResourcePath(Class<?> cls) {
        if (cls == null) return "";
        String name = cls.getName();
        return "/" + name.replace('.', '/') + ".class";
    }

    public static String sha256Hex(byte[] data) {
        if (data == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return toHex(digest);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static String sha256HexOfResource(String path) {
        return sha256Hex(readResource(path));
    }

    public static String sha256HexOfClass(Class<?> cls) {
        return sha256Hex(readClassBytes(cls));
    }

    public static String sha256HexOfClass(String className) {
        return sha256Hex(readClassBytes(className));
    }

    public static boolean verifyResourceHash(String path, String expectedSha256Hex) {
        String expected = normalizeHex(expectedSha256Hex);
        if (expected.isEmpty()) return false;
        String actual = normalizeHex(sha256HexOfResource(path));
        return !actual.isEmpty() && actual.equals(expected);
    }

    public static boolean verifyClassHash(Class<?> cls, String expectedSha256Hex) {
        String expected = normalizeHex(expectedSha256Hex);
        if (expected.isEmpty()) return false;
        String actual = normalizeHex(sha256HexOfClass(cls));
        return !actual.isEmpty() && actual.equals(expected);
    }

    public static boolean verifyClassHash(String className, String expectedSha256Hex) {
        String expected = normalizeHex(expectedSha256Hex);
        if (expected.isEmpty()) return false;
        String actual = normalizeHex(sha256HexOfClass(className));
        return !actual.isEmpty() && actual.equals(expected);
    }

    public static boolean looksLikeSha256(String hex) {
        String v = normalizeHex(hex);
        return v.length() == 64;
    }

    public static String normalizeHex(String hex) {
        if (hex == null) return "";
        String v = hex.trim().toLowerCase(Locale.ROOT);
        if (v.startsWith("sha256:")) v = v.substring(7).trim();

        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lowerHex = c >= 'a' && c <= 'f';
            if (digit || lowerHex) sb.append(c);
        }
        return sb.toString();
    }

    public static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        char[] out = new char[bytes.length * 2];
        int j = 0;
        for (byte b : bytes) {
            int v = b & 0xFF;
            out[j++] = hexChar((v >>> 4) & 0xF);
            out[j++] = hexChar(v & 0xF);
        }
        return new String(out);
    }

    private static char hexChar(int v) {
        return (char) (v < 10 ? ('0' + v) : ('a' + (v - 10)));
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.isEmpty()) return "";
        if (!p.startsWith("/")) p = "/" + p;
        return p;
    }
}