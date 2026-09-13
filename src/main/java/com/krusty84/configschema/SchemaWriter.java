/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

final class SchemaWriter {
    private SchemaWriter() {
    }

    static void write(Path input, ConfigData config, Path schemaPath) throws IOException {
        Files.writeString(schemaPath, generateSchema(input, config), StandardCharsets.UTF_8);
    }

    private static String generateSchema(Path input, ConfigData config) {
        StringBuilder out = new StringBuilder();
        out.append("{\n")
                .append("  \"title\": ")
                .append(jsonString(stripKnownExtension(input.getFileName().toString()) + " configuration"))
                .append(",\n")
                .append("  \"documentation\": null,\n")
                .append("  \"properties\": {\n");

        int index = 0;
        for (Map.Entry<String, ConfigData.Entry> entry : config.entries().entrySet()) {
            out.append("    ").append(jsonString(entry.getKey())).append(": ")
                    .append("{\n")
                    .append("      \"description\": ").append(jsonString(entry.getValue().description())).append(",\n")
                    .append("      \"default\": \"\"\n")
                    .append("    }");
            if (++index < config.entries().size())
                out.append(',');
            out.append('\n');
        }
        out.append("  }\n}\n");
        return out.toString();
    }

    private static String jsonString(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20)
                        b.append(String.format("\\u%04x", (int) c));
                    else
                        b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    private static String stripKnownExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
