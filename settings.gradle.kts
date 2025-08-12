
rootProject.name="Registry"
pluginManagement {
    val quarkusPluginVersion: String by settings
    val quarkusPluginId: String by settings
    repositories {
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
    plugins {
        id(quarkusPluginId) version quarkusPluginVersion
    }
}
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        /*
        add this repository to pick up the SNAPSHOT version of the IVOA base library - in the future when this
        will not be necessary when this library is released as a non-SNAPSHOT version.
         */
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        maven {
            url = uri("https://files.basex.org/maven/")
        }
    }
}

