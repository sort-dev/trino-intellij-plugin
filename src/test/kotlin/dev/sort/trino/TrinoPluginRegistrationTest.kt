package dev.sort.trino

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.options.ConfigurableEP
import com.intellij.openapi.util.JDOMUtil
import com.intellij.serviceContainer.BaseKeyedLazyInstance
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.KeyedLazyInstance
import org.jdom.Element
import java.nio.file.Files
import java.util.zip.ZipFile

/** Real registered adapters and lazy beans, not a list of constructors that bypasses XML binding. */
class TrinoPluginRegistrationTest : BasePlatformTestCase() {
    fun testOwnDescriptorExtensionsAndServicesInstantiate() {
        val id = PluginId.getId("dev.sort.trino-intellij-plugin")
        val plugin = requireNotNull(PluginManagerCore.getPlugin(id))
        val loader = requireNotNull(plugin.pluginClassLoader)
        val jars = Files.walk(plugin.pluginPath).use { paths ->
            paths.filter { it.toString().endsWith(".jar") }.toList()
        }
        val documents = mutableMapOf<String, Element>()
        val classes = mutableSetOf<String>()
        for (jar in jars) ZipFile(jar.toFile()).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.name.startsWith("META-INF/") && entry.name.endsWith(".xml")) {
                    val text = zip.getInputStream(entry).use { it.readBytes() }
                    val root = JDOMUtil.load(text)
                    if (root.name == "idea-plugin") documents[entry.name] = root
                }
                if (entry.name.startsWith("dev/sort/trino/") && entry.name.endsWith(".class"))
                    classes += entry.name.removeSuffix(".class").replace('/', '.')
            }
        }
        val root = requireNotNull(documents["META-INF/plugin.xml"]) { "Own installed plugin.xml missing" }
        assertEquals(id.idString, root.getChildTextTrim("id"))
        val descriptors = mutableListOf(root)
        root.children.filter { it.name == "include" }.forEach {
            descriptors += requireNotNull(documents[it.getAttributeValue("href").removePrefix("/")])
        }
        root.getChildren("depends").forEach {
            val config = it.getAttributeValue("config-file")
            if (config != null && PluginManagerCore.getPlugin(PluginId.getId(it.textTrim))?.isEnabled == true)
                descriptors += requireNotNull(documents["META-INF/$config"])
        }
        var extensions = 0
        for (descriptor in descriptors) for (group in descriptor.getChildren("extensions")) {
            val namespace = group.getAttributeValue("defaultExtensionNs")
            for ((name, registrations) in group.children.groupBy { "$namespace.${it.name}" }) {
                val app = ApplicationManager.getApplication()
                val area = if (app.extensionArea.hasExtensionPoint(name)) app.extensionArea else project.extensionArea
                val point = area.getExtensionPoint<Any>(name) as ExtensionPointImpl<Any>
                val adapters = point.sortedAdapters.filter { it.pluginDescriptor.pluginId == id }
                assertTrue("Missing adapters for $name", adapters.size >= registrations.size)
                val instances = adapters.map { adapter ->
                    val bean = requireNotNull(adapter.createInstance<Any>(point.componentManager))
                    val instance = when (bean) {
                        is ConfigurableEP<*> -> requireNotNull(bean.createConfigurable())
                        is BaseKeyedLazyInstance<*> -> requireNotNull(bean.instance)
                        is KeyedLazyInstance<*> -> requireNotNull(bean.instance)
                        else -> bean
                    }
                    println("REGISTERED $name: ${bean.javaClass.name} -> ${instance.javaClass.name}")
                    if (instance is com.intellij.openapi.options.UnnamedConfigurable) instance.disposeUIResources()
                    instance
                }
                for (registration in registrations) {
                    val expected = listOf("implementation", "implementationClass", "instance", "serviceImplementation")
                        .firstNotNullOfOrNull { registration.getAttributeValue(it) }
                        ?: registration.getChildTextTrim("className")
                    if (expected != null && !expected.contains(".TRINO_BRIKK"))
                        assertTrue("$name did not instantiate $expected", instances.any { it.javaClass.name == expected })
                    extensions++
                }
            }
        }
        var services = 0
        for (name in classes) {
            val type = loader.loadClass(name)
            val service = type.getAnnotation(Service::class.java) ?: continue
            for (level in service.value) {
                val manager = if (level == Service.Level.APP) ApplicationManager.getApplication() else project
                assertNotNull("Service $name", manager.getService(type))
                println("SERVICE $level $name")
                services++
            }
        }
        assertTrue("No own services discovered", services > 0)
        println("Own plugin.xml: $extensions extension registrations and $services light services instantiated")
    }
}
