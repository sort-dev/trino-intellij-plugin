import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.util.zip.ZipFile
import java.util.jar.JarInputStream

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.sort.trino"
version = "0.3.5"

val brikkSqlVersion = providers.gradleProperty("brikkSqlVersion").getOrElse("0.19.1")
val sqlIdeVersion = providers.gradleProperty("sqlIdeVersion").getOrElse("0.1.1")
val kotlinIdeVersion = providers.gradleProperty("kotlinIdeVersion").getOrElse("0.2.0")
val kotlinInstalled = providers.gradleProperty("test.kotlin").orNull != "absent"
val companionInstalled = providers.gradleProperty("test.sqlTranspiler").orNull == "installed"
val siblingZips = listOf("doris", "duckdb").mapNotNull { dialect ->
    providers.gradleProperty("test.${dialect}PluginZip").orNull?.let { path ->
        val zip = file(path)
        require(zip.isFile) { "Missing $dialect plugin ZIP: $zip" }
        dialect to zip
    }
}

repositories {
    if (sqlIdeVersion.endsWith("-SNAPSHOT")) mavenLocal {
        content {
            includeVersion("dev.brikk.house", "brikk-sql-intellij-support", sqlIdeVersion)
        }
    }
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("dev.brikk.house:brikk-sql-jvm:$brikkSqlVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-serialization-json-jvm")
    }
    implementation("dev.brikk.house:brikk-sql-intellij-support:$sqlIdeVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation("dev.brikk.house:brikk-engine-kotlin-intellij-support:$kotlinIdeVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
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
        val localIde = providers.gradleProperty("trino.localIde")
        if (localIde.isPresent) local(localIde) else intellijIdea("2026.1.3")
        bundledPlugin("com.intellij.database")
        // Test-runtime transitive requirement of com.intellij.database (doris-intellij notes).
        bundledPlugin("com.intellij.modules.json")
        if (kotlinInstalled) {
            bundledPlugin("org.jetbrains.kotlin")
            bundledPlugin("com.intellij.java")
        }
        bundledPlugin("Git4Idea")
        if (companionInstalled) plugin("dev.sort.sql-transpiler-intellij-plugin:0.3.0")
        siblingZips.forEach { (_, zip) -> localPlugin(zip) }
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
        failureLevel.set(listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        ))
        ides {
            create("DB", "2026.1.3") {}
            create("IU", "262.8665.81") {}
            create("DB", "2026.2.5") {}
            create("IU", "263.4732.28") {}
            create("IU", "263.5701.42") {}
        }
    }
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }
}

tasks {
    named<JavaExec>("verifyPlugin") {
        // Other dogfood agents also run the verifier; never share its extracted-plugin cache.
        systemProperty("plugin.verifier.home.dir", layout.buildDirectory.dir("plugin-verifier-cache").get().asFile.absolutePath)
    }
    processResources {
        inputs.property("sqlIdeVersion", sqlIdeVersion)
        from(files("THIRD_PARTY_NOTICES.md")) {
            into("META-INF")
            filter { line: String -> line.replace("brikk-sql-intellij-support:0.1.1", "brikk-sql-intellij-support:$sqlIdeVersion") }
        }
        // The shared Apache-2.0 library carries the complete license, identical to this product's license.
        from({ zipTree(configurations.runtimeClasspath.get().single { it.name.startsWith("brikk-sql-intellij-support-") }) }) {
            include("META-INF/LICENSE")
        }
    }
    processTestResources {
        from("src/main/resources/META-INF/trino-kotlin.xml") { into("META-INF") }
    }
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
        systemProperty("java.awt.headless", "true")
        systemProperty("idea.load.plugins.id", buildList {
            add("com.intellij.database")
            add("dev.sort.trino-intellij-plugin")
            add("Git4Idea")
            if (kotlinInstalled) { add("org.jetbrains.kotlin"); add("com.intellij.java") }
            if (companionInstalled) add("dev.sort.sql-transpiler-intellij-plugin")
            siblingZips.forEach { (name, _) -> add("dev.sort.$name-intellij-plugin") }
        }.joinToString(","))
        if (!kotlinInstalled) exclude("**/brikk/**")
        if (providers.gradleProperty("test.pluginIsolation").orNull != "true") exclude("**/TrinoPipesIsolationTest.class")
        providers.gradleProperty("test.brikkCompilerPlugin").orNull?.let {
            require(file(it).isFile) { "Missing compiler fixture JAR: $it" }
            systemProperty("test.brikkCompilerPlugin", file(it).absolutePath)
        } ?: exclude("**/BrikkCompilerRelationTest.class")
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
        // Use SDK interface defaults directly instead of generating spurious internal-API overrides.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

// The flattened fixture must use our own libraries. Production products own isolated copies.
tasks.named<Test>("test") {
    classpath = classpath.filter { entry ->
        !(listOf("doris", "duckdb", "sql-transpiler").any { entry.path.contains("/$it-intellij-plugin/") } &&
            (entry.name.startsWith("brikk-") || entry.name.startsWith("kotlin-stdlib")))
    }
}
if (providers.gradleProperty("test.pluginIsolation").orNull == "true") {
    val outputs = sourceSets.main.get().output.files.map { it.absoluteFile }.toSet() +
        layout.buildDirectory.dir("instrumented/instrumentCode").get().asFile.absoluteFile
    val resources = sourceSets.test.get().output.resourcesDir?.absoluteFile
    tasks.named<Test>("test") {
        include("**/TrinoPipesIsolationTest.class")
        systemProperty("test.pluginIsolation", "true")
        classpath = classpath.filter {
            it.absoluteFile !in outputs && it.absoluteFile != resources &&
                !listOf("doris", "duckdb", "sql-transpiler").any { peer -> it.path.contains("/$peer-intellij-plugin/") } &&
                !it.name.startsWith("trino-intellij-plugin-") && !it.name.startsWith("brikk-") &&
                !it.name.startsWith("trino-parser-") && !it.name.startsWith("trino-grammar-")
        }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Didea.force.use.core.classloader=true", "-Didea.use.core.classloader.for.plugin.path=false")
        })
    }
}

