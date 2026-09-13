/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Alexey Sedoykin
 * SPDX-License-Identifier: MIT
 */

package com.krusty84.configschema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSchemaGeneratorTest {
    private static final Path ROOT = Path.of(System.getProperty("project.basedir"));
    private static final String SCHEMA = """
            {"title":"Сервис","documentation":null,
             "properties":{"PORT":{"description":"Порт","default":8080}}}
            """;

    @TempDir
    Path work;

    private record Result(String stdout, String stderr) {}

    private Result runCli(int expectedCode, String... args) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        Path classes = Path.of(ConfigSchemaGenerator.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        List<String> command = new ArrayList<>(List.of(java.toString(), "-Dfile.encoding=UTF-8",
                "-cp", classes.toString(), ConfigSchemaGenerator.class.getName()));
        command.addAll(List.of(args));
        Path stdout = work.resolve("stdout.txt");
        Path stderr = work.resolve("stderr.txt");
        Process process = new ProcessBuilder(command).directory(work.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "CLI process timed out");
            Result result = new Result(Files.readString(stdout), Files.readString(stderr));
            assertEquals(expectedCode, process.exitValue(), result.stdout + result.stderr);
            return result;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor();
            }
        }
    }

    private Path writeConfig(String content, String name) throws Exception {
        Path path = work.resolve(name);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, content);
    }

    private Path writeConfig(String content) throws Exception {
        return writeConfig(content, "service.conf");
    }

    private Path writeSchema(String content, String name) throws Exception {
        return writeConfig(content, "config_schemas/" + name);
    }

    private Path writeSchema(String content) throws Exception {
        return writeSchema(content, "service.conf.schema");
    }

    private Map<?, ?> readSchema(Path path) throws Exception {
        return (Map<?, ?>) new JsonParser(Files.readString(path)).parse();
    }

    private Map<String, String> snapshot(Path directory, String suffix) throws Exception {
        Map<String, String> files = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(suffix)).toList()) {
                files.put(directory.relativize(path).toString(),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return files;
    }

    @Test
    void allExistingConfigsValidateWithoutChanges() throws Exception {
        Path fixtures = Path.of(Objects.requireNonNull(getClass().getResource("/configs")).toURI());
        Path schemas = ROOT.resolve("config_schemas");
        Map<String, String> originalSchemas = snapshot(schemas, ".schema");
        Map<String, String> originalConfigs = snapshot(fixtures, ".config");
        assertEquals(22, originalConfigs.size());
        Set<String> expectedNames = new TreeSet<>();
        for (String name : originalConfigs.keySet()) {
            expectedNames.add(name + ".schema");
            Map<?, ?> properties = (Map<?, ?>) readSchema(schemas.resolve(name + ".schema")).get("properties");
            assertEquals(properties.keySet(), ConfigParser.parse(fixtures.resolve(name)).entries().keySet());
            writeConfig(Files.readString(fixtures.resolve(name)), "configs/" + name);
            writeSchema(Files.readString(schemas.resolve(name + ".schema")), name + ".schema");
        }
        assertEquals(expectedNames, originalSchemas.keySet());
        Map<String, String> before = snapshot(work, ".schema");
        Map<String, String> configsBefore = snapshot(work, ".config");
        assertTrue(runCli(0, "configs").stdout.contains("22 valid, 0 created"));
        assertEquals(before, snapshot(work, ".schema"));
        assertEquals(configsBefore, snapshot(work, ".config"));
        assertEquals(originalSchemas, snapshot(schemas, ".schema"));
        assertEquals(originalConfigs, snapshot(fixtures, ".config"));
    }

    @Test
    void schemaLifecycleRejectsNewParametersWithoutExpandingSchema() throws Exception {
        for (String extension : List.of("conf", "config")) {
            String name = "lifecycle." + extension;
            Path config = writeConfig("PORT=8080\n", name);
            Result first = runCli(0, name);
            assertTrue(first.stdout.contains("CREATED"));
            assertTrue(first.stdout.contains("0 valid, 1 created, 0 generated, 0 failed"));
            Path schema = work.resolve("config_schemas/" + name + ".schema");
            byte[] original = Files.readAllBytes(schema);
            Files.writeString(config, "PORT=9090\n");
            Result second = runCli(0, name);
            assertTrue(second.stdout.contains("VALID"));
            assertTrue(second.stdout.contains("1 valid, 0 created, 0 generated, 0 failed"));
            assertArrayEquals(original, Files.readAllBytes(schema));
            Files.writeString(config, "PORT=9090\nNEW_PARAMETER=value\n");
            for (int i = 0; i < 2; i++) {
                Result invalid = runCli(4, name);
                assertTrue(invalid.stderr.contains("NEW_PARAMETER (line 2)"));
                assertTrue(invalid.stdout.contains("0 valid, 0 created, 0 generated, 1 failed"));
                assertArrayEquals(original, Files.readAllBytes(schema));
            }
            assertFalse(Files.exists(schema.resolveSibling("json" + name + ".schema")));
        }
    }

    @Test
    void newSchemaHasOnlyAcceptedFieldsAndNoActualValues() throws Exception {
        writeConfig("# Порт \"сервиса\"\nPORT=+08080 # локальный\n"
                + "ENABLED=true\nURL=\"http://localhost/#fragment\"\nEMPTY=\nSECRET=private-test-value\n");
        assertTrue(runCli(0, "service.conf").stdout.contains("CREATED"));
        Path path = work.resolve("config_schemas/service.conf.schema");
        String raw = Files.readString(path);
        Map<?, ?> schema = readSchema(path);
        assertEquals(List.of("title", "documentation", "properties"), new ArrayList<>(schema.keySet()));
        assertNull(schema.get("documentation"));
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertEquals(List.of("PORT", "ENABLED", "URL", "EMPTY", "SECRET"), new ArrayList<>(properties.keySet()));
        for (Object value : properties.values()) {
            Map<?, ?> rule = (Map<?, ?>) value;
            assertEquals(List.of("description", "default"), new ArrayList<>(rule.keySet()));
            assertEquals("", rule.get("default"));
        }
        assertEquals("Порт \"сервиса\"\nлокальный", ((Map<?, ?>) properties.get("PORT")).get("description"));
        assertEquals("", ((Map<?, ?>) properties.get("SECRET")).get("description"));
        assertFalse(raw.contains("private-test-value"));
        assertTrue(runCli(0, "service.conf").stdout.contains("VALID"));
        assertEquals(raw, Files.readString(path));
    }

    @Test
    void defaultsDoNotConstrainValuesOrRequireMissingParameters() throws Exception {
        writeSchema(SCHEMA);
        for (String content : List.of("PORT=not-a-number\n", "PORT=\n", "")) {
            writeConfig(content);
            runCli(0, "--validate-only", "service.conf");
        }
    }

    @Test
    void unknownParameterIsRejectedWithoutChangingSchema() throws Exception {
        writeConfig("PORT=9000\nTYPO=value\n");
        Path path = writeSchema(SCHEMA);
        byte[] before = Files.readAllBytes(path);
        assertTrue(runCli(4, "service.conf").stderr.contains("TYPO (line 2): parameter is not defined"));
        assertArrayEquals(before, Files.readAllBytes(path));
    }

    @Test
    void prefixedNameIsRecognizedAndCanonicalNameHasPriority() throws Exception {
        writeConfig("PORT=8080\n");
        Path prefixed = writeSchema(SCHEMA, "jsonservice.conf.schema");
        assertTrue(runCli(0, "service.conf").stdout.contains("VALID"));
        assertFalse(Files.exists(prefixed.resolveSibling("service.conf.schema")));
        writeSchema("{\"title\":\"Сервис\",\"documentation\":null,\"properties\":{}}");
        runCli(4, "--validate-only", "service.conf");
    }

    @Test
    void forceGenerationReusesExistingPrefixedFilename() throws Exception {
        writeConfig("PORT=8080\n");
        Path path = writeSchema(SCHEMA, "jsonservice.conf.schema");
        runCli(0, "--generate", "service.conf");
        Map<?, ?> properties = (Map<?, ?>) readSchema(path).get("properties");
        assertEquals(Map.of("description", "", "default", ""), properties.get("PORT"));
        assertFalse(Files.exists(path.resolveSibling("service.conf.schema")));
    }

    @Test
    void missingSchemaFailsInValidationOnlyMode() throws Exception {
        writeConfig("PORT=8080\n");
        assertTrue(runCli(4, "--validate-only", "service.conf").stderr.contains("Schema not found:"));
        assertTrue(snapshot(work, ".schema").isEmpty());
    }

    @Test
    void malformedSchemaMetadataIsRejected() throws Exception {
        writeConfig("PORT=8080\n");
        List<String> invalid = new ArrayList<>(List.of("[]", "{\"title\":\"Only title\"}",
                SCHEMA.replace("\"title\":\"Сервис\",", ""),
                SCHEMA.replace("\"documentation\":null,", ""),
                SCHEMA.replace("\"documentation\":null", "\"documentation\":123")));
        for (String rule : List.of("null", "{}", "{\"description\":\"\"}",
                "{\"description\":123,\"default\":\"\"}")) {
            invalid.add(SCHEMA.replace("{\"description\":\"Порт\",\"default\":8080}", rule));
        }
        for (String schema : invalid) {
            writeSchema(schema);
            runCli(4, "--validate-only", "service.conf");
        }
        writeSchema("{broken");
        assertTrue(runCli(4, "--validate-only", "service.conf").stderr.contains("Invalid JSON schema"));
    }

    @Test
    void metadataScalarDefaultsAndDocumentationUrlAreAccepted() throws Exception {
        writeConfig("PORT=8080\n");
        for (String value : List.of("\"\"", "\"text\"", "123", "1.5", "true")) {
            writeSchema(SCHEMA.replace("8080", value)
                    .replace("null", "\"https://docs.sarusplus.ru/example\""));
            runCli(0, "--validate-only", "service.conf");
        }
    }

    @Test
    void recursiveGenerationPreservesTreeAndConfigExtension() throws Exception {
        writeConfig("PORT=8080\n", "inputs/a/service.conf");
        writeConfig("OTHER=value\n", "inputs/b/service.conf");
        writeConfig("NAME=value\n", "inputs/example.config");
        assertTrue(runCli(0, "-r", "--preserve-tree", "-o", "out", "inputs").stdout.contains("3 created"));
        assertEquals(Set.of(Path.of("a", "service.conf.schema").toString(),
                Path.of("b", "service.conf.schema").toString(), "example.config.schema"),
                snapshot(work.resolve("out"), ".schema").keySet());
        runCli(0, "--validate-only", "-r", "--preserve-tree", "-o", "out", "inputs");
    }

    @Test
    void duplicateConfigKeysStillFail() throws Exception {
        writeConfig("PORT=8080\nPORT=9090\n");
        assertTrue(runCli(4, "service.conf").stderr.contains("Duplicate key 'PORT' at line 2"));
    }

    @Test
    void processingContinuesAfterValidationAndParsingErrors() throws Exception {
        Path invalid = writeConfig("UNKNOWN=value\n", "inputs/1-invalid.config");
        Path broken = writeConfig("PORT=8080\nPORT=9090\n", "inputs/2-broken.config");
        Path valid = writeConfig("PORT=8080\n", "inputs/3-valid.config");
        for (Path config : List.of(invalid, broken, valid)) {
            writeSchema(SCHEMA, config.getFileName() + ".schema");
        }
        Result result = runCli(4, "inputs");
        assertTrue(result.stderr.lines().anyMatch(line -> line.startsWith("INVALID   ")
                && line.endsWith(invalid.getFileName().toString())), result.stderr);
        assertTrue(result.stderr.contains("UNKNOWN (line 1): parameter is not defined"));
        assertTrue(result.stderr.lines().anyMatch(line -> line.startsWith("FAILED    ")
                && line.contains(broken.getFileName() + ":")), result.stderr);
        assertTrue(result.stderr.contains("Duplicate key 'PORT' at line 2"));
        assertTrue(result.stdout.lines().anyMatch(line -> line.startsWith("VALID     ")
                && line.contains(valid.getFileName() + " (")), result.stdout);
        assertTrue(result.stdout.contains("Summary: 1 valid, 0 created, 0 generated, 2 failed."));
    }

    @Test
    void obsoleteSchemaOptionsAreRejected() throws Exception {
        for (String option : List.of("--strings-only", "--allow-extra", "--fail-fast")) {
            assertTrue(runCli(2, option).stderr.contains("Unknown option"));
        }
    }
}
