# sarus-plus-configshape

## What is this?

**sarus-plus-configshape** is a CLI utility that creates schemas for
configuration files and checks existing configurations against their schemas.
It works with local `KEY=value` files, including configurations used by SARUS
Plus services.

Schemas use a custom, simplified JSON format containing parameter descriptions
and default values. Validation checks schema structure and allowed parameter
names. It does not check value types, formats, or ranges, require every parameter
to be present, or insert default values into configuration files.

<img width="902" height="435" alt="image" src="https://github.com/user-attachments/assets/f9b51ae1-4e65-4725-8576-77f14f5e389f" />

For implementation details and known limitations, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Features

- Validates known configuration files against existing schemas.
- Automatically creates a schema for a new configuration files, subsequent
  runs validate against that schema.
- Rejects unknown parameters without automatically extending existing schemas.
- Includes 22 schemas for known configurations in [config_schemas](config_schemas/).
- Uses configuration comments as descriptions when generating schemas, generated
  defaults are empty strings, and actual parameter values are not copied.
- Processes individual files or directories, with optional recursive traversal
  and preservation of directory structure in the schema directory.
- Continues processing remaining files after validation or parsing errors and
  reports a summary with an exit code suitable for scripts.

## Who is it for?

Developers, system administrators, DevOps engineers, and QA engineers who maintain
SARUS Plus service configurations or other configuration files using `KEY=value`
syntax.

## Instructions

### Prerequisites

- Java 17 or later to run the application, a JDK 17 or later to build from source.
- Verify Java with `java -version`. For source builds, set `JAVA_HOME` to your JDK
  installation directory.
- Network access for the first source build to download Maven and build
  dependencies. Maven Wrapper pins Maven 3.9.15, so a separate Maven installation
  is not required.

The application has no external runtime dependencies. It does not require a
running Spring Boot service or access to the documentation links stored in schemas.

### Get Your Input Data

Prepare a UTF-8 configuration file with one `KEY=value` parameter per line:

```properties
# Application port
PORT=8080
HOST=localhost # Service host
```

Default file extensions are `.env`, `.conf`, `.config`, `.cfg`, and `.properties`.
Use `--extensions` to replace this list or `--all-files` to disable extension
filtering. The input must still use the supported `KEY=value` syntax, YAML and
JSON configuration files are not supported.

Standalone comments beginning with `#`, `;`, or `!` apply to the next parameter.
A blank line breaks that association. Within a value, `#` or `;` starts a comment
outside quotes when it appears at the beginning or after whitespace. For example,
`HOST=localhost # Service host` has value `localhost`, while `VALUE="text # text"`
keeps the `#` inside the value. Duplicate parameter names are rejected.

For an unknown file named `service.config`, the example above produces
`service.config.schema`:

```json
{
  "title": "service configuration",
  "documentation": null,
  "properties": {
    "PORT": {
      "description": "Application port",
      "default": ""
    },
    "HOST": {
      "description": "Service host",
      "default": ""
    }
  }
}
```

You can fill in descriptions, defaults, and the documentation link manually.
During validation, these fields remain reference metadata. Comments in the input
do not update an existing schema.

### Run sarus-plus-configshape

Use the ready-to-run `sarus-plus-configshape_#_#_#.jar` with Java 17 or later.
You do not need Maven or the project source code to run it.

Place the JAR in a convenient directory. To validate known configurations, copy
[config_schemas](config_schemas/) there as well, schemas are not bundled in the
JAR. Keep the original configuration filenames so they match their schemas.
For example, your working directory can contain:

Go to Release section to get `sarus-plus-configshape_#_#_#.jar` and `config_schemas.zip`

```text
sarus-plus-configshape.jar
config_schemas/
configs/
```

Open a terminal in that directory. All examples below assume the JAR is in the
current directory. Replace sample configuration paths with your own paths.

#### Show Help

```bash
java -jar sarus-plus-configshape.jar --help
```

Running without arguments also shows help, but exits with code `2`.
There is no interactive terminal interface.

#### Process a Single Configuration

```bash
java -jar sarus-plus-configshape.jar configs/service.config
```

The application looks for `config_schemas/service.config.schema`:

- If the schema exists, the file is validated against it.
- If the schema is missing, it is created and the application reports `CREATED`.
- Running the same command again validates against the created schema and reports
  `VALID` if the file passes.
- Adding a parameter that is absent from the schema produces an error. The schema
  is not expanded automatically, update it explicitly if the parameter is intended.

A newly generated schema contains descriptions from comments and empty defaults.
A `CREATED` result means a schema was generated from the current parameter names,
those names have not been checked against an independently maintained schema. 

#### Validate Using Existing Schemas Only

Use this mode when every input file must already have a schema:

```bash
java -jar sarus-plus-configshape.jar --validate-only configs/service.config
```

If the schema is missing, the file fails with `Schema not found` and no schema
file is created. A parameter not defined in the schema also causes validation
to fail. Omitted parameters and different values for known parameters are allowed.

#### Process Multiple Files or a Directory

Pass several files in one command:

```bash
java -jar sarus-plus-configshape.jar configs/service-a.config configs/service-b.config
```

Or process the supported files directly inside a directory:

```bash
java -jar sarus-plus-configshape.jar configs
```

Add `-r` to include subdirectories. Combine it with `--validate-only` to require
existing schemas for every configuration:

