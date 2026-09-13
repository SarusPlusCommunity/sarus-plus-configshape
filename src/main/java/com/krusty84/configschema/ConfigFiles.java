/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

final class ConfigFiles {
    private ConfigFiles() {
    }

    static List<Path> collect(CliOptions options) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path input : options.inputs) {
            if (!Files.exists(input)) {
                System.err.println("Skipping missing path: " + input);
                continue;
            }
            if (Files.isRegularFile(input)) {
                if (matchesExtension(input, options.extensions))
                    result.add(input.toAbsolutePath().normalize());
                continue;
            }
            if (Files.isDirectory(input)) {
                int depth = options.recursive ? Integer.MAX_VALUE : 1;
                try (Stream<Path> stream = Files.walk(input, depth)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> matchesExtension(path, options.extensions))
                            .map(path -> path.toAbsolutePath().normalize())
                            .forEach(result::add);
                }
            }
        }
        return result.stream().distinct().sorted().toList();
    }

    private static boolean matchesExtension(Path path, Set<String> extensions) {
        if (extensions.isEmpty())
            return true;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(ext -> name.endsWith(ext.toLowerCase(Locale.ROOT)));
    }

    static Path schemaPath(Path input, CliOptions options) {
        Path schemaDir = options.outputDir;
        if (options.preserveTree) {
            for (Path root : options.inputs) {
                Path normalizedRoot = root.toAbsolutePath().normalize();
                if (Files.isDirectory(normalizedRoot) && input.startsWith(normalizedRoot)) {
                    Path parent = normalizedRoot.relativize(input).getParent();
                    if (parent != null)
                        schemaDir = schemaDir.resolve(parent);
                    break;
                }
            }
        }
        String schemaName = input.getFileName() + ".schema";
        Path schemaPath = schemaDir.resolve(schemaName);
        if (Files.exists(schemaPath))
            return schemaPath;
        Path prefixedPath = schemaDir.resolve("json" + schemaName);
        return Files.exists(prefixedPath) ? prefixedPath : schemaPath;
    }
}
