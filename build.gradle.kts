plugins {
    id("dev.sort.sql-dialect-plugin")
}

group = "dev.sort.trino"
version = "0.3.5"

sqlDialectPlugin {
    additionalBundledPlugins = listOf("Git4Idea")
    legalFiles = listOf("LICENSE")
    bytecodeCeilings = mapOf("trino-parser" to 69)
    embeddedLibraries = with(libs.versions) {
        setOf(
            "brikk-sql-jvmMain-${brikk.sql.core.get()}.jar",
            "brikk-sql-metadata-jvmMain-${brikk.sql.core.get()}.jar",
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
    implementation(libs.trino.parser)
    testImplementation(libs.brikk.sql.intellij.test.support)
    testImplementation(libs.trino.jdbc)
}

tasks.processResources {
    val sqlIdeVersion = libs.versions.brikk.sql.intellij.support.get()
    inputs.property("sqlIdeVersion", sqlIdeVersion)
    from(files("THIRD_PARTY_NOTICES.md")) {
        into("META-INF")
        filter { line: String -> line.replace("brikk-sql-intellij-support:0.1.1", "brikk-sql-intellij-support:$sqlIdeVersion") }
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
    if (name == "test") {
        providers.systemProperty("trino.live.url")
            .orElse(providers.gradleProperty("trino.live.url"))
            .orNull?.let { systemProperty("trino.live.url", it) }
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
    "toolsImplementation"(libs.trino.jdbc)
    "toolsImplementation"(libs.trino.parser)
}

// Usage: ./gradlew harvestFunctionCatalog -Ptrino.harvest.url='jdbc:trino://localhost:18080?sessionUser=harvest'
// Regenerates the bundled function/keyword catalog resources from a live Trino (SHOW FUNCTIONS).
// Commit the output.
val harvestFunctionCatalog by tasks.registering(JavaExec::class) {
    classpath = sourceSets["tools"].runtimeClasspath
    mainClass = "dev.sort.trino.tools.FunctionCatalogHarvestKt"
    args(
        providers.gradleProperty("trino.harvest.url").getOrElse("jdbc:trino://localhost:18080?sessionUser=harvest"),
        layout.projectDirectory.dir("src/main/resources/trino").asFile.absolutePath,
    )
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
