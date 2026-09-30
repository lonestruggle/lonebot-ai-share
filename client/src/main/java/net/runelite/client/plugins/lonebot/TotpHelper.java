package net.runelite.client.plugins.lonebot;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * RFC 6238 TOTP (6 digits, 30s, HMAC-SHA1) — zelfde als pyotp / Google Authenticator.
 */
public final class TotpHelper {

    private TotpHelper() {
    }

    /** Huidige 6-cijferige code, of {@code ""} bij ongeldige secret. */
    public static String now(String secretBase32) {
        String norm = normalizeSecret(secretBase32);
        if (norm.isEmpty()) {
            return "";
        }
        try {
            byte[] key = decodeBase32(norm);
            if (key.length == 0) {
                return "";
            }
            long counter = System.currentTimeMillis() / 1000L / 30L;
            return hotp(key, counter);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Seconden tot volgende code (1–30). */
    public static int secondsRemaining() {
        int rem = 30 - (int) (System.currentTimeMillis() / 1000L % 30L);
        return rem == 0 ? 30 : rem;
    }

    public static String normalizeSecret(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        // Strip otpauth://…?secret=XXX
        int idx = s.indexOf("SECRET=");
        if (idx >= 0) {
            s = s.substring(idx + 7);
            int amp = s.indexOf('&');
            if (amp > 0) {
                s = s.substring(0, amp);
            }
        }
        return s;
    }

    public static boolean hasSecret(String raw) {
        return normalizeSecret(raw).length() >= 16;
    }

    private static String hotp(byte[] key, long counter) throws Exception {
        byte[] data = ByteBuffer.allocate(8).putLong(counter).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format(Locale.ROOT, "%06d", otp);
    }

    /** RFC 4648 Base32 (A–Z2–7), padding optioneel. */
    static byte[] decodeBase32(String input) {
        String s = input.replace("=", "");
        if (s.isEmpty()) {
            return new byte[0];
        }
        int bitBuffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int val = base32Value(c);
            if (val < 0) {
                continue;
            }
            bitBuffer = (bitBuffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((bitBuffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static int base32Value(char c) {
        if (c >= 'A' && c <= 'Z') {
            return c - 'A';
        }
        if (c >= '2' && c <= '7') {
            return 26 + (c - '2');
        }
        return -1;
    }
}
