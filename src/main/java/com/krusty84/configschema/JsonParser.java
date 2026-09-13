/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.math.BigDecimal;
import java.util.*;

/** Minimal JSON parser for configuration schemas. */
final class JsonParser {
    private final String s;
    private int p;

    JsonParser(String s) {
        this.s = s;
    }

    Object parse() {
        skipWs();
        Object v = value();
        skipWs();
        if (p != s.length())
            fail("Unexpected trailing content");
        return v;
    }

    private Object value() {
        skipWs();
        if (p >= s.length())
            fail("Unexpected end of JSON");
        return switch (s.charAt(p)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        expect('{');
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        skipWs();
        if (take('}'))
            return m;
        while (true) {
            skipWs();
            if (p >= s.length() || s.charAt(p) != '"')
                fail("Expected object key");
            String k = string();
            skipWs();
            expect(':');
            m.put(k, value());
            skipWs();
            if (take('}'))
                return m;
            expect(',');
        }
    }

    private List<Object> array() {
        expect('[');
        ArrayList<Object> a = new ArrayList<>();
        skipWs();
        if (take(']'))
            return a;
        while (true) {
            a.add(value());
            skipWs();
            if (take(']'))
                return a;
            expect(',');
        }
    }

    private String string() {
        expect('"');
        StringBuilder b = new StringBuilder();
        while (p < s.length()) {
            char c = s.charAt(p++);
            if (c == '"')
                return b.toString();
            if (c != '\\') {
                b.append(c);
                continue;
            }
            if (p >= s.length())
                fail("Invalid escape sequence");
            char e = s.charAt(p++);
            switch (e) {
                case '"', '\\', '/' -> b.append(e);
                case 'b' -> b.append('\b');
                case 'f' -> b.append('\f');
                case 'n' -> b.append('\n');
                case 'r' -> b.append('\r');
                case 't' -> b.append('\t');
                case 'u' -> {
                    if (p + 4 > s.length())
                        fail("Invalid unicode escape");
                    try {
                        b.append((char) Integer.parseInt(s.substring(p, p + 4), 16));
                    } catch (NumberFormatException ex) {
                        fail("Invalid unicode escape");
                    }
                    p += 4;
                }
                default -> fail("Unknown escape \\" + e);
            }
        }
        fail("Unterminated string");
        return null;
    }

    private Object number() {
        int start = p;
        if (p < s.length() && s.charAt(p) == '-')
            p++;
        while (p < s.length() && Character.isDigit(s.charAt(p)))
            p++;
        boolean decimal = false;
        if (p < s.length() && s.charAt(p) == '.') {
            decimal = true;
            p++;
            while (p < s.length() && Character.isDigit(s.charAt(p)))
                p++;
        }
        if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
            decimal = true;
            p++;
            if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-'))
                p++;
            while (p < s.length() && Character.isDigit(s.charAt(p)))
                p++;
        }
        if (start == p)
            fail("Expected JSON value");
        String n = s.substring(start, p);
        try {
            return decimal ? new BigDecimal(n) : Long.parseLong(n);
        } catch (NumberFormatException e) {
            try {
                return new BigDecimal(n);
            } catch (NumberFormatException x) {
                fail("Invalid number");
                return null;
            }
        }
    }

    private Object literal(String text, Object value) {
        if (!s.startsWith(text, p))
            fail("Expected " + text);
        p += text.length();
        return value;
    }

    private void skipWs() {
        while (p < s.length() && Character.isWhitespace(s.charAt(p)))
            p++;
    }

    private boolean take(char c) {
        if (p < s.length() && s.charAt(p) == c) {
            p++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        skipWs();
        if (!take(c))
            fail("Expected '" + c + "'");
    }

    private void fail(String message) {
        throw new IllegalArgumentException(message + " at character " + p);
    }
}
