# Registry 

This project is an implementation of an [IVOA publishing registry](https://www.ivoa.net/documents/RegistryInterface/20180723/REC-RegistryInterface-1.1.html). It uses [BaseX](https://docs.basex.org/) a native XML database as the document store.

It supports the [OAI-PMH](http://www.openarchives.org/OAI/openarchivesprotocol.html) interface for harvesting.

The development framework that is used to create the application is Quarkus. If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./gradlew quarkusDev
```

> **_NOTE:_** 
> * Quarkus ships with a Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.
> * there is also a Swagger UI to run all the endpoints http://localhost:8080/q/dev-ui/io.quarkus.quarkus-smallrye-openapi/swagger-ui


### Adding content

In a deployment it is possible to add entries to the registry using the admin endpoints - these are basicauth protected with 

* user: admin
* password: passwordchangeme

this can be changed in a deployment by setting the following environment variable
```shell
QUARKUS_SECURITY_USERS_EMBEDDED_USERS__admin__ = betterpassword
```

## Running in Docker

An image called `javastro/publishing-registry` will be created and run with
```shell
./gradlew quarkusBuild 
docker-compose up
```
