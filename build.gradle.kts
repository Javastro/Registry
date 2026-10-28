plugins {
    id("org.javastro.build.module.quarkus-app")
}

group = "org.javastro.ivoa.registry"
version = "0.2-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.dev.uksrc.org/repository/maven-public/")
    maven("https://files.basex.org/maven/")

}

dependencies {
 //   implementation("io.quarkiverse.helm:quarkus-helm:1.2.3")
    implementation("org.javastro.ivoa:ivoa-entities:0.9.15")
    implementation("org.basex:basex-api:12.4")
    implementation("org.xmlresolver:xmlresolver:6.0.18")
    implementation("net.sf.saxon:Saxon-HE:12.5") // for xslt 3.0
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-rest-client-jaxb")
    implementation("io.quarkus:quarkus-elytron-security-properties-file")
    implementation("io.quarkus:quarkus-rest-qute")
    implementation("io.quarkiverse.qute.web:quarkus-qute-web:3.4.5")
    implementation("io.quarkus:quarkus-rest-jaxb")
    implementation("io.quarkus:quarkus-rest-jackson")

    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-graphql")
//TAP
    implementation("io.quarkus:quarkus-hibernate-orm")
    implementation("io.quarkus:quarkus-jdbc-postgresql")
    implementation("org.javastro.ivoa.core.quarkus:quarkus-tap-lib:0.9.0")
    implementation("org.javastro.ivoa.core:tap:0.9.2") // TODO should make this transitively available via quarkus-tap-lib
    implementation("org.javastro.ivoa.dm:RegTAPDM:0.9")
    implementation("org.javastro.ivoa.core:dal:0.9.1")

//END TAP

 //   implementation("io.quarkus:quarkus-kubernetes")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-container-image-docker")
    // implementation("io.quarkus:quarkus-resteasy-reactive")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("io.rest-assured:rest-assured")

    implementation("io.quarkus:quarkus-kubernetes-config")
}



java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.test {
    useJUnitPlatform()
//    jvmArgs("--illegal-access=warn")
}
tasks.withType<Test> {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
