package dev.sort.trino.pipes

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionWithDelegate
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Fixture core never links product implementations in this dedicated worker. */
class TrinoPipesIsolationTest : BasePlatformTestCase() {
    fun testActualProductLoadersAndExecuteChain() {
        if (System.getProperty("test.pluginIsolation") != "true") return
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.trino-intellij-plugin"))!!
        val loader = plugin.pluginClassLoader!!
        assertEquals("com.intellij.ide.plugins.cl.PluginClassLoader", loader.javaClass.name)
        for (name in listOf("clickhouse", "doris", "duckdb", "sql-transpiler")) {
            val peer = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin"))
            assertNotNull("Required local sibling was not loaded: $name", peer)
            assertNotNull("Required local sibling has no loader: $name", peer!!.pluginClassLoader)
        }
        val fragment = loader.loadClass("dev.brikk.house.sql.shape.SqlFragment")
        for (name in listOf(fragment.name, "dev.brikk.house.sql.intellij.CooperativeExecuteAction", "dev.brikk.house.intellij.ColumnContract",
            "dev.brikk.house.sql.intellij.pipe.PipeDialect", "dev.brikk.house.sql.intellij.pipe.PipeExecuteInterceptor",
            "dev.brikk.house.sql.intellij.completion.SqlCompletionContributor", "dev.brikk.house.sql.metadata.DialectMetadata",
            "dev.brikk.house.sql.intellij.lexer.MaskingSqlLexer", "dev.brikk.house.sql.intellij.parser.LenientSqlParsingKt",
            "dev.brikk.house.sql.intellij.pipe.PipeStatementGate", "dev.brikk.house.sql.intellij.editor.MaskedSpanRecolorAnnotator",
            "dev.brikk.house.sql.intellij.editor.SubstrateErrorFilter", "dev.brikk.house.sql.intellij.editor.SqlDialectQuoteHandler",
            "dev.brikk.house.sql.intellij.editor.KeywordOverlayHighlighterFactory", "dev.brikk.house.sql.intellij.dialect.PgFamilyDialect",
            "dev.brikk.house.sql.intellij.dialect.SqlDialectRegistry",
            "dev.brikk.house.sql.intellij.catalog.PgFamilyModelFacade",
            "dev.brikk.house.sql.intellij.catalog.GenericJdbcIntrospectorGate",
            "dev.brikk.house.sql.intellij.catalog.DialectDataSourceResolver",
            "dev.brikk.house.sql.intellij.catalog.DataSourceResolution",
            "dev.brikk.house.sql.intellij.catalog.CatalogProfile", "dev.brikk.house.sql.intellij.catalog.CatalogScopes",
            "dev.brikk.house.sql.intellij.catalog.NamespacePathResolver", "dev.brikk.house.sql.intellij.catalog.NamespaceTarget",
            "dev.brikk.house.sql.intellij.catalog.AutoIntrospectEngine", "dev.brikk.house.sql.intellij.catalog.AutoIntrospectService",
            "dev.brikk.house.sql.intellij.catalog.IntrospectBannerProvider", "dev.brikk.house.sql.intellij.catalog.QualifiedPathIntrospectContributor",
            "dev.brikk.house.sql.intellij.catalog.TrinoFunctionHarvester", "dev.brikk.house.sql.intellij.catalog.TrinoLiveCatalogProfile",
            "dev.brikk.house.sql.intellij.catalog.LiveCatalogService", "dev.brikk.house.sql.intellij.catalog.HarvestConnectionInterceptor",
            "dev.brikk.house.sql.intellij.catalog.LiveCatalogCleanup", "dev.brikk.house.sql.intellij.catalog.LiveCatalogStore",
            "dev.brikk.house.sql.intellij.catalog.ExecutionObserver", "dev.brikk.house.sql.intellij.catalog.ExecutionObserverProfile",
            "dev.brikk.house.sql.intellij.catalog.ExecutionObserverStartup", "dev.brikk.house.sql.intellij.catalog.StatementHeadDetector",
            "dev.brikk.house.sql.intellij.diagnostics.NativeErrorAnnotator", "dev.brikk.house.sql.intellij.diagnostics.NativeErrorProfile",
            "dev.brikk.house.sql.intellij.catalog.ShowCreateDefinitionProvider", "dev.brikk.house.sql.intellij.catalog.TextExplainService",
            "dev.brikk.house.sql.intellij.host.HostSqlChannel", "dev.brikk.house.sql.intellij.host.HostSqlRunConsoleVeto",
            "dev.brikk.house.sql.intellij.relation.ExternalRelationResolveExtension",
            "dev.brikk.house.sql.intellij.relation.ExternalRelationCompletionContributor"))
            assertSame(name, loader, loader.loadClass(name).classLoader)
        assertEquals("dev.brikk.house.sql.intellij.lexer.MaskingSqlLexer",
            loader.loadClass("dev.sort.trino.sql.TrinoLexer").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.dialect.PgFamilyDialect",
            loader.loadClass("dev.sort.trino.sql.TrinoSqlDialect").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.catalog.PgFamilyModelFacade",
            loader.loadClass("dev.sort.trino.TrinoModelFacade").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.catalog.GenericJdbcIntrospectorGate",
            loader.loadClass("dev.sort.trino.TrinoIntrospectorGate").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.diagnostics.NativeErrorAnnotator",
            loader.loadClass("dev.sort.trino.validate.TrinoErrorAnnotator").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.catalog.ShowCreateDefinitionProvider",
            loader.loadClass("dev.sort.trino.TrinoDefinitionProvider").superclass.name)
        for ((consumer, shared) in mapOf(
            "TrinoAutoIntrospectService" to "AutoIntrospectService",
            "TrinoIntrospectBannerProvider" to "IntrospectBannerProvider",
            "TrinoQualifiedPathIntrospectContributor" to "QualifiedPathIntrospectContributor",
            "TrinoLiveCatalogService" to "LiveCatalogService",
            "TrinoHarvestConnectionInterceptor" to "HarvestConnectionInterceptor",
            "TrinoLiveCatalogCleanup" to "LiveCatalogCleanup",
            "TrinoExecutionObserver" to "ExecutionObserver",
            "TrinoExecutionObserverStartup" to "ExecutionObserverStartup",
        )) {
            val type = loader.loadClass("dev.sort.trino.catalog.$consumer")
            assertSame(loader, type.classLoader)
            assertSame(loader.loadClass("dev.brikk.house.sql.intellij.catalog.$shared"), type.superclass)
        }
        val serviceType = loader.loadClass("dev.sort.trino.catalog.TrinoAutoIntrospectService")
        val service = project.getService(serviceType)
        assertSame(loader, service.javaClass.classLoader)
        val profile = serviceType.getMethod("getProfile").invoke(service)
        assertSame(loader.loadClass("dev.sort.trino.catalog.TrinoCatalogProfile").getField("INSTANCE").get(null), profile)
        val observerType = loader.loadClass("dev.sort.trino.catalog.TrinoExecutionObserver")
        val observer = project.getService(observerType)
        assertSame(loader, observer.javaClass.classLoader)
        val observerProfile = observerType.getMethod("getProfile").invoke(observer)
        assertSame(loader.loadClass("dev.sort.trino.catalog.TrinoExecutionObserverProfile").getField("INSTANCE").get(null), observerProfile)
        assertSame(serviceType, observerProfile.javaClass.getMethod("getAutoIntrospect").invoke(observerProfile))
        assertNull(observerProfile.javaClass.getMethod("getLiveCatalog").invoke(observerProfile))
        val liveType = loader.loadClass("dev.sort.trino.catalog.TrinoLiveCatalogService")
        val live = com.intellij.openapi.application.ApplicationManager.getApplication().getService(liveType)
        assertSame(loader, live.javaClass.classLoader)
        val liveProfile = liveType.getMethod("getProfile").invoke(live)
        assertSame(loader.loadClass("dev.sort.trino.catalog.TrinoFunctionCatalogProfile").getField("INSTANCE").get(null), liveProfile)
        assertEquals(true, liveProfile.javaClass.getMethod("getEnabled").invoke(liveProfile))
        val registry = loader.loadClass("dev.brikk.house.sql.intellij.dialect.SqlDialectRegistry")
        val registryInstance = registry.getField("INSTANCE").get(null)
        val entry = registry.getMethod("forLanguageId", String::class.java).invoke(registryInstance, "TrinoSQL")!!
        assertSame(loader, entry.javaClass.classLoader)
        assertEquals("trino", entry.javaClass.getMethod("getCoreDialect").invoke(entry))
        assertSame(entry, registry.getMethod("forMarker", String::class.java).invoke(registryInstance, "TRINO"))
        val dbms = loader.loadClass("dev.sort.trino.TrinoDbms").getField("TRINO_BRIKK").get(null)
        assertSame(entry, registry.getMethod("forDbms", com.intellij.database.Dbms::class.java).invoke(registryInstance, dbms))
        assertNull(registry.getMethod("forLanguageId", String::class.java).invoke(registryInstance, "GenericSQL"))
        assertEquals("dev.brikk.house.sql.intellij.completion.SqlCompletionContributor",
            loader.loadClass("dev.sort.trino.sql.TrinoMetadataCompletionContributor").superclass.name)
        assertEquals("dev.brikk.house.sql.intellij.pipe.PipeStageCompletionContributor",
            loader.loadClass("dev.sort.trino.pipes.TrinoPipesCompletionContributor").superclass.name)
        val configuration = loader.loadClass("dev.sort.trino.pipes.TrinoPipesActionConfiguration").getConstructor().newInstance()
        assertEquals("dev.brikk.house.sql.intellij.pipe.PipeExecuteInterceptor",
            configuration.javaClass.getMethod("getInterceptor").invoke(configuration).javaClass.name)
        val value = fragment.getConstructor(String::class.java, String::class.java).newInstance("FROM t |> SELECT id |> LIMIT 1", "trino")
        val result = fragment.getMethod("toExecutable", String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(value, "trino", true, true)
        assertFalse((result.javaClass.getMethod("getSql").invoke(result) as String).contains("|>"))
        for (name in listOf("clickhouse", "doris", "duckdb", "sql-transpiler")) {
            val peer = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin"))!!
            assertNotSame(fragment, peer.pluginClassLoader!!.loadClass(fragment.name))
            val metadata = "dev.brikk.house.sql.metadata.DialectMetadata"
            assertNotSame(loader.loadClass(metadata), peer.pluginClassLoader!!.loadClass(metadata))
            if (name != "sql-transpiler") {
                val completion = "dev.brikk.house.sql.intellij.completion.SqlCompletionContributor"
                assertNotSame(loader.loadClass(completion), peer.pluginClassLoader!!.loadClass(completion))
                val catalogKit = listOf("catalog.CatalogProfile", "catalog.CatalogScopes", "catalog.NamespacePathResolver", "catalog.NamespaceTarget",
                    "catalog.AutoIntrospectEngine", "catalog.AutoIntrospectService", "catalog.IntrospectBannerProvider",
                    "catalog.QualifiedPathIntrospectContributor")
                val peerHasCatalogKit = peer.pluginClassLoader!!.getResource("dev/brikk/house/sql/intellij/catalog/CatalogProfile.class") != null
                val liveKit = listOf("catalog.LiveCatalogService", "catalog.LiveCatalogStore", "catalog.HarvestConnectionInterceptor",
                    "catalog.LiveCatalogCleanup", "catalog.TrinoLiveCatalogProfile", "catalog.TrinoFunctionHarvester",
                    "catalog.ExecutionObserver", "catalog.ExecutionObserverProfile", "catalog.ExecutionObserverStartup",
                    "catalog.StatementHeadDetector")
                val peerHasLiveKit = peer.pluginClassLoader!!.getResource("dev/brikk/house/sql/intellij/catalog/LiveCatalogService.class") != null
                for (shared in listOf("lexer.MaskingSqlLexer", "pipe.PipeStatementGate", "editor.MaskedSpanRecolorAnnotator",
                    "dialect.PgFamilyDialect", "dialect.SqlDialectRegistry", "catalog.PgFamilyModelFacade",
                    "catalog.GenericJdbcIntrospectorGate", "catalog.DialectDataSourceResolver", "catalog.DataSourceResolution") +
                    catalogKit.takeIf { peerHasCatalogKit }.orEmpty() + liveKit.takeIf { peerHasLiveKit }.orEmpty()) {
                    val type = "dev.brikk.house.sql.intellij.$shared"
                    val peerType = peer.pluginClassLoader!!.loadClass(type)
                    assertSame(peer.pluginClassLoader, peerType.classLoader)
                    assertNotSame(loader.loadClass(type), peerType)
                }
            }
        }
        for (id in listOf("Console.Jdbc.Execute", "Console.Jdbc.Execute.2", "Console.Jdbc.Execute.3", "Console.Jdbc.Execute.Selection")) {
            var action = ActionManager.getInstance().getAction(id)!!
            val types = mutableListOf<String>()
            val classes = mutableListOf<Class<*>>()
            val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
            while (visited.add(action)) {
                types.add(action.javaClass.name)
                classes.add(action.javaClass)
                action = (action as? ActionWithDelegate<*>)?.delegate as? com.intellij.openapi.actionSystem.AnAction ?: break
            }
            assertTrue(types.toString(), types.any { it == "dev.brikk.house.sql.intellij.CooperativeExecuteAction" })
            assertTrue(types.toString(), types.last().startsWith("com.intellij.database.actions.RunQueryAction"))
            for (peer in listOf("clickhouse", "doris", "duckdb")) {
                PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$peer-intellij-plugin"))?.let { plugin ->
                    val peerAction = plugin.pluginClassLoader!!.loadClass("dev.brikk.house.sql.intellij.CooperativeExecuteAction")
                    assertSame(plugin.pluginClassLoader, peerAction.classLoader)
                    assertTrue(types.toString(), classes.any { it === peerAction })
                }
            }
        }
    }

