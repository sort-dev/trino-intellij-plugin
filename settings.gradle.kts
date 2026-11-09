pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            // Plugin release pins; keep the shared conventions tree unchanged.
            version("brikk-sql-core", "1.0.1")
            version("brikk-sql-verify", "1.0.1")
            version("brikk-sql-intellij-support", "1.0.1")
            version("trino", "484")
        }
    }
}

rootProject.name = "trino-intellij-plugin"
