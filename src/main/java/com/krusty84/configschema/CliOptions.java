/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.nio.file.*;
import java.util.*;

final class CliOptions {
    Path outputDir = Paths.get("config_schemas").toAbsolutePath().normalize();
    boolean recursive, preserveTree, help;
    Mode mode = Mode.CHECK_OR_CREATE;
    Set<String> extensions = new LinkedHashSet<>(List.of(".env", ".conf", ".config", ".cfg", ".properties"));
    List<Path> inputs = new ArrayList<>();

    static CliOptions parse(String[] args) {
        CliOptions o = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-h", "--help" -> o.help = true;
                case "-r", "--recursive" -> o.recursive = true;
                case "--preserve-tree" -> o.preserveTree = true;
                case "--all-files" -> o.extensions = Set.of();
                case "--generate" -> {
                    if (o.mode == Mode.VALIDATE_ONLY)
                        throw new IllegalArgumentException("--generate and --validate-only are mutually exclusive");
                    o.mode = Mode.GENERATE;
                }
                case "--validate-only" -> {
                    if (o.mode == Mode.GENERATE)
                        throw new IllegalArgumentException("--generate and --validate-only are mutually exclusive");
                    o.mode = Mode.VALIDATE_ONLY;
                }
                case "-o", "--output" -> {
                    if (++i >= args.length)
                        throw new IllegalArgumentException("Missing output directory");
                    o.outputDir = Paths.get(args[i]).toAbsolutePath().normalize();
                }
                case "--extensions" -> {
                    if (++i >= args.length)
                        throw new IllegalArgumentException("Missing extension list");
                    LinkedHashSet<String> set = new LinkedHashSet<>();
                    for (String item : args[i].split(",")) {
                        String ext = item.trim();
                        if (!ext.isEmpty())
                            set.add(ext.startsWith(".") ? ext : "." + ext);
                    }
                    o.extensions = set;
                }
                default -> {
                    if (args[i].startsWith("-"))
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    o.inputs.add(Paths.get(args[i]));
                }
            }
        }
        return o;
    }

    enum Mode {
        CHECK_OR_CREATE, GENERATE, VALIDATE_ONLY
    }

    static void printUsage() {
        System.out.println("""
                   ╔═══════════════════════════════╗
                   ║     sarus-plus-configshape    ║
                   ╚═══════════════════════════════╝
                ======================================
                Usage: java -jar sarus-plus-configshape.jar [options] <file-or-directory> [...]

                Example:
                  java -jar sarus-plus-configshape.jar service.config
                  java -jar sarus-plus-configshape.jar --validate-only -r -o config_schemas configs

                Options:
                  -o, --output <dir>  : Schema directory (default: ./config_schemas)
                  -r, --recursive     : Recursively scan directories
                  --preserve-tree     : Preserve input directory structure in schema directory
                  --extensions <list> : Comma-separated suffixes (default: .env,.conf,.config,.cfg,.properties)
                  --all-files         : Process every regular file
                  --generate          : Force (re)generation; overwrite existing schemas
                  --validate-only     : Validate only; fail when a schema is missing
                  -h, --help          : Show help

                Author: Alexey Sedoykin
                Contact|Support: www.linkedin.com/in/sedoykin | https://github.com/Krusty84
                =====================================
                """);
    }
}
