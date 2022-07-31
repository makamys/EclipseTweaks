This is an Eclipse extension that makes compile-only Gradle dependencies get omitted from the classpath when running Gradle projects. This is needed because Eclipse doesn't seem to have a notion of dependency scope.

It works by using the [Equinox Framework Adaptor API](https://wiki.eclipse.org/Adaptor_Hooks) to register a class transformer. The [Gradle Tooling API](https://docs.gradle.org/current/userguide/third_party_integration.html#embedding) is used to query the dependencies of the project and their scopes, and alter the class path accordingly. It's very hacky, and probably won't work for very long.

It was created to be used with Minecraft mods and was only tested with Eclipse 2021-09.

## Usage
Add this to your `eclipse.ini` below the `-vmargs` line.
```-Dosgi.framework.extensions=reference:file:/path/to/EclipseGradleDependencyScope-<version>.jar```

Then add this VM argument to each Gradle project you want to activate EGDS for:
```-Degds.enable```

To enable logging, add this to your `eclipse.ini`. The log will be written to your system's temp folder, and named `EclipseGradleDependencyScope.log`.
```-Degds.enableLog=true```

## License
The project is licensed under the [Unlicense](UNLICENSE).
