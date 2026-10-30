# Installation

## Docker

```shell
./gradlew quarkusBuild
docker-compose up
```

builds the image `javastro/publishing-registry` and starts it.

## Kubernetes / Helm

```shell
./gradlew build
```

generates a Helm chart in `build/helm`. In production the application reads a ConfigMap named `registry-configmap`
(`%prod.quarkus.kubernetes-config`). The harvesting cron expression is exposed as a Helm value.

## Running the jar

After `./gradlew quarkusBuild` the application is in `build/quarkus-app/`:

```shell
java -jar build/quarkus-app/quarkus-run.jar
```

The BaseX database location defaults to the BaseX home directory (shown in the start-up log as
`BaseX home directory=...`). Set the Java system property `org.basex.path` to place it explicitly, and make sure it is on
persistent storage. Only one process can open the database at a time.

To include the optional relational mode see {doc}`regtap-admin`.
