/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.util.LinkedHashMap;

record ConfigData(LinkedHashMap<String, Entry> entries) {
    record Entry(String value, String description, int line) {
    }
}
