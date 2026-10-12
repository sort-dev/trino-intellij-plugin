pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            version("brikk-sql-core", "0.20.0-SNAPSHOT")
            version("brikk-sql-intellij-support", "0.2.0-SNAPSHOT")
        }
    }
}

rootProject.name = "trino-intellij-plugin"
