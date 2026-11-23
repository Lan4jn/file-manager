package com.lan4jn.file.controller;

import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password login and certificate enrollment must accept exactly the same stored formats. */
final class StoredPassword {
    private StoredPassword() {}

    static boolean isBcrypt(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    static boolean matches(String raw, String stored, PasswordEncoder encoder) {
        if (stored == null || stored.isBlank()) return false;
        return isBcrypt(stored) ? BCrypt.checkpw(raw, stored) : encoder.matches(raw, stored);
    }
}
