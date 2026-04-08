package com.lan4jn.file.utils;

public final class HttpRangeUtils {

    private HttpRangeUtils() {
    }

    public static ByteRange parseSingleRange(String rangeHeader, long fileSize) {
        if (rangeHeader == null || rangeHeader.trim().isEmpty()) {
            return null;
        }
        if (fileSize <= 0) {
            throw new IllegalArgumentException("文件大小无效");
        }

        String normalized = rangeHeader.trim();
        if (!normalized.startsWith("bytes=")) {
            throw new IllegalArgumentException("仅支持 bytes Range");
        }

        String value = normalized.substring("bytes=".length()).trim();
        if (value.isEmpty() || value.contains(",")) {
            throw new IllegalArgumentException("仅支持单个 Range");
        }

        int dashIndex = value.indexOf('-');
        if (dashIndex < 0) {
            throw new IllegalArgumentException("Range 格式无效");
        }

        String startPart = value.substring(0, dashIndex).trim();
        String endPart = value.substring(dashIndex + 1).trim();

        long start;
        long end;

        if (startPart.isEmpty()) {
            long suffixLength = parsePositiveLong(endPart, "Range 后缀长度无效");
            if (suffixLength > fileSize) {
                suffixLength = fileSize;
            }
            start = fileSize - suffixLength;
            end = fileSize - 1;
        } else {
            start = parseNonNegativeLong(startPart, "Range 起始位置无效");
            end = endPart.isEmpty() ? fileSize - 1 : parseNonNegativeLong(endPart, "Range 结束位置无效");
        }

        if (start >= fileSize || start < 0 || end < start) {
            throw new IllegalArgumentException("Range 超出文件范围");
        }
        if (end >= fileSize) {
            end = fileSize - 1;
        }

        return new ByteRange(start, end, fileSize);
    }

    private static long parsePositiveLong(String value, String message) {
        long parsed = parseNonNegativeLong(value, message);
        if (parsed <= 0) {
            throw new IllegalArgumentException(message);
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    public static final class ByteRange {
        private final long start;
        private final long end;
        private final long fileSize;

        public ByteRange(long start, long end, long fileSize) {
            this.start = start;
            this.end = end;
            this.fileSize = fileSize;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public long getFileSize() {
            return fileSize;
        }

        public long getLength() {
            return end - start + 1;
        }

        public String toContentRange() {
            return "bytes " + start + "-" + end + "/" + fileSize;
        }
    }
}
