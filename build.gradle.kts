plugins {
    `java-library`
    id("io.quarkus")
}

group = "org.javastro.ivoa.registry"
version = "0.1-SNAPSHOT"


val quarkusPlatformGroupId: String by project
val quarkusPlatformArtifactId: String by project
val quarkusPlatformVersion: String by project

dependencies {
    implementation("org.javastro.ivoa:ivoa-entities:0.9.12")
    implementation("org.basex:basex-api:12.0")
    implementation("org.xmlresolver:xmlresolver:6.0.18")
    implementation("net.sf.saxon:Saxon-HE:12.5") // for xslt 3.0
    implementation(enforcedPlatform("${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}"))
    implementation("io.quarkus:quarkus-rest-client-jackson")
    implementation("io.quarkus:quarkus-rest-client-jaxb")
    implementation("io.quarkus:quarkus-elytron-security-properties-file")
    implementation("io.quarkus:quarkus-rest-qute")
    implementation("io.quarkus:quarkus-rest-jaxb")
    implementation("io.quarkus:quarkus-rest-jackson")


    implementation("io.quarkus:quarkus-smallrye-openapi")
    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-smallrye-graphql")

    implementation("io.quarkus:quarkus-kubernetes")
    implementation("io.quarkus:quarkus-container-image-docker")
   // implementation("io.quarkus:quarkus-resteasy-reactive")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
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
