package ravenworks.fizz.common.util;

import java.security.SecureRandom;
import java.time.Instant;


public final class UUIDv7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UUIDv7() {
    }

    public static String generate() {
        Instant now = Instant.now();
        long millis = now.toEpochMilli();

        byte[] bytes = new byte[16];
        bytes[0] = (byte) (millis >> 40);
        bytes[1] = (byte) (millis >> 32);
        bytes[2] = (byte) (millis >> 24);
        bytes[3] = (byte) (millis >> 16);
        bytes[4] = (byte) (millis >> 8);
        bytes[5] = (byte) millis;

        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);
        System.arraycopy(rand, 0, bytes, 6, 10);

        bytes[6] = (byte) ((bytes[6] & 0x0F) | 0x70);
        bytes[8] = (byte) ((bytes[8] & 0x3F) | 0x80);

        return bytesToHex(bytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

}
