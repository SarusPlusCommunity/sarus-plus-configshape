# sarus-plus-configshape Architecture

## Overview

The utility is a Java command-line process that runs once to process local files
containing `KEY=value` parameters. It validates configurations against existing
schemas or creates schemas for unknown files. Files are processed sequentially.

The application supports configuration files for SARUS Plus services, among
others, but does not start Spring Boot or load its environment. Schemas use a
custom, simplified JSON format. Validation covers the schema structure and
allowed parameter names, configuration values are not validated.

User commands are documented in [README.md](README.md).

## Repository Structure

| Path                                       | Purpose                                                     |
| ------------------------------------------ | ----------------------------------------------------------- |
| `src/main/java/com/krusty84/configschema/` | Eight application classes and records in a single package   |
| `src/test/java/com/krusty84/configschema/` | JUnit tests for command-line behavior                       |
| `src/test/resources/configs/`              | 22 test configurations with synthetic values                |
| `config_schemas/`                          | 22 schemas for known configurations, stored outside the JAR |
| `pom.xml`                                  | Compilation, testing, and executable JAR packaging          |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/`        | Maven Wrapper and the pinned Maven version                  |
| `target/`                                  | Build output, excluded from Git                             |

The local `_examples_` and `off_doc_extract` directories are excluded from Git.
Neither is required for building or testing.

## Main Runtime Components

All supporting types have package visibility. The public entry point is
`ConfigSchemaGenerator.main`, there is no separate library API.

| Component                                                                                   | Responsibility                                                                                    |
| ------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| [ConfigSchemaGenerator](src/main/java/com/krusty84/configschema/ConfigSchemaGenerator.java) | Coordinates processing, selects actions, creates directories, and handles messages and exit codes |
| [CliOptions](src/main/java/com/krusty84/configschema/CliOptions.java)                       | Parses arguments, sets defaults and the operating mode, and formats help output                   |
| [ConfigFiles](src/main/java/com/krusty84/configschema/ConfigFiles.java)                     | Discovers, filters, and sorts configuration files, and selects schema paths                       |
| [ConfigParser](src/main/java/com/krusty84/configschema/ConfigParser.java)                   | Reads configurations as UTF-8, parses lines and comments, and rejects duplicate keys              |
| [ConfigData](src/main/java/com/krusty84/configschema/ConfigData.java)                       | Carries ordered parameters with their values, descriptions, and line numbers                      |
| [SchemaWriter](src/main/java/com/krusty84/configschema/SchemaWriter.java)                   | Generates JSON and writes new or explicitly regenerated schemas                                   |
| [SchemaValidator](src/main/java/com/krusty84/configschema/SchemaValidator.java)             | Reads schemas, validates their metadata, and detects unknown configuration parameters             |
| [JsonParser](src/main/java/com/krusty84/configschema/JsonParser.java)                       | Parses JSON into Java objects for the validator                                                   |

Components are separated by responsibility, without interfaces or a dependency
injection container. The parser and schema components perform their own file
I/O. `ConfigData` uses a `LinkedHashMap` to preserve parameter order, although
it is a `record`, the map it contains is mutable.

## Data Flow

1. `CliOptions.parse` receives the arguments. A help request exits with code `0`,
   missing input paths produce help output and exit code `2`.
2. `ConfigFiles.collect` gathers files, normalizes absolute paths, removes
   duplicate paths, and sorts the result. Directory traversal is limited to
   one level by default and becomes recursive with `-r`.
3. For each file, `ConfigParser.parse` creates a `ConfigData` instance. An invalid
   line or duplicate key stops parsing that file before a schema is selected
   or written.
4. `ConfigFiles.schemaPath` determines the schema path. The entry point then
   selects an action based on the mode and whether the schema file exists.
5. Counters accumulate the results. Processing ends with a `Summary`, if any
   file failed, the process exits with code `4`.

| Mode                       | Schema exists                        | Schema is missing          |
| -------------------------- | ------------------------------------ | -------------------------- |
| Default, `CHECK_OR_CREATE` | Validate, report `VALID` or an error | Create, report `CREATED`   |
| `--validate-only`          | Validate                             | Report `Schema not found`  |
| `--generate`               | Overwrite, report `GENERATED`        | Create, report `GENERATED` |

`--generate` and `--validate-only` are mutually exclusive. Validation errors
are reported as `INVALID`, parsing and file operation errors within the loop
are reported as `FAILED`. Both increment `failed`, and processing continues
with the remaining files. The removed `--fail-fast` option is rejected as unknown.

Successful results, help, and the summary go to stdout, diagnostics go to stderr.
Errors outside the processing loop can terminate the entire run: argument errors
return code `2`, no matching configuration files returns `3`, and other exceptions
caught at the outer level return `1`.

## Key Design Decisions

### Schemas Are Associated with File Names

The default schema directory is `config_schemas`, relative to the working
directory rather than the JAR location. `-o` selects a different directory.

The primary name is `<full configuration filename>.schema`, for example
`service.config.schema`. If that file is missing, an existing
`jsonservice.config.schema` is used. If neither exists, the new schema receives
the name without the prefix. The same selection applies to `--generate`, so an
existing prefixed schema is overwritten without creating a second copy.

`--preserve-tree` appends the relative path of the configuration's parent
directory to the schema directory. This path is calculated using the first
matching input directory in argument order.

### Schemas Store Allowed Names and Reference Metadata

The root object requires a string `title`, a string or `null` for `documentation`,
and a `properties` object. Each property must be an object containing a string
`description` and a `default` field. The validator checks that `default` exists
but does not constrain its type. Additional schema fields are ignored.

Parameter names are matched exactly and are case-sensitive. A configuration
parameter absent from `properties` is an error. Parameters listed in the schema
may be omitted from the configuration. Type, format, range, and required-field
constraints are not applied, and default values are not substituted.

The generator writes only keys from the parsed configuration. It constructs
`title` from the filename with its last extension removed, followed by
` configuration`. It sets `documentation` to `null` and every `default` to an
empty string. Actual parameter values are not copied into the schema.
Descriptions and defaults are not automatically fetched from external
documentation.

### Comments Become Descriptions

Standalone lines beginning with `#`, `;`, or `!` after trimming whitespace are
collected for the next parameter. A blank line clears the collected comments.
Within a value, `#` and `;` start a comment outside quotes when they appear at
the beginning of the value or after whitespace. Preceding and inline comments
are joined with newline characters and become `description` during generation.