```bash
java -jar sarus-plus-configshape.jar --validate-only -r configs
```

Validation and parsing errors do not stop processing of the remaining files.
The final summary includes both successful and failed files.

#### Choose a Schema Directory

`-o` specifies where schemas are read or created, it does not specify a directory
for modified configuration files. The input files are not rewritten.

```bash
java -jar sarus-plus-configshape.jar -o schemas configs/service.config
```

Here the schema path is `schemas/service.config.schema`. Relative paths are
resolved from the terminal's current working directory, not the JAR's location.
You can also use absolute paths. Quote paths containing spaces, for example
in Windows PowerShell:

```bash
java -jar sarus-plus-configshape.jar --validate-only -o "C:\Sarus Plus\schemas" "C:\Sarus Plus\configs\service.config"
```

#### Preserve Subdirectories in the Schema Directory

```bash
java -jar sarus-plus-configshape.jar -r --preserve-tree -o schemas configs
```

For the input directory `configs`, this maps `configs/team-a/service.config`
to `schemas/team-a/service.config.schema` and `configs/team-b/service.config`
to `schemas/team-b/service.config.schema`. Use the same directory layout and
options on later validation runs:

```bash
java -jar sarus-plus-configshape.jar --validate-only -r --preserve-tree -o schemas configs
```

#### Select File Extensions

To process only `.config` and `.properties` files:

```bash
java -jar sarus-plus-configshape.jar --extensions config,properties configs
```

To process all regular files regardless of extension:

```bash
java -jar sarus-plus-configshape.jar --all-files configs
```

Extension filtering applies to explicit file paths as well as directory scans.
`--all-files` changes file selection only: each selected file must still contain
supported `KEY=value` data.

#### Explicitly Regenerate a Schema

```bash
java -jar sarus-plus-configshape.jar --generate configs/service.config
```

This reports `GENERATED` and replaces the schema using the current configuration's
parameter names and comments. Existing descriptions, defaults, and the documentation
link are replaced, every generated default is empty. Use this command only when
you intend to recreate the schema. To preserve manually maintained metadata,
edit the existing schema instead.

#### Options Reference

| Option                | Description                                                                             |
| --------------------- | --------------------------------------------------------------------------------------- |
| `-o, --output <dir>`  | Schema directory, defaults to `./config_schemas`                                        |
| `-r, --recursive`     | Search input directories recursively                                                    |
| `--preserve-tree`     | Preserve each configuration's relative parent path under the schema directory           |
| `--extensions <list>` | Replace the default extensions with a comma-separated list, such as `config,properties` |
| `--all-files`         | Process all regular files regardless of extension                                       |
| `--generate`          | Force schema generation, overwriting an existing schema                                 |
| `--validate-only`     | Validate only, fail for a file whose schema is missing                                  |
| `-h, --help`          | Show help and exit successfully                                                         |

`--generate` and `--validate-only` are mutually exclusive. `--generate` also
replaces manually entered descriptions, defaults, and documentation links.
The removed `--fail-fast` option is rejected as an unknown argument.

Schemas are read from and written to `config_schemas` relative to the current
working directory, unless `-o` is provided. The primary name is
`<configuration filename>.schema`, for example `service.config.schema`.
Existing names prefixed with `json`, such as `jsonservice.config.schema`, are
also recognized, the name without the prefix takes precedence when both exist.
Schemas are external files and are not bundled in the JAR, so copy them separately
when distributing the application.

Without `--preserve-tree`, configurations with the same filename share a schema.
The option preserves paths relative to input directories, matching relative paths
under different input roots can still collide.

#### Read the Results

Successful file results are reported as `VALID`, `CREATED`, or `GENERATED`.
Validation errors are reported as `INVALID`, and parsing or file operation errors
as `FAILED`. The final `Summary` counts valid, created, generated, and failed files.
Results and the summary go to stdout, diagnostics go to stderr.
For example, one valid file and two failed files produce:

```text
Summary: 1 valid, 0 created, 0 generated, 2 failed.
```

This run exits with code `4`. A successful run exits with code `0`.

| Exit code | Meaning                                               |
| --------- | ----------------------------------------------------- |
| `0`       | All processed files succeeded, or help was requested  |
| `1`       | Unexpected error outside the per-file processing loop |
| `2`       | Invalid arguments or no input paths                   |
| `3`       | No matching configuration files found                 |
| `4`       | One or more files failed validation or processing     |

### Build sarus-plus-configshape from Source

1. Clone or download this repository.
2. Open a terminal in the project directory.
3. Build, run the tests, and package the executable JAR:

```bash
./mvnw clean verify
```

In Windows PowerShell, use `./mvnw.cmd clean verify`. If Maven is already
installed, you can use `mvn clean verify` instead.

The compiled JAR is `target/sarus-plus-configshape.jar`. Compilation targets
Java 17. Build output is excluded from Git.

To run only the tests:

```bash
./mvnw test
```

The 14 JUnit tests launch the CLI in separate Java processes and check output,
exit codes, schema generation, and unchanged schemas during validation.
The test suite includes 22 configurations with synthetic values in
[src/test/resources/configs](src/test/resources/configs/), it does not depend
on the local `_examples_` directory. JUnit is a test-only dependency.

After building, copy the compiled JAR to the repository root to check all
supplied test configurations against the existing schemas:

```bash
java -jar sarus-plus-configshape.jar --validate-only src/test/resources/configs
```
