
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
        uksrc repo is where most javastro stuff is being published to at the moment
         */
        maven {
            url= uri("https://repo.dev.uksrc.org/repository/maven-public/")
        }
        maven {
            url = uri("https://files.basex.org/maven/")
        }
    }
}

