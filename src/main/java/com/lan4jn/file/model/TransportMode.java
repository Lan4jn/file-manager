package com.lan4jn.file.model;

import java.util.Locale;

public enum TransportMode {
    APPLICATION,
    PROXY,
    DIRECT,
    HYBRID;

    public static TransportMode from(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return APPLICATION;
        }
        try {
            return TransportMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return APPLICATION;
        }
    }
}
