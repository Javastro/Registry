# Building the RegTAP variant

RegTAP is optional at both build and run time, controlled by the `regtap` flag and Quarkus profile.

## What the flag does

`build.gradle.kts` treats RegTAP as enabled if any of these is set: `-Pregtap`, `-Dquarkus.profile=...regtap...`, or the
environment variable `QUARKUS_PROFILE` containing `regtap`.

* **Enabled** - the Hibernate ORM, PostgreSQL JDBC, `quarkus-tap-lib`, TAP, RegTAPDM and DAL dependencies are added and
  the `registry/tap` sources are compiled.
* **Disabled** - those dependencies are omitted and `registry/tap/**` is excluded from main and test sources.

At run time `application-regtap.properties` is loaded only for the `regtap` profile, and all classes in `registry.tap`
carry `@IfBuildProfile("regtap")`. The Gradle flag and the Quarkus profile must agree.

## Commands

```shell
./gradlew quarkusDev -Pregtap -Dquarkus.profile=dev,regtap
./gradlew quarkusBuild -Pregtap -Dquarkus.profile=prod,regtap
./gradlew test -Pregtap -Dquarkus.profile=regtap --tests '*RegTapLoadTest'
```

The RegTAP tests need Docker (a PostgreSQL dev service is started).

## Keeping core code independent

Code outside `registry.tap` must not reference tap classes. The harvester talks to the relational store through
`RelationalRecordSink`; `RegTapLoader` implements it, and the harvester injects it as `Instance<RelationalRecordSink>`,
which is unresolvable when the profile is off.
