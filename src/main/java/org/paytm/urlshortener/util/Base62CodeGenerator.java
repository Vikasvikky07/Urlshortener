package org.paytm.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62CodeGenerator {
    private static final char[] ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    public static final String GENERATED_PREFIX = "u_";

    public String encode(long value) {
        if (value < 1) {
            throw new IllegalArgumentException("Short-code source id must be positive");
        }

        StringBuilder code = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            int index = (int) (remaining % ALPHABET.length);
            code.append(ALPHABET[index]);
            remaining = remaining / ALPHABET.length;
        }
        return GENERATED_PREFIX + code.reverse();
    }
}
