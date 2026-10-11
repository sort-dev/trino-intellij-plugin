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
        val fragment = loader.loadClass("dev.brikk.house.sql.shape.SqlFragment")
        for (name in listOf(fragment.name, "dev.brikk.house.sql.intellij.CooperativeExecuteAction", "dev.brikk.house.intellij.ColumnContract"))
            assertSame(name, loader, loader.loadClass(name).classLoader)
        val value = fragment.getConstructor(String::class.java, String::class.java).newInstance("FROM t |> SELECT id |> LIMIT 1", "trino")
        val result = fragment.getMethod("toExecutable", String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(value, "trino", true, true)
        assertFalse((result.javaClass.getMethod("getSql").invoke(result) as String).contains("|>"))
        for (name in listOf("doris", "duckdb", "sql-transpiler")) {
            val peer = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin")) ?: continue
            assertNotSame(fragment, peer.pluginClassLoader!!.loadClass(fragment.name))
        }
        for (id in listOf("Console.Jdbc.Execute", "Console.Jdbc.Execute.2", "Console.Jdbc.Execute.3", "Console.Jdbc.Execute.Selection")) {
            var action = ActionManager.getInstance().getAction(id)!!
            val types = mutableListOf<String>()
            val visited = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Any, Boolean>())
            while (visited.add(action)) {
                types.add(action.javaClass.name)
                action = (action as? ActionWithDelegate<*>)?.delegate as? com.intellij.openapi.actionSystem.AnAction ?: break
            }
            assertTrue(types.toString(), types.any { it == "dev.brikk.house.sql.intellij.CooperativeExecuteAction" })
            assertTrue(types.toString(), types.last().startsWith("com.intellij.database.actions.RunQueryAction"))
            for (peer in listOf("doris", "duckdb")) {
                if (PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$peer-intellij-plugin")) != null)
                    assertTrue(types.toString(), types.any { it.startsWith("dev.sort.$peer.pipes.") })
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
