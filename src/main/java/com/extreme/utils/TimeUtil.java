package com.extreme.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parseo y formato de duraciones: 500ms, 30s, 15m, 2h, 1h30m, 7d, 1w. Un número solo = segundos. */
public final class TimeUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)(ms|s|m|h|d|w)");

    private TimeUtil() {}

    public static long parse(String input, long defMs) {
        if (input == null) return defMs;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) return defMs;
        if (s.matches("\\d+")) return Long.parseLong(s) * 1000L;
        Matcher m = PART.matcher(s);
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return defMs;
            total += Long.parseLong(m.group(1)) * unit(m.group(2));
            end = m.end();
        }
        return (end == s.length() && total > 0) ? total : defMs;
    }

    private static long unit(String u) {
        return switch (u) {
            case "ms" -> 1L;
            case "s" -> 1000L;
            case "m" -> 60_000L;
            case "h" -> 3_600_000L;
            case "d" -> 86_400_000L;
            default -> 604_800_000L;
        };
    }

    /** 5400000 -> "1h30m" */
    public static String format(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s == 0) return "0s";
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append('d');
        if (h > 0) sb.append(h).append('h');
        if (m > 0) sb.append(m).append('m');
        if (sec > 0) sb.append(sec).append('s');
        return sb.toString();
    }

    /** 3725000 -> "1:02:05" / 125000 -> "02:05" */
    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%02d:%02d", m, sec);
    }
}