val verifyEmbeddedPipes by tasks.registering {
    group = "verification"
    val archive = tasks.named<Zip>("buildPlugin").flatMap { it.archiveFile }
    val ownJar = tasks.named<org.gradle.jvm.tasks.Jar>("composedJar").flatMap { it.archiveFileName }
    dependsOn("buildPlugin")
    inputs.file(archive)
    doLast {
        ZipFile(archive.get().asFile).use { zip ->
            val jars = zip.entries().asSequence().filter { it.name.endsWith(".jar") }.toList()
            val names = jars.map { it.name.substringAfterLast('/') }
            val required = setOf(ownJar.get(), "brikk-sql-jvmMain-$brikkSqlVersion.jar", "brikk-sql-metadata-jvmMain-$brikkSqlVersion.jar",
                "brikk-sql-intellij-support-$sqlIdeVersion.jar", "brikk-engine-kotlin-intellij-support-$kotlinIdeVersion.jar",
                "trino-parser-483.jar", "trino-grammar-483.jar", "antlr4-runtime-4.13.2.jar")
            val transitive = setOf("annotations-13.0.jar", "error_prone_annotations-2.41.0.jar", "failureaccess-1.0.3.jar",
                "guava-33.5.0-jre.jar", "j2objc-annotations-3.1.jar", "jakarta.annotation-api-3.0.0.jar", "jspecify-1.0.0.jar",
                "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar")
            check(names.size == names.toSet().size && names.toSet().containsAll(required) && names.all { it in required || it in transitive }) {
                "Unexpected bundled libraries: $names"
            }
            val resources = mutableMapOf<String, String>()
            for (library in jars) JarInputStream(zip.getInputStream(library)).use { jar ->
                while (true) {
                    val entry = jar.nextJarEntry ?: break
                    if (entry.name.endsWith(".class")) {
                        val header = jar.readNBytes(8)
                        check(header.size == 8)
                        val major = ((header[6].toInt() and 255) shl 8) or (header[7].toInt() and 255)
                        val maximum = if (library.name.endsWith("/trino-parser-483.jar")) 69 else 65
                        check(major <= maximum) { "Unexpected bytecode $major: ${library.name}/${entry.name}" }
                    } else if (library.name.endsWith("/" + ownJar.get()) && entry.name in setOf("META-INF/plugin.xml", "META-INF/LICENSE", "META-INF/THIRD_PARTY_NOTICES.md")) {
                        resources[entry.name] = jar.readBytes().toString(Charsets.UTF_8)
                    }
                }
            }
            check(resources.size == 3)
            check(!Regex("<depends[^>]*>\\s*dev.sort.sql-transpiler-intellij-plugin").containsMatchIn(resources.getValue("META-INF/plugin.xml")))
            check("brikk-sql-intellij-support" in resources.getValue("META-INF/THIRD_PARTY_NOTICES.md"))
        }
    }
}
tasks.named("check") { dependsOn(verifyEmbeddedPipes) }

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
