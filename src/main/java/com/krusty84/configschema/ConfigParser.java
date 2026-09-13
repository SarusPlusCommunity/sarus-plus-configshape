/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class ConfigParser {
    private ConfigParser() {
    }

    static ConfigData parse(Path input) throws IOException {
        LinkedHashMap<String, ConfigData.Entry> entries = new LinkedHashMap<>();
        List<String> pendingComments = new ArrayList<>();
        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);

        for (int i = 0; i < lines.size(); i++) {
            String original = lines.get(i);
            String line = original.trim();

            if (line.isEmpty()) {
                pendingComments.clear();
                continue;
            }

            if (isFullLineComment(line)) {
                pendingComments.add(stripCommentMarker(line));
                continue;
            }

            if (line.startsWith("export "))
                line = line.substring(7).trim();
            int eq = findUnquotedEquals(line);
            if (eq <= 0) {
                throw new IllegalArgumentException("Invalid line " + (i + 1) + ": " + original);
            }

            String key = line.substring(0, eq).trim();
            if (key.isEmpty())
                throw new IllegalArgumentException("Empty key at line " + (i + 1));

            String rawValue = line.substring(eq + 1).trim();
            ValueAndComment vc = splitInlineComment(rawValue);
            String value = unquote(vc.value.trim());

            List<String> comments = new ArrayList<>(pendingComments);
            if (vc.comment != null && !vc.comment.isBlank())
                comments.add(vc.comment.trim());
            pendingComments.clear();

            if (entries.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate key '" + key + "' at line " + (i + 1));
            }
            entries.put(key, new ConfigData.Entry(value, String.join("\n", comments), i + 1));
        }
        return new ConfigData(entries);
    }

    private static boolean isFullLineComment(String line) {
        return line.startsWith("#") || line.startsWith(";") || line.startsWith("!");
    }

    private static String stripCommentMarker(String line) {
        if (line.isEmpty())
            return line;
        return line.substring(1).trim();
    }

    private static int findUnquotedEquals(String line) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && quote == '"') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote)
                    quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if (c == '=')
                return i;
        }
        return -1;
    }

    private static ValueAndComment splitInlineComment(String raw) {
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && quote == '"') {
                escaped = true;
                continue;
            }
            if (quote != 0) {
                if (c == quote)
                    quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                continue;
            }
            if ((c == '#' || c == ';') && (i == 0 || Character.isWhitespace(raw.charAt(i - 1)))) {
                return new ValueAndComment(raw.substring(0, i).stripTrailing(), raw.substring(i + 1).trim());
            }
        }
        return new ValueAndComment(raw, null);
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char a = value.charAt(0), b = value.charAt(value.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\''))
                return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private record ValueAndComment(String value, String comment) {
    }
}
