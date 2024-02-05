# EclipseTweaks

An Eclipse extension that fixes some Eclipse oddities.

It works by using the [Equinox Framework Adaptor API](https://wiki.eclipse.org/Adaptor_Hooks) to register a class transformer.

## Usage

Add this to your `eclipse.ini` below the `-vmargs` line:

```
-Dosgi.framework.extensions=reference:file:/path/to/EclipseTweaks-<version>.jar
```

To enable logging, add this to your `eclipse.ini`. The log will be written to your system's temp folder, and named `EclipseTweaks.log`.

```
-DeclipseTweaks.log=true
```

## Modules

All modules are enabled by default. A module can be disabled by adding the `-DeclipseTweaks.<module>.enabled=false` JVM flag in your `eclipse.ini`.

### `gradleScope`

Makes compile-only Gradle dependencies get omitted from the classpath when running Gradle projects.

- Hooks are inserted during runtime classpath resolution to remove all classpath entries that lack the required scope in their `gradle_used_by_scope` property.
- The Buildship plugin's runtime classpath resolver is also modified to force it to use the behavior used for Gradle <5.6, which is respecting dependency scopes. The scope it looks for can also be overridden.

The module will automatically apply to every run configuration. By default it will exclude dependencies not in the `main` scope for native projects, and dependencies in a different scope than the main class for Gradle projects.

The following VM flags can be added to run configurations to customize them:

- `-DeclipseTweaks.gradleScope=myScope`: Changes the scope to `myScope`. Multiple values can be specified by separating them with commas.
- `-DeclipseTweaks.gradleScope.enabled=false`: Disable the module for one project.

#### Config

The module can be further configured on the project level by placing a Java property file called `eclipseTweaks.properties` in the Gradle project's root directory.

The following options are supported:

* `gradleScope.dependencyBlacklist`: Comma separated list of dependency file name glob patterns to be excluded from the runtime classpath. If set, this list will be used instead of checking the classpath properties.

### `fastStep`

For some reason in Eclipse 2022-06, a 100 ms delay [was added](https://github.com/eclipse-platform/eclipse.platform/commit/bca984dc24bc623ddd4f152c5e5b2ba588dbb792) before the debug UI's highlighted line is updated after stepping. This causes the UI to feel very unresponsive when stepping through lines. This tweak reverts this to 0 ms. So far no issues have been encountered from this.

## License
The project is licensed under the [Unlicense](UNLICENSE).
