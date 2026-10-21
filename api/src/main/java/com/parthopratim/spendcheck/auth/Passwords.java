package com.parthopratim.spendcheck.auth;

import java.nio.charset.StandardCharsets;

public final class Passwords {

    /** BCrypt only reads 72 bytes; Spring Security's encoder throws beyond that. */
    public static final int BCRYPT_MAX_BYTES = 72;

    private Passwords() {
    }

    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= BCRYPT_MAX_BYTES;
    }
}
