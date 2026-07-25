plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.sort.trino"
version = "0.2.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    // BUNDLED authority: Trino's OWN parser, published by the Trino project as a plain Maven
    // artifact and versioned with the engine (483 = current release, proven latest on Central
    // 2026-07-23). This is the doris fe-sql-parser move — engine-exact parse authority — except
    // upstream ships it for us: pure JVM, no natives, no running engine needed. The validator
    // annotator drives it; census grading uses it as truth.
    implementation("io.trino:trino-parser:483")

    // TESTS ONLY: the official Trino JDBC driver — TrinoDriverFactsTest extracts driver class +
    // URL scheme from the jar itself (acceptsURL needs no server), keeping
    // config/trino-brikk-drivers.xml honest. The plugin does not bundle this jar; data sources
    // download it via the pinned artifact.
    testImplementation("io.trino:trino-jdbc:483")

    intellijPlatform {
        // Same platform window as doris/duckdb: compiled against DataGrip 2026.1 (build 261),
        // one artifact serving 261+262. Remote SDK so any clone/CI can build without an IDE.
        datagrip("2026.1.3")
        bundledPlugin("com.intellij.database")
        // Test-runtime transitive requirement of com.intellij.database (doris-intellij notes).
        bundledPlugin("com.intellij.modules.json")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
    }
    pluginVerification {
        ides {
            ide("DB", "2026.1.3")
            ide("IU", "262.8665.81")
        }
    }
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }
}

tasks {
    // Stable artifact name (no version suffix) so install-from-disk always points at the same file.
    buildPlugin {
        archiveVersion = ""
    }

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    named<Test>("test") {
        useJUnit()
        // Load our plugin (and the database plugin it depends on) in the light test fixture.
        systemProperty("idea.load.plugins.id", "com.intellij.database,dev.sort.trino-intellij-plugin")
        // Trino syntax corpus for the substrate scoreboard (census lane fills this).
        systemProperty("corpus.dir", layout.projectDirectory.dir("src/test/resources/corpus").asFile.absolutePath)
        // Live Trino suites (container or real cluster) are gated on this property; forward it
        // from the Gradle invocation (-Dtrino.live.url=... or -Ptrino.live.url=...) into the
        // forked test JVM. Absent => those tests JUnit-Assume out, keeping the committed run
        // offline-deterministic.
        val trinoLiveUrl = providers.systemProperty("trino.live.url")
            .orElse(providers.gradleProperty("trino.live.url"))
            .orNull
        if (trinoLiveUrl != null) systemProperty("trino.live.url", trinoLiveUrl)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// --- harvest tooling (manual tasks; output is committed corpus/resources) ---

sourceSets {
    create("tools")
}

dependencies {
    "toolsImplementation"("io.trino:trino-jdbc:483")
    "toolsImplementation"("io.trino:trino-parser:483")
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
