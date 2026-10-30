# Building and testing

Requires JDK 17.

```shell
./gradlew quarkusDev          # dev mode with live reload
./gradlew test                # default suite (integration-tagged tests excluded)
./gradlew test -Dquarkus.test.exclude-tags=   # include tests that call external registries
./gradlew quarkusBuild        # packaged application
./gradlew javadoc             # API documentation in build/docs/javadoc
./gradlew docsSite -Pregtap   # full documentation site (Sphinx + Javadoc) in build/site; needs `pip install -r docs/requirements.txt`
```

Tests use an embedded BaseX. If another process (such as the BaseX GUI) has the default database open, run the tests with
`JAVA_TOOL_OPTIONS=-Dorg.basex.path=/some/empty/dir`.
