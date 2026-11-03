pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            // Plugin release pins; keep the shared conventions tree unchanged.
            version("brikk-sql-core", "0.23.0")
            version("brikk-sql-verify", "0.23.0")
            version("brikk-sql-intellij-support", "0.4.0")
            version("trino", "484")
        }
    }
}

rootProject.name = "trino-intellij-plugin"
