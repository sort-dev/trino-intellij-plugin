pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            version("brikk-sql-intellij-support", "0.1.2-SNAPSHOT")
        }
    }
}

rootProject.name = "trino-intellij-plugin"