    fun testActualThreePluginRegistrationsInAllSixOrders() {
        if (System.getProperty("test.pluginIsolation") != "true") return
        val names = mapOf(
            "trino" to "dev.sort.trino.pipes.TrinoPipesActionConfiguration",
            "doris" to "dev.sort.doris.pipes.DorisPipesActionConfiguration",
            "duckdb" to "dev.sort.duckdb.pipes.DuckdbPipesActionConfiguration",
        )
        val loaders = names.keys.associateWith { name ->
            PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin"))?.pluginClassLoader
        }
        org.junit.Assume.assumeTrue("All three distributions are required", loaders.values.all { it != null })
        val ids = listOf("Console.Jdbc.Execute", "Console.Jdbc.Execute.2", "Console.Jdbc.Execute.3", "Console.Jdbc.Execute.Selection")
        val manager = ActionManager.getInstance()
        val originals = ids.associateWith { manager.getAction(it)!! }
        val orders = listOf(listOf("trino","doris","duckdb"), listOf("trino","duckdb","doris"),
            listOf("doris","trino","duckdb"), listOf("doris","duckdb","trino"),
            listOf("duckdb","trino","doris"), listOf("duckdb","doris","trino"))
        try {
            for (order in orders) {
                var delegated = 0
                val event = com.intellij.openapi.actionSystem.AnActionEvent.createFromDataContext("Peer chain", com.intellij.openapi.actionSystem.Presentation(),
                    com.intellij.openapi.actionSystem.impl.SimpleDataContext.getProjectContext(project))
                ids.forEach { id -> manager.replaceAction(id, object : com.intellij.openapi.actionSystem.AnAction() {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) { assertSame(event, e); delegated++ }
                }) }
                val configurations = order.map { name ->
                    loaders.getValue(name)!!.loadClass(names.getValue(name)).getConstructor().newInstance()
                        as com.intellij.openapi.actionSystem.impl.DynamicActionConfigurationCustomizer
                }
                try {
                    configurations.forEach { it.registerActions(manager) }
                    ids.forEach { id -> manager.getAction(id)!!.actionPerformed(event) }
                    assertEquals("Each variant delegates exactly once through $order", 4, delegated)
                    // Remove the innermost plugin first, exercising retained detached wrappers.
                    configurations.first().unregisterActions(manager)
                    ids.forEach { id -> manager.getAction(id)!!.actionPerformed(event) }
                    assertEquals("Detached predecessor chain remains usable: $order", 8, delegated)
                } finally { configurations.asReversed().forEach { it.unregisterActions(manager) } }
            }
        } finally { originals.forEach { (id, action) -> manager.replaceAction(id, action) } }
    }
}
