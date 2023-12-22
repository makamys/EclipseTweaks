# EclipseGradleDependencyScope

This is an Eclipse extension that makes compile-only Gradle dependencies get omitted from the classpath when running Gradle projects.

It works by using the [Equinox Framework Adaptor API](https://wiki.eclipse.org/Adaptor_Hooks) to register a class transformer.
- A hook is inserted after runtime classpath resolution to remove all classpath entries that have an empty `gradle_used_by_scope` property.
- The Buildship plugin's runtime classpath resolver is also modified to force it to use the behavior used for Gradle <5.6, which is respecting dependency scopes.

## Usage

Add this to your `eclipse.ini` below the `-vmargs` line:

```
-Dosgi.framework.extensions=reference:file:/path/to/EclipseGradleDependencyScope-<version>.jar
```

EGDS will automatically apply to every run configuration. You can force it to be disabled for one by adding this to its VM flags:

```
-Degds.disable
```

To enable logging, add this to your `eclipse.ini`. The log will be written to your system's temp folder, and named `EclipseGradleDependencyScope.log`.

```
-Degds.enableLog=true
```

### Config

The tool can be further configured on the project level by placing a Java property file called `egds.properties` in the Gradle project's root directory.

The following options are supported:

* `dependencyBlacklist`: Comma separated list of dependency file name glob patterns to be excluded from the runtime classpath. If set, this list will be used instead of checking the classpath properties.

## License
The project is licensed under the [Unlicense](UNLICENSE).
