package com.lan4jn.file.utils;

import java.util.ArrayList;
import java.util.List;

public final class RequestValidationUtils {

    private RequestValidationUtils() {
    }

    public static String requireText(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        rejectControlChars(normalized, fieldName);
        return normalized;
    }

    public static String optionalText(String value, String fieldName) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "";
        }
        rejectControlChars(normalized, fieldName);
        return normalized;
    }

    public static String nullableText(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        rejectControlChars(normalized, fieldName);
        return normalized;
    }

    public static Integer optionalPositive(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + "必须大于0");
        }
        return value;
    }

    public static List<String> requireTextList(List<String> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        List<String> normalizedValues = new ArrayList<>();
        for (String value : values) {
            normalizedValues.add(requireText(value, fieldName));
        }
        return normalizedValues;
    }

    public static String[] normalizeTextArray(String[] values, String fieldName) {
        if (values == null) {
            return null;
        }
        List<String> normalizedValues = new ArrayList<>();
        for (String value : values) {
            normalizedValues.add(requireText(value, fieldName));
        }
        return normalizedValues.toArray(new String[0]);
    }

    private static void rejectControlChars(String value, String fieldName) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new IllegalArgumentException(fieldName + "包含非法控制字符");
            }
        }
    }
}
