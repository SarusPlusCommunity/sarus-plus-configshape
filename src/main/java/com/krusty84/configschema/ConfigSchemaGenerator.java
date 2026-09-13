/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import java.nio.file.*;
import java.util.*;

public final class ConfigSchemaGenerator {
    private ConfigSchemaGenerator() {
    }

    public static void main(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help || options.inputs.isEmpty()) {
                CliOptions.printUsage();
                System.exit(options.help ? 0 : 2);
            }

            List<Path> inputFiles = ConfigFiles.collect(options);
            if (inputFiles.isEmpty()) {
                System.err.println("No configuration files found.");
                System.exit(3);
            }

            Files.createDirectories(options.outputDir);
            int created = 0, generated = 0, valid = 0, failed = 0;

            for (Path input : inputFiles) {
                try {
                    ConfigData config = ConfigParser.parse(input);
                    Path schemaPath = ConfigFiles.schemaPath(input, options);
                    Files.createDirectories(schemaPath.getParent());

                    if (options.mode == CliOptions.Mode.GENERATE) {
                        SchemaWriter.write(input, config, schemaPath);
                        System.out.printf("GENERATED %s -> %s%n", input, schemaPath);
                        generated++;
                        continue;
                    }

                    if (!Files.exists(schemaPath)) {
                        if (options.mode == CliOptions.Mode.VALIDATE_ONLY) {
                            throw new ValidationException(List.of("Schema not found: " + schemaPath));
                        }
                        SchemaWriter.write(input, config, schemaPath);
                        System.out.printf("CREATED   %s -> %s%n", input, schemaPath);
                        created++;
                        continue;
                    }

                    List<String> errors = SchemaValidator.validate(config, schemaPath);
                    if (!errors.isEmpty())
                        throw new ValidationException(errors);
                    System.out.printf("VALID     %s (%s)%n", input, schemaPath);
                    valid++;
                } catch (ValidationException e) {
                    failed++;
                    System.err.printf("INVALID   %s%n", input);
                    for (String error : e.errors)
                        System.err.println("  - " + error);
                } catch (Exception e) {
                    failed++;
                    System.err.printf("FAILED    %s: %s%n", input, e.getMessage());
                }
            }

            System.out.printf("Summary: %d valid, %d created, %d generated, %d failed.%n",
                    valid, created, generated, failed);
            if (failed > 0)
                System.exit(4);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            CliOptions.printUsage();
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static final class ValidationException extends Exception {
        final List<String> errors;

        ValidationException(List<String> errors) {
            super(String.join("; ", errors));
            this.errors = errors;
        }
    }
}