The parser accepts the `export ` prefix and removes matching outer single or
double quotes from values. During normal validation, comments do not update
descriptions in an existing schema.

## External Dependencies and Integrations

At runtime, the application uses only the Java standard library. It has no
Spring Boot runtime, network requests, database, or external validation service.
`documentation` is a reference link, the application does not open it.
Schemas are read from disk when processing the corresponding configuration and
are not bundled in the JAR. Changes to an external schema are available to the
next run without rebuilding.

The only declared dependency is JUnit Jupiter 5.13.4 with `test` scope.
Maven and build dependencies are downloaded from Maven Central when needed.

## Build and Validation Notes

[pom.xml](pom.xml) specifies Java release 17, UTF-8, and pinned versions of the
compilation, testing, and packaging plugins. The Wrapper uses Maven 3.9.15.
`./mvnw clean verify` produces `target/sarus-plus-configshape.jar` with
`com.krusty84.configschema.ConfigSchemaGenerator` as the manifest entry point.

[ConfigSchemaGeneratorTest](src/test/java/com/krusty84/configschema/ConfigSchemaGeneratorTest.java)
contains 14 JUnit tests. They launch the compiled entry point in a separate Java
process using the classpath and check stdout, stderr, and exit codes.
Working configurations and schemas are placed in temporary directories.

Coverage includes the schema lifecycle, comments, metadata and parameter errors,
filename compatibility, recursive processing, and continuation after errors.
Test configurations with synthetic values exercise the 22 known schemas,
checking matching keys and unchanged files. Running the packaged JAR itself is
not part of the JUnit suite. After changing packaging, verify it separately with
`java -jar target/sarus-plus-configshape.jar --help`.

## Known Constraints

- The parser implements a custom, line-based `KEY=value` format rather than
  the full semantics of Java Properties, shell, or Spring Boot. It does not
  support YAML, values continued across lines, variable substitution, or
  decoding escape sequences in values. Unclosed quotes are not diagnosed
  separately.
- The internal JSON parser is not an implementation of the JSON Schema standard.
  Duplicate JSON keys are replaced by the last value without a separate error.
- Without `--preserve-tree`, configurations with the same filename in different
  directories share a schema. Even with this option, identical relative paths
  under different input roots can collide, there is no separate collision check.
- Schemas are written directly through `Files.writeString`, without atomic
  replacement, backups, or locking between processes. `--generate` also replaces
  manually entered descriptions, defaults, and the documentation link.
- Even `--validate-only` creates the necessary schema directories, although it
  does not write schema files.
- Missing input paths are skipped with a message. If other files are processed
  successfully, a missing path alone does not cause the run to fail.
- Configuration and schema files are read entirely into memory. There is no
  streaming of file contents or input size limit.
