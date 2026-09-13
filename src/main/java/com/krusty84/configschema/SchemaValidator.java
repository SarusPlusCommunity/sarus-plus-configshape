/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

final class SchemaValidator {
    private SchemaValidator() {
    }

    static List<String> validate(ConfigData config, Path schemaPath) throws IOException {
        Object parsed;
        try {
            parsed = new JsonParser(Files.readString(schemaPath, StandardCharsets.UTF_8)).parse();
        } catch (RuntimeException e) {
            return List.of("Invalid JSON schema " + schemaPath + ": " + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> root))
            return List.of("Schema root must be a JSON object");

        List<String> errors = new ArrayList<>();
        if (!(root.get("title") instanceof String))
            errors.add("Schema 'title' must be a string");
        if (!root.containsKey("documentation") ||
                (root.get("documentation") != null && !(root.get("documentation") instanceof String))) {
            errors.add("Schema 'documentation' must be a string or null");
        }
        Object propsObj = root.get("properties");
        if (!(propsObj instanceof Map<?, ?> properties))
            return List.of("Schema has no object 'properties'");

        for (Map.Entry<?, ?> property : properties.entrySet()) {
            if (!(property.getValue() instanceof Map<?, ?> rule)) {
                errors.add(property.getKey() + ": schema property must be an object");
                continue;
            }
            if (!(rule.get("description") instanceof String)) {
                errors.add(property.getKey() + ": schema 'description' must be a string");
            }
            if (!rule.containsKey("default"))
                errors.add(property.getKey() + ": schema 'default' is missing");
        }

        for (Map.Entry<String, ConfigData.Entry> cfg : config.entries().entrySet()) {
            if (!properties.containsKey(cfg.getKey())) {
                errors.add(cfg.getKey() + " (line " + cfg.getValue().line() + "): parameter is not defined in schema");
            }
        }
        return errors;
    }
}
