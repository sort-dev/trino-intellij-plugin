plugins {
    id("dev.sort.sql-dialect-plugin")
}

group = "dev.sort.trino"
version = "0.4.0"

sqlDialectPlugin {
    additionalBundledPlugins = listOf("Git4Idea")
    legalFiles = listOf("LICENSE")
    bytecodeCeilings = mapOf("trino-parser" to 69)
    embeddedLibraries = with(libs.versions) {
        setOf(
            "brikk-sql-jvmMain-${brikk.sql.core.get()}.jar",
            "brikk-sql-metadata-jvmMain-${brikk.sql.core.get()}.jar",
            "brikk-sql-verify-api-${brikk.sql.verify.get()}.jar",
            "brikk-sql-verify-trino-${brikk.sql.verify.get()}.jar",
            "brikk-sql-intellij-support-${brikk.sql.intellij.support.get()}.jar",
            "brikk-engine-kotlin-intellij-support-${brikk.engine.kotlin.intellij.support.get()}.jar",
            "trino-parser-${trino.get()}.jar", "trino-grammar-${trino.get()}.jar",
            "antlr4-runtime-${antlr.get()}.jar",
            "error_prone_annotations-2.41.0.jar", "failureaccess-1.0.3.jar",
            "guava-33.5.0-jre.jar", "j2objc-annotations-3.1.jar", "jakarta.annotation-api-3.0.0.jar",
            "jspecify-1.0.0.jar", "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
        )
    }
    requiredNotices = listOf("brikk-sql-intellij-support", "ANTLR", "BSD-3-Clause", "Guava", "Apache License 2.0")
}

dependencies {
    implementation(libs.brikk.sql.jvm)
    implementation(libs.brikk.sql.intellij.support)
    implementation(libs.brikk.engine.kotlin.intellij.support)
    implementation("dev.brikk.house:brikk-sql-verify-trino:${libs.versions.brikk.sql.verify.get()}")
    testImplementation(libs.brikk.sql.intellij.test.support)
    testImplementation(libs.trino.jdbc)
    testImplementation(libs.trino.parser)
}

tasks.processResources {
    from(files("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("corpus.dir", layout.projectDirectory.dir("src/test/resources/corpus").asFile.absolutePath)
    val compiler = providers.gradleProperty("test.brikkCompilerPlugin").orNull
    if (name in setOf("test", "test262") && compiler != null) {
        require(file(compiler).isFile) { "Missing compiler fixture JAR: $compiler" }
        systemProperty("test.brikkCompilerPlugin", file(compiler).absolutePath)
    } else {
        exclude("**/BrikkCompilerRelationTest.class")
    }
    providers.systemProperty("trino.live.url")
        .orElse(providers.gradleProperty("trino.live.url"))
        .orElse(providers.environmentVariable("TRINO_LIVE_URL"))
        .orNull?.let { systemProperty("trino.live.url", it) }
    if (providers.environmentVariable("TRINO_LIVE").orNull == "1") {
        include("**/TrinoAutoIntrospectLiveTest.class", "**/TrinoLiveCatalogLiveTest.class",
            "**/TrinoJdbcMetadataTruthTest.class", "**/TrinoPgCatalogLandmineTest.class",
            "**/TrinoConsoleTruthTest.class", "**/TrinoAuthMatrixTest.class")
    }
    val scanDirectory = providers.gradleProperty("realSql.dir").orNull
    val scanGlob = providers.gradleProperty("realSql.glob").orNull
    if (scanDirectory == null) {
        require(scanGlob == null) { "realSql.glob requires realSql.dir" }
        require(providers.gradleProperty("realSql.extraFile").orNull == null) { "realSql.extraFile requires realSql.dir" }
        exclude("**/ExternalSqlScanTest.class")
    } else {
        require(!scanGlob.isNullOrBlank()) { "External scans require an explicit realSql.glob" }
        require(file(scanDirectory).isDirectory) { "External scan directory is unavailable" }
        require(providers.gradleProperty("test.pluginIsolation").orNull != "true") { "External scans and isolation are separate lanes" }
        include("**/ExternalSqlScanTest.class")
        systemProperty("realSql.dir", file(scanDirectory).absolutePath)
        systemProperty("realSql.glob", scanGlob)
        providers.gradleProperty("realSql.extraFile").orNull?.let { extra ->
            require(file(extra).isFile && file(extra).extension.equals("sql", true)) { "Explicit extra SQL file is unavailable" }
            systemProperty("realSql.extraFile", file(extra).absolutePath)
        }
        val label = providers.gradleProperty("realSql.label").orNull ?: "scan"
        require(label.matches(Regex("[A-Za-z0-9_-]+"))) { "realSql.label must be a neutral scratch-directory label" }
        val scratch = layout.buildDirectory.dir("realsql/$label/$name").get().asFile
        systemProperty("realSql.output", scratch.absolutePath)
        // Private SQL must not reach ordinary test reports, IDE caches or reusable test outputs.
        reports.junitXml.outputLocation.set(scratch.resolve("test-results"))
        reports.html.outputLocation.set(scratch.resolve("test-report"))
        binaryResultsDirectory.set(scratch.resolve("test-results/binary"))
        jvmArgumentProviders.add(org.gradle.process.CommandLineArgumentProvider {
            listOf("-Didea.log.path=${scratch.resolve("ide-log")}",
                "-Didea.system.path=${scratch.resolve("ide-system")}",
                "-Didea.config.path=${scratch.resolve("ide-config")}")
        })
        outputs.upToDateWhen { false }
        outputs.cacheIf { false }
    }
}

kotlin {
    compilerOptions {
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

// --- harvest tooling (manual tasks; output is committed corpus/resources) ---

sourceSets {
    create("tools")
}

dependencies {
    "toolsImplementation"(libs.trino.parser)
}

// Usage: ./gradlew harvestCensus [-PtrinoSrc=/path/to/trino-checkout-at-483]
// Reads product-test .sql + docs ```sql fences, grades every candidate with the BUNDLED
// trino-parser (exact authority), buckets into AST-derived syntax families, samples up to 4 per
// family, writes src/test/resources/corpus/census/. Commit the output.
val harvestCensus by tasks.registering(JavaExec::class) {
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass = "dev.sort.trino.tools.TrinoCensusHarvestKt"
    val src = providers.gradleProperty("trinoSrc")
        .getOrElse(layout.projectDirectory.dir("../references/trino-upstream").asFile.absolutePath)
    args(
        src,
        layout.projectDirectory.dir("src/test/resources/corpus/census").asFile.absolutePath,
    )
}
