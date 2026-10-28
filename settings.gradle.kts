
rootProject.name="Registry"
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.dev.uksrc.org/repository/maven-public/")
        mavenLocal()
    }

}
dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
//    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://files.basex.org/maven/")
    }
}

plugins {
    id("org.javastro.build") version "0.2.6"
}


