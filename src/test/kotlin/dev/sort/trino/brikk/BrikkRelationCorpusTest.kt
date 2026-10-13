package dev.sort.trino.brikk

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.database.psi.DbPsiFacade
import com.intellij.database.model.ObjectKind
import com.intellij.database.symbols.DasColumnSymbol
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.database.SqlDataSourceManager
import com.intellij.sql.dialects.SqlResolveMappings
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.sql.psi.SqlNameElement
import com.intellij.sql.psi.SqlResolveResult
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.intellij.*
import dev.brikk.house.sql.intellij.testing.BackgroundErrors
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.shape.SqlFragment
import dev.brikk.house.sql.shape.ShapeCatalog
import dev.brikk.house.sql.shape.Shape
import dev.brikk.house.sql.shape.ColumnShape
import dev.brikk.house.sql.generator.UnsupportedError
import dev.sort.trino.TrinoDbms
import dev.sort.trino.sql.TrinoSqlDialect
import dev.sort.trino.pipes.TrinoPipesSettings
import dev.sort.trino.validate.TrinoNativeValidation
import dev.sort.trino.validate.nativeErrors
import dev.brikk.house.sql.verify.VerifyResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class BrikkRelationCorpusTest : BasePlatformTestCase() {
    private val facts by lazy { resource("facts.json").let(Json::parseToJsonElement).jsonObject }
    private val columns get() = facts.getValue("columns").jsonObject
    private val shapes get() = facts.getValue("facts").jsonObject
    private val cases by lazy { resource("cases.jsonl").lineSequence().filter(String::isNotBlank)
        .map { Json.parseToJsonElement(it).jsonObject }.toList() }
    private val witnesses by lazy { resource("witnesses.json").let(Json::parseToJsonElement).jsonObject }
    private lateinit var errors: BackgroundErrors
    private var contracts = emptyMap<String, ShapeResult>()
    private val declarations = LinkedHashMap<String, PsiElement>()
    private val locations = LinkedHashMap<String, SourceLocation>()
    private var physicalColumns = emptySet<String>()
    private var previousPipes = false
    private var previousAutocomplete = false
    private interface CorpusTarget : NavigationTarget { val identity: String }

    override fun setUp() {
        super.setUp()
        errors = BackgroundErrors(testRootDisposable)
        previousAutocomplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
        previousPipes = project.service<TrinoPipesSettings>().enabled
        project.service<TrinoPipesSettings>().enabled = true
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
        InjectedLanguageManager.getInstance(project).registerMultiHostInjector(object : MultiHostInjector {
            override fun elementsToInjectIn() = listOf(KtStringTemplateExpression::class.java)
            override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
                if (!context.containingFile.name.startsWith("RelationCase-")) return
                val host = context as KtStringTemplateExpression
                registrar.startInjecting(TrinoSqlDialect)
                    .addPlace(null, null, host, TextRange(3, host.textLength - 3)).doneInjecting()
            }
        }, testRootDisposable)
        val ids = columns.values.filter { it.jsonObject.string("origin") != "PHYSICAL_TABLE" }
            .mapNotNull { it.jsonObject.stringOrNull("navigationId") }.distinct()
        val navigationFile = myFixture.addFileToProject("corpus/Declarations.kt", ids.joinToString("\n") {
            "val ${declarationName(it)} = 0"
        })
        val properties = PsiTreeUtil.findChildrenOfType(navigationFile, KtProperty::class.java).associateBy { it.name }
        ids.forEach { id ->
            val declaration = properties.getValue(declarationName(id))
            declarations[id] = declaration
            locations[id] = SourceLocation(navigationFile.virtualFile.url, declaration.textRange.startOffset, declaration.textRange.endOffset)
        }
    }

    override fun tearDown() = RunAll(
        { LookupManager.getInstance(project).hideActiveLookup() },
        { CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = previousAutocomplete },
        { project.service<TrinoPipesSettings>().enabled = previousPipes },
        { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() },
        { errors.assertNone() },
        { super.tearDown() },
    ).run()

    fun testCurrentRelationImplementationsAgainstAcceptanceCorpus() {
        assertEquals("2.1", facts.string("version"))
        assertEquals(156, cases.size)
        assertEquals(cases.size, cases.map { it.string("id") }.toSet().size)
        val usedWitnesses = cases.flatMap { case ->
            assertTrue(case.string("id"), case.string("catalogKnowledge") in setOf("CLOSED", "UNKNOWN"))
            case.getValue("engine").jsonObject.mapNotNull { (dialect, witness) ->
                if (witness == JsonNull) null else {
                    assertTrue(case.string("id"), JsonPrimitive(dialect) in case.array("dialects"))
                    val key = witness.jsonPrimitive.content
                    val oracle = witnesses.getValue(key).jsonObject
                    assertFalse(key, "error" in oracle || "expectFailure" in oracle)
                    assertEquals(key, dialect == "duckdb", oracle.getValue("verified").jsonPrimitive.boolean)
                    key
                }
            }
        }.toSet()
        assertEquals("No orphan native witnesses", usedWitnesses, witnesses.keys)
        val baseline = Json.parseToJsonElement(resource("duckdb-disagreements.json")).jsonObject
        val known = Json.parseToJsonElement(resource("trino-disagreements.json")).jsonObject
        assertTrue(known.keys.all { id -> cases.any { it.string("id") == id } })
        val differences = ArrayList<JsonObject>()
        val results = ArrayList<JsonObject>()
        val unexpected = ArrayList<String>()
        val resolved = ArrayList<String>()
        val stageCoverage = cases.flatMap { it.array("stages").map { stage -> stage.jsonPrimitive.content } }.toSet()
        assertTrue(stageCoverage.toString(), stageCoverage.containsAll(setOf("SELECT", "EXTEND", "SET", "DROP", "RENAME", "AS",
            "WHERE", "AGGREGATE", "ORDER BY", "LIMIT", "OFFSET", "DISTINCT", "PIVOT", "UNPIVOT", "TABLESAMPLE", "WINDOW", "CALL",
            "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN", "SEMI JOIN", "ANTI JOIN", "RIGHT SEMI JOIN", "RIGHT ANTI JOIN",
            "UNION ALL", "UNION DISTINCT", "INTERSECT", "EXCEPT")))
        val applicable = cases.filter { "trino" in it.array("dialects").map { dialect -> dialect.jsonPrimitive.content } }
        assertTrue(known.keys.all { id -> applicable.any { it.string("id") == id } })
        for (case in applicable) {
            val id = case.string("id")
            val sql = case.string("sql")
            contracts = (case.getValue("relations").jsonObject + case.getValue("callContracts").jsonObject)
                .mapValues { (_, value) -> contract(value.jsonPrimitive.content) }
            val expected = expected(case)
            val actual = withPhysicalTables(case) {
                val file = configure(id, sql)
                val analysis = BrikkSqlScopes.analyze(sql, contracts)
                val references = case.array("references").map { it.jsonObject }
                val observed = linkedMapOf<String, JsonElement>(
                    "references" to JsonArray(references.map { reference ->
                        val start = reference.int("start")
                        val scoped = analysis?.at(start)
                        reference(reference, scoped?.let { targets(it.scope.candidates(it.name, it.qualifier)) }.orEmpty())
                    }),
                    "resolve" to JsonArray(references.map { reference -> reference(reference, nativeTargets(file, reference.int("start"))) }),
                    "navigation" to JsonArray(references.map { reference -> navigationReference(reference, navigation(file, reference.int("start"))) }),
                    "diagnostics" to diagnostics(file, case),
                )
                val scopes = ArrayList<JsonObject>()
                val completions = ArrayList<JsonObject>()
                val orders = ArrayList<JsonObject>()
                for (caret in case.array("carets").map { it.jsonObject }) {
                    val offset = caret.int("offset")
                    assertTrue("$id caret", offset in 0..sql.length)
                    val marked = sql.substring(0, offset) + "__brikk_caret" + sql.substring(offset)
                    val scoped = BrikkSqlScopes.analyze(marked, contracts)?.at(offset)
                    val selected = scoped?.takeIf { it.kind == BrikkSqlScopes.Kind.COLUMN }?.let {
                        if (it.qualifier.isBlank()) it.scope.columns else listOfNotNull(it.scope.relation(it.qualifier))
                            .flatMap { relation -> relation.columns.map { column -> relation to column } }
                    }.orEmpty()
                    scopes += buildJsonObject {
                        put("offset", offset)
                        put("knowledge", knowledge(scoped?.scope, caret.string("positionKind")))
                        put("columns", sorted(selected.map { (relation, column) -> observedColumn(relation, column) }))
                    }
                    if (caret.string("order") == "SIGNIFICANT") orders += buildJsonObject {
                        put("offset", offset); put("columns", strings(selected.map { it.second.name }))
                    }
                    configure(id, sql.substring(0, offset) + "<caret>" + sql.substring(offset))
                    myFixture.completeBasic()
                    val items = myFixture.lookupElements.orEmpty()
                    completions += buildJsonObject {
                        put("offset", offset)
                        put("columns", sorted(items.mapNotNull(::completion)))
                    }
                    LookupManager.getInstance(project).hideActiveLookup()
                }
                observed["scope"] = JsonArray(scopes)
                observed["completion"] = JsonArray(completions)
                if (orders.isNotEmpty()) observed["order"] = JsonArray(orders)
                case["coreOrder"]?.let { observed["coreOrder"] = coreOrder(case) }
                case["coreRefusal"]?.let {
                    observed["coreRefusal"] = JsonPrimitive(try {
                        SqlFragment(sql, "trino").toExecutable("trino")
                        false
                    } catch (_: UnsupportedError) {
                        true
                    })
                }
                observed
            }
            val allowed = known[id]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
            assertTrue(id, allowed.all { it in expected.keys })
            val axes = ArrayList<JsonObject>()
            for ((axis, wanted) in expected) {
                val got = actual.getValue(axis)
                val equal = wanted == got
                axes += buildJsonObject { put("axis", axis); put("expected", wanted); put("actual", got); put("matches", equal) }
                if (equal) {
                    if (axis in allowed) resolved += "$id/$axis"
                } else {
                    differences += buildJsonObject {
                        put("id", id); put("category", case.string("category")); put("axis", axis); put("input", sql)
                        put("expected", wanted); put("actual", got); put("known", axis in allowed)
                        put("judgement", facts.getValue("evidence").jsonObject.getValue(case.string("category")))
                    }
                    if (axis !in allowed) unexpected += "$id/$axis"
                }
            }
            results += buildJsonObject {
                put("id", id); put("input", sql); put("axes", JsonArray(axes))
                put("changes", case.getValue("changes"))
                put("serializableResults", serializableResults(case, axes))
                put("dialects", case.getValue("dialects")); put("positions", JsonArray(case.array("carets").map { caret ->
                    buildJsonObject { put("offset", caret.jsonObject.int("offset")); put("kind", caret.jsonObject.string("positionKind")) }
                }))
                put("catalogKnowledge", case.getValue("catalogKnowledge"))
                case.getValue("engine").jsonObject["trino"]?.takeUnless { it == JsonNull }?.let { witness ->
                    val key = witness.jsonPrimitive.content
                    put("engine", engine(witnesses.getValue(key).jsonObject, key))
                }
            }
            errors.assertNone()
        }
        val report = buildJsonObject {
            put("corpusVersion", facts.getValue("version")); put("dialect", "trino")
            put("corpusSize", cases.size); put("disagreementCount", differences.size)
            put("applicableCases", applicable.size)
            put("notApplicable", strings((cases - applicable.toSet()).map { it.string("id") }))
            put("engineWitnessCount", 0)
            put("authoredEngineWitnessCount", applicable.count { it.getValue("engine").jsonObject["trino"] != JsonNull })
            put("disagreeingCases", differences.map { it.string("id") }.toSet().size)
            put("resolvedKnownDisagreements", strings(resolved))
            put("sourceBaselinePrunedV1Flags", baseline.getValue("prunedV1Flags"))
            put("sourceBaselineNotes", baseline.getValue("notes"))
            put("stageCoverage", strings(stageCoverage.sorted()))
            put("representationNotes", facts.getValue("representationNotes"))
            put("dialectNotes", facts.getValue("dialectNotes"))
            put("stageAliasPolicy", facts.getValue("stageAliasPolicy"))
            put("resultContract", facts.getValue("resultContract"))
            put("inputReviews", facts.getValue("inputReviews"))
            put("caseChanges", JsonObject(cases.associate { it.string("id") to it.getValue("changes") }))
            put("adapter", "Identical post-replacement SQL in genuine Trino injection into a raw Kotlin string, with copied facts through BrikkTrinoProfile.RELATIONS on the injection host. No Kotlin Analysis API or new analyzer. Physical schemas use real SQL-file data sources. Completion runs every registered contributor, with auto-insert disabled and items read from lookupElements. Resolve uses native SQL PSI and the existing Brikk editor support for flat PIPE PSI; the private ContractColumnSymbol column payload is read reflectively. Navigation uses the Brikk handler plus native fallback. Diagnostics use the real contract annotator and registered SqlResolveInspection for relation probes. Catalog knowledge is recorded, not invented by the adapter. Trino-tagged witnesses are grammar-checked only, not executed; unverified witnesses remain unverified. No SQL or offset adaptation.")
            put("categories", JsonObject(differences.groupBy { it.string("category") }.mapValues { JsonArray(it.value) }))
            put("categorySummary", JsonObject(applicable.groupBy { it.string("category") }.mapValues { (category, entries) ->
                val mismatches = differences.filter { it.string("category") == category }
                buildJsonObject {
                    put("cases", entries.size); put("disagreements", mismatches.size)
                    put("disagreeingCases", mismatches.map { it.string("id") }.toSet().size)
                }
            }))
            put("results", JsonArray(results))
        }
        val output = Path.of("build", "relation-corpus", "trino-${ApplicationInfo.getInstance().build.baselineVersion}.json")
        Files.createDirectories(output.parent)
        Files.writeString(output, Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report))
        println("Trino relation corpus v2.1: ${cases.size} cases, ${applicable.size} applicable, ${differences.size} disagreements in ${differences.map { it.string("id") }.toSet().size} cases, ${resolved.size} resolved; $output")
        assertTrue("Unlisted relation disagreements: ${unexpected.joinToString()}; see $output", unexpected.isEmpty())
    }

    private fun resource(name: String) = Files.readString(Path.of(System.getProperty("corpus.dir"))
        .resolveSibling("relation-corpus").resolve(name))
    private fun declarationName(id: String) = id.replace(Regex("[^A-Za-z0-9_]"), "_")

    private fun serializableResults(case: JsonObject, axes: List<JsonObject>): JsonObject {
        val sql = case.string("sql")
        fun range(start: Int, end: Int) = buildJsonObject { put("start", start); put("end", end) }
        fun completionItems(value: JsonElement): JsonArray = JsonArray(value.jsonArray.flatMap { row ->
            val caret = row.jsonObject.int("offset")
            val prefix = sql.substring(0, caret).takeLastWhile { Character.isUnicodeIdentifierPart(it) }
            row.jsonObject.array("columns").map { column ->
                val fields = column.jsonObject
                buildJsonObject {
                    put("name", fields.getValue("name")); put("kind", "COLUMN")
                    put("insertText", fields.getValue("name")); put("range", range(caret - prefix.length, caret))
                    put("detail", "${fields.string("origin")} ${fields.string("relation")} ${fields.string("displayType")}".trim())
                }
            }
        })
        fun diagnosticItems(value: JsonElement) = JsonArray(value.jsonArray.map { item ->
            val diagnostic = item.jsonObject
            val start = diagnostic.int("start"); val end = diagnostic.int("end")
            buildJsonObject {
                put("kind", diagnostic.getValue("kind")); put("range", range(start, end))
                put("severity", if (diagnostic.string("kind") == "PARTIAL_CONTRACT") "WARNING" else "ERROR")
                put("message", diagnostic.string("kind").lowercase().replace('_', ' ') + ": " + sql.substring(start, end))
            }
        })
        return buildJsonObject {
            for (axis in axes.filter { it.string("axis") in setOf("completion", "diagnostics", "references") }) {
                put(axis.string("axis"), buildJsonObject {
                    for (side in listOf("expected", "actual")) {
                        val value = axis.getValue(side)
                        put(side, when (axis.string("axis")) {
                            "completion" -> completionItems(value)
                            "diagnostics" -> diagnosticItems(value)
                            else -> JsonArray(value.jsonArray.map { item ->
                                val reference = item.jsonObject
                                val start = reference.int("start"); val end = reference.int("end")
                                val probe = case.array("references").map { it.jsonObject }.single { it.int("start") == start && it.int("end") == end }
                                buildJsonObject {
                                    put("name", sql.substring(start, end)); put("kind", probe.getValue("kind"))
                                    put("range", range(start, end)); put("targets", reference.getValue("targets"))
                                }
                            })
                        })
                    }
                })
            }
        }
    }

    private fun contract(id: String): ShapeResult {
        val shape = shapes.getValue(id).jsonObject
        if (shape.string("knowledge") == "UNKNOWN") return ShapeResult.Unavailable("corpus unknown input")
        val copied = shape.array("columns").map { key ->
            val column = columns.getValue(key.jsonPrimitive.content).jsonObject
            val navigation = column.stringOrNull("navigationId")?.let { target -> object : CorpusTarget {
                override val identity = column.string("identity")
                override val kind = if (column.string("origin") == "RELATION_CONTRACT") NavigationKind.RELATION_FALLBACK else NavigationKind.PROPERTY
                override val location get() = locations.getValue(target)
                override fun navigate(requestFocus: Boolean) {
                    val declaration = declarations.getValue(target)
                    OpenFileDescriptor(project, declaration.containingFile.virtualFile, declaration.textOffset).navigate(requestFocus)
                }
            } }
            ColumnContract(column.string("name"), column.stringOrNull("kotlinType"),
                ColumnNullability.valueOf(column.string("nullability")), BrikkTypeFamilies.forClassId(column.stringOrNull("kotlinType")), navigation)
        }
        return ShapeResult.Available(RelationShape(ShapeKnowledge.valueOf(shape.string("knowledge")), copied, ShapeOrigin.DECLARED))
    }

    private fun configure(id: String, sql: String): PsiFile {
        val hostFile = myFixture.configureByText("RelationCase-$id.kt", "val sql = \"\"\"$sql\"\"\"")
        val manager = InjectedLanguageManager.getInstance(project)
        val host = requireNotNull(PsiTreeUtil.findChildOfType(manager.getTopLevelFile(hostFile), KtStringTemplateExpression::class.java))
        val file = requireNotNull(manager.getInjectedPsiFiles(host)?.single()).first.containingFile
        host.putUserData(BrikkTrinoProfile.RELATIONS) { contracts }
        file.putUserData(BrikkTrinoProfile.KEY, BrikkTrinoProfile(listOf("min_id"), contracts.keys.toList(), emptyList()))
        assertEquals(contracts, BrikkTrinoProfile.contracts(file))
        assertEquals(id, sql.replace("<caret>", ""), file.text)
        return file
    }

    private fun <T> withPhysicalTables(case: JsonObject, action: () -> T): T {
        val tables = case.getValue("physicalTables").jsonObject
        physicalColumns = tables.values.flatMap { fact -> shapes.getValue(fact.jsonPrimitive.content).jsonObject.array("columns") }
            .map { columns.getValue(it.jsonPrimitive.content).jsonObject.string("name") }.toSet()
        if (tables.isEmpty()) return action()
        val ddl = tables.entries.joinToString("\n") { (name, fact) ->
            val fields = shapes.getValue(fact.jsonPrimitive.content).jsonObject.array("columns").joinToString(", ") { key ->
                val column = columns.getValue(key.jsonPrimitive.content).jsonObject
                "${column.string("name")} ${column.string("sqlType")}" + if (column.string("nullability") == "NON_NULL") " NOT NULL" else ""
            }
            "CREATE TABLE $name ($fields);"
        }
        val schema = myFixture.addFileToProject("corpus/${case.string("id")}-schema.sql", ddl)
        setSqlDialectMapping(project, schema.virtualFile, TrinoSqlDialect, testRootDisposable)
        val manager = SqlDataSourceManager.getInstance(project)
        val source = manager.createEmpty().apply {
            name = "Relation acceptance ${case.string("id")}"; definedDbms = TrinoDbms.TRINO_BRIKK
            setFiles(arrayOf(schema.virtualFile))
        }
        manager.addDataSource(source)
        try {
            val sync = ApplicationManager.getApplication().executeOnPooledThread {
                source.sync()
                source.javaClass.methods.firstOrNull { it.name == "waitComputed" && it.parameterCount == 0 }?.invoke(source)
            }
            PlatformTestUtil.waitForFuture(sync)
            DbPsiFacade.getInstance(project).clearCaches()
            SqlResolveMappings.getInstance(project).setMapping(null, SqlResolveMappings.ALL_DATA_SOURCES)
            return action()
        } finally {
            SqlResolveMappings.getInstance(project).setMapping(null, null)
            manager.removeDataSource(source)
            DbPsiFacade.getInstance(project).clearCaches()
        }
    }

    private fun expected(case: JsonObject): Map<String, JsonElement> {
        val references = case.array("references")
        val carets = case.array("carets").map { it.jsonObject }
        val resolved = JsonArray(references.map { value ->
            val reference = value.jsonObject
            reference(reference, reference.array("targets").map { value ->
                val column = expectedColumn(value)
                target(column.string("relation"), column.string("name"), column.string("identity"), column.stringOrNull("navigationId"))
            })
        })
        return linkedMapOf<String, JsonElement>(
            "references" to resolved,
            "resolve" to resolved,
            "navigation" to JsonArray(references.map { ref ->
                val reference = ref.jsonObject
                navigationReference(reference, reference["navigationTargets"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: reference.array("targets").mapNotNull { expectedColumn(it).stringOrNull("navigationId") })
            }),
            "diagnostics" to sorted(case.array("diagnostics")),
            "scope" to JsonArray(carets.map { caret -> buildJsonObject {
                put("offset", caret.int("offset")); put("knowledge", caret.getValue("knowledge"))
                put("columns", sorted(caret.array("columns").map(::expectedColumn)))
            } }),
            "completion" to JsonArray(carets.map { caret -> buildJsonObject {
                put("offset", caret.int("offset"))
                put("columns", sorted((caret["completionColumns"] ?: caret.getValue("columns")).jsonArray.map { tuple ->
                    val column = expectedColumn(tuple)
                    completionColumn(column.string("name"), column.string("relation"), column.string("origin"), display(column), column.string("nullability"))
                }))
            } }),
        ).apply {
            val ordered = carets.filter { it.string("order") == "SIGNIFICANT" }
            if (ordered.isNotEmpty()) put("order", JsonArray(ordered.map { caret -> buildJsonObject {
                put("offset", caret.int("offset")); put("columns", strings(caret.array("columns").map { expectedColumn(it).string("name") }))
            } }))
            case["coreOrder"]?.let { put("coreOrder", it) }
            case["coreRefusal"]?.let { put("coreRefusal", it) }
        }
    }

    private fun expectedColumn(value: JsonElement): JsonObject {
        val tuple = value.jsonArray
        val column = columns.getValue(tuple[0].jsonPrimitive.content).jsonObject
        fun override(index: Int, key: String) = tuple.getOrNull(index)?.takeUnless { it == JsonNull } ?: column.getValue(key)
        return buildJsonObject {
            put("name", override(2, "name")); put("relation", tuple[1]); put("origin", override(4, "origin"))
            put("kotlinType", column.getValue("kotlinType")); put("sqlType", column.getValue("sqlType"))
            put("nullability", override(3, "nullability")); put("navigationId", override(5, "navigationId"))
            put("identity", override(6, "identity"))
        }
    }

    private fun observedColumn(relation: BrikkSqlScopes.Relation, column: ColumnContract) = buildJsonObject {
        put("name", column.name); put("relation", relation.alias)
        put("origin", when (column.navigation?.kind) {
            NavigationKind.PROPERTY -> "KOTLIN_RELATION"
            NavigationKind.RELATION_FALLBACK -> "RELATION_CONTRACT"
            null -> "COMPUTED"
        })
        put("kotlinType", nullable(column.kotlinClassId)); put("sqlType", nullable(column.exactSqlType))
        put("nullability", column.nullability.name); put("navigationId", nullable(idOf(column.navigation?.location)))
        put("identity", identity(column))
    }

    private fun targets(candidates: List<Pair<BrikkSqlScopes.Relation, ColumnContract>>): List<JsonObject> =
        candidates.singleOrNull()?.let { (relation, column) ->
            listOf(target(relation.alias, column.name, identity(column), idOf(column.navigation?.location)))
        }.orEmpty()

    private fun target(relation: String, column: String, identity: String, navigation: String?) = buildJsonObject {
        put("relation", relation); put("column", column); put("identity", identity); put("navigationId", nullable(navigation))
    }
    private fun identity(column: ColumnContract) = (column.navigation as? CorpusTarget)?.identity ?: "computed.${column.name}"

    private fun knowledge(scope: BrikkSqlScopes.Scope?, position: String): String = when {
        position != "COLUMN" -> "NONE"
        scope == null -> "UNKNOWN"
        scope.complete -> "FULL"
        scope.columns.isNotEmpty() || scope.relations.any { it.available } -> "PARTIAL"
        else -> "UNKNOWN"
    }

    private fun coreOrder(case: JsonObject): JsonElement {
        val inputs = case.getValue("relations").jsonObject.mapValues { (_, fact) ->
            Shape(shapes.getValue(fact.jsonPrimitive.content).jsonObject.array("columns").map { value ->
                val column = columns.getValue(value.jsonPrimitive.content).jsonObject
                ColumnShape(column.string("name"), column.stringOrNull("sqlType") ?: "UNKNOWN", when (column.string("nullability")) {
                    "NON_NULL" -> false; "NULLABLE" -> true; else -> null
                })
            })
        }
        return strings(SqlFragment(case.string("sql"), "trino").outputShape(ShapeCatalog(emptyMap(), inputs)).names())
    }

    private fun idOf(location: SourceLocation?) = locations.entries.firstOrNull { it.value == location }?.key
    private fun idOf(element: PsiElement?) = declarations.entries.firstOrNull { (_, declaration) ->
        element != null && element.containingFile === declaration.containingFile &&
            declaration.textRange.contains(element.textRange)
    }?.key

    private fun reference(expected: JsonObject, targets: List<JsonObject>) = buildJsonObject {
        put("start", expected.int("start")); put("end", expected.int("end")); put("targets", sorted(targets))
    }

    private fun navigationReference(expected: JsonObject, targets: List<String>) = buildJsonObject {
        put("start", expected.int("start")); put("end", expected.int("end")); put("targets", strings(targets.sorted()))
    }

    private fun nativeReference(file: PsiFile, start: Int): SqlReferenceExpression? =
        PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java)
            .filter { it.identifier?.textRange?.startOffset == start }.minByOrNull { it.textLength }

    private fun nativeTargets(file: PsiFile, start: Int): List<JsonObject> {
        val reference = nativeReference(file, start) ?: return file.findElementAt(start)?.let { element ->
            BrikkSqlEditorSupport.reference(element, start)?.takeIf { it.kind == BrikkSqlScopes.Kind.COLUMN }
                ?.let { targets(it.scope.candidates(it.name, it.qualifier)) }
        }.orEmpty()
        val resolved = reference.reference.resolve() ?: return emptyList()
        val result = reference.resolveSingle() as? SqlResolveResult
        val qualifier = reference.qualifierExpression?.text
            ?: (result?.immediateQualifier as? SqlNameElement)?.name
            ?: (result?.qualifier as? SqlNameElement)?.name
        val symbol = resolved as? DasColumnSymbol ?: reference.resolveSymbol()
        val contract = (symbol as? DasColumnSymbol)?.let(::contractColumn)
        if (contract != null) return listOf(target(qualifier ?: (symbol as DasColumnSymbol).dasObject?.table?.name.orEmpty(),
            contract.name, identity(contract), idOf(contract.navigation?.location)))
        if (symbol is DasColumnSymbol) return symbol.dasObject?.name?.let {
            listOf(target(qualifier.orEmpty(), it, "physical.$it", "decl:physical.$it"))
        }.orEmpty()
        val name = (resolved as? PsiNamedElement)?.name ?: return emptyList()
        return listOf(target("", name, "computed.$name", idOf(resolved.navigationElement)))
    }

    private fun navigation(file: PsiFile, start: Int): List<String> {
        val element = file.findElementAt(start) ?: return emptyList()
        val targets = BrikkSqlGotoDeclarationHandler().getGotoDeclarationTargets(element, start, myFixture.editor)
            ?.mapNotNull { idOf(it) }.orEmpty()
        if (targets.isNotEmpty()) return targets.distinct()
        return nativeTargets(file, start).mapNotNull { it.stringOrNull("navigationId") }
    }

    private fun diagnostics(file: PsiFile, case: JsonObject): JsonArray {
        val holder = AnnotationHolderImpl(AnnotationSession(file), false)
        holder.runAnnotatorWithContext(file, BrikkSqlContractAnnotator())
        holder.assertAllAnnotationsCreated()
        val result = holder.map { annotation -> buildJsonObject {
            put("kind", when {
                annotation.message.startsWith("Ambiguous") -> "AMBIGUOUS"
                annotation.message.contains("partial relation contract") -> "PARTIAL_CONTRACT"
                else -> "MISSING"
            })
            put("start", annotation.startOffset); put("end", annotation.endOffset)
            put("subject", "COLUMN")
        } }.toMutableList()
        val probes = case.array("references").map { it.jsonObject }.filter { it.string("kind") in setOf("QUALIFIER", "TABLE") }
        if (probes.isNotEmpty()) {
            val inspection = com.intellij.codeInspection.ex.InspectionToolRegistrar.getInstance().createTools()
                .single { it.shortName == "SqlResolveInspection" }.tool
            myFixture.enableInspections(inspection)
            val manager = InjectedLanguageManager.getInstance(project)
            for (info in myFixture.doHighlighting().filter { it.inspectionToolId == "SqlResolveInspection" }) {
                for (probe in probes) {
                    val start = manager.injectedToHost(file, probe.int("start"))
                    val end = manager.injectedToHost(file, probe.int("end"))
                    if (info.startOffset == start && info.endOffset == end) result += buildJsonObject {
                        put("kind", if (probe.string("kind") == "QUALIFIER") "UNKNOWN_QUALIFIER" else "UNKNOWN_TABLE")
                        put("subject", probe.getValue("kind")); put("start", probe.int("start")); put("end", probe.int("end"))
                    }
                }
            }
        }
        return sorted(result.distinct())
    }

    private fun completion(item: LookupElement): JsonObject? {
        val shown = LookupElementPresentation().also(item::renderElement)
        val tail = shown.tailText.orEmpty()
        val symbol = (item.`object` as? SqlResolveResult)?.targetSymbol ?: item.psiElement as? DasColumnSymbol
        val contract = (symbol as? DasColumnSymbol)?.let(::contractColumn)
        val physical = (symbol as? DasColumnSymbol)?.takeIf { contract == null }?.dasObject
        val origin = when {
            tail.contains("relation contract") -> "RELATION_CONTRACT"
            contract?.navigation?.kind == NavigationKind.RELATION_FALLBACK -> "RELATION_CONTRACT"
            contract != null && contract.navigation == null -> "COMPUTED"
            tail.contains("brikk column") || contract != null -> "KOTLIN_RELATION"
            physical != null && physical.kind == ObjectKind.COLUMN && item.lookupString == physical.name && physical.name in physicalColumns -> "PHYSICAL_TABLE"
            shown.typeText == "PIPE input column" -> "COMPUTED"
            else -> return null
        }
        val relation = Regex("\\(([^()]*)\\)$").find(tail)?.groupValues?.get(1)
            ?: (symbol as? DasColumnSymbol)?.dasObject?.table?.name.orEmpty()
        return completionColumn(item.lookupString, relation, origin, shown.typeText.orEmpty().let {
            if (origin == "PHYSICAL_TABLE") it.uppercase() else it
        }, when {
            contract != null -> contract.nullability.name
            origin == "PHYSICAL_TABLE" -> if (physical?.isNotNull == true) "NON_NULL" else "NULLABLE"
            shown.typeText.orEmpty().contains("nullability unknown") || shown.typeText == "PIPE input column" -> "UNKNOWN"
            shown.typeText.orEmpty().endsWith('?') -> "NULLABLE"
            else -> "NON_NULL"
        })
    }

    private fun completionColumn(name: String, relation: String, origin: String, type: String, nullability: String) = buildJsonObject {
        put("name", name); put("relation", relation); put("origin", origin); put("displayType", type); put("nullability", nullability)
    }

    private fun contractColumn(symbol: DasColumnSymbol): ColumnContract? {
        if (symbol.javaClass.name != "dev.sort.trino.brikk.ContractColumnSymbol") return null
        return symbol.javaClass.getDeclaredField("column").apply { isAccessible = true }.get(symbol) as ColumnContract
    }

    private fun display(column: JsonObject): String {
        val base = column.stringOrNull("kotlinType")?.substringAfterLast('.') ?: column.stringOrNull("sqlType") ?: "unknown type"
        if (column.string("origin") == "PHYSICAL_TABLE") return base
        return base + when (column.string("nullability")) { "NULLABLE" -> "?"; "UNKNOWN" -> " (nullability unknown)"; else -> "" }
    }

    private fun engine(oracle: JsonObject, id: String): JsonObject {
        val verdict = TrinoNativeValidation.verify(oracle.string("sql"))
        return buildJsonObject {
            put("witnessId", id); put("verified", false); put("authored", oracle)
            put("parserOnly", true); put("syntaxAccepted", verdict == VerifyResult.Accepted)
            put("syntaxErrors", JsonArray(verdict.nativeErrors().map { error -> buildJsonObject {
                put("message", error.message)
                put("offset", error.offset?.let(::JsonPrimitive) ?: JsonNull)
            } }))
        }
    }

    private fun JsonObject.string(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.stringOrNull(key: String) = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int
    private fun JsonObject.array(key: String) = getValue(key).jsonArray
    private fun nullable(value: String?): JsonElement = value?.let(::JsonPrimitive) ?: JsonNull
    private fun strings(values: Collection<String>) = JsonArray(values.map(::JsonPrimitive))
    private fun sorted(values: Collection<JsonElement>) = JsonArray(values.sortedBy(JsonElement::toString))
}
