package com.doppelganger113.commandrunner.hash;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
public class ShaHash {

    private final MessageDigest digest;

    public ShaHash() {
        try {
            digest = MessageDigest.getInstance("SHA3-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * When dealing with JSON we want to consider null field values and non-existing fields the same, thus
     * we use this function to exclude null values from a map.
     */
    private static <K, V> Map<K, V> excludeNullValues(Map<K, V> map) {
        if (map == null) {
            return null;
        }
        Map<K, V> result = new HashMap<>();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue();

            if (value instanceof Map) {
                // Recursively exclude null values from nested maps
                value = (V) excludeNullValues((Map<K, V>) value);
            }

            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    public String hash(HashMap<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        var sortedMap = new TreeMap<>(excludeNullValues(map));
        byte[] hashbytes = digest.digest(
                sortedMap.toString().getBytes(StandardCharsets.UTF_8)
        );
        return hexEncode(hashbytes);
    }

    static String hexEncode(byte[] data) {
        StringBuilder builder = new StringBuilder();
        for (byte b : data) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }
}
