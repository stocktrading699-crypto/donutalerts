package com.dasa.donutalerts;

import java.util.Locale;

public final class Fmt {
    private Fmt() {}

    public static double mult(String suffix) {
        switch (suffix.toUpperCase(Locale.ROOT)) {
            case "K": return 1e3;
            case "M": return 1e6;
            case "B": return 1e9;
            case "T": return 1e12;
            default: return 1;
        }
    }

    /** Parses things like 1m, 2.5M, 500k, $1,000,000. Returns null if invalid. */
    public static Double parseLoose(String s) {
        if (s == null) return null;
        s = s.trim().replace("$", "").replace(",", "").replace(" ", "");
        if (s.isEmpty()) return null;
        String suffix = "";
        char last = s.charAt(s.length() - 1);
        if (Character.isLetter(last)) {
            if ("kmbtKMBT".indexOf(last) < 0) return null;
            suffix = String.valueOf(last);
            s = s.substring(0, s.length() - 1);
        }
        try {
            return Double.parseDouble(s) * mult(suffix);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "12s", "5m", "1h" or plain seconds. Returns seconds, or -1 if invalid. */
    public static int parseDuration(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return -1;
        double mul = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 's' || last == 'm' || last == 'h') {
            mul = last == 'm' ? 60 : last == 'h' ? 3600 : 1;
            s = s.substring(0, s.length() - 1);
        }
        try {
            double v = Double.parseDouble(s) * mul;
            return v > 0 ? (int) Math.round(v) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String trim(double d) {
        String s = String.format(Locale.US, "%.2f", d);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            s = s.replaceAll("\\.$", "");
        }
        return s;
    }

    public static String shortNum(double v) {
        if (v >= 1e12) return trim(v / 1e12) + "T";
        if (v >= 1e9) return trim(v / 1e9) + "B";
        if (v >= 1e6) return trim(v / 1e6) + "M";
        if (v >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }

    public static String money(double v) {
        return "$" + shortNum(v);
    }

    public static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }
}
