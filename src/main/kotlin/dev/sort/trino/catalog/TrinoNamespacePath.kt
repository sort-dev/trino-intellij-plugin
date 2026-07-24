package dev.sort.trino.catalog

/**
 * The completion-time targeting decision, factored OFF the platform model so it is unit-testable
 * with fakes: given the introspected model roots (catalogs) and the dotted path the user is typing
 * (`hive` for `hive.<caret>`, `hive.web` for `hive.web.<caret>`), resolve the addressed model node
 * and decide whether it is the "enumerated-but-childless" namespace that should be deepened.
 *
 * A namespace is *enumerated-but-childless* when it exists in the model (so the name is real —
 * typo-proof by construction) but has no children loaded yet: a catalog whose schemas were never
 * introspected, or a schema whose tables were never introspected. That is exactly the moment to
 * kick a targeted introspection ([TrinoAutoIntrospect]); a name that simply does not resolve is
 * mid-typing and must NOT trigger anything.
 *
 * Paths are treated as FULLY QUALIFIED (`catalog` / `catalog.schema`) — the Trino convention, and
 * the only reliable one here since a Trino connection reports no current catalog
 * (`getCatalog() == null`, truth battery). The model is abstracted as [Node] (name + children) so
 * the walk can be exercised without a live DataGrip model; the completion contributor adapts
 * `DasObject` to it.
 */
object TrinoNamespacePath {

    /** A model namespace: its name and its currently-loaded children. */
    interface Node {
        val name: String
        fun childNodes(): List<Node>
    }

    /**
     * Resolve the node addressed by the fully-qualified [parts] against the catalog [roots] (head
     * segment = a catalog / model root). Returns null when any segment fails to resolve
     * (mid-typing / typo): callers must treat null as "do nothing".
     */
    fun resolve(roots: List<Node>, parts: List<String>): Node? {
        if (parts.isEmpty()) return null
        var node: Node? = roots.firstOrNull { it.name.equals(parts.first(), ignoreCase = true) }
        for (segment in parts.drop(1)) {
            node = node?.childNodes()?.firstOrNull { it.name.equals(segment, ignoreCase = true) }
        }
        return node
    }

    /** True when [node] is enumerated but has no children loaded — the deepen trigger. */
    fun isChildless(node: Node): Boolean = node.childNodes().isEmpty()

    /**
     * The introspection target for a resolved-and-childless namespace, or null when nothing should
     * be deepened (path doesn't resolve, or the node already has children, or the path is deeper
     * than a schema).
     *
     * [parts] length decides the level: 1 segment addresses a CATALOG (deepen → schemas),
     * 2 segments a SCHEMA (deepen → tables). Deeper paths address a table (columns come with the
     * table once its schema is deepened) and are left to the platform.
     */
    fun decideDeepen(roots: List<Node>, parts: List<String>): Deepen? {
        if (parts.isEmpty() || parts.size > 2) return null
        val node = resolve(roots, parts) ?: return null
        if (!isChildless(node)) return null
        return when (parts.size) {
            1 -> Deepen(Level.CATALOG, catalog = parts[0], schema = null, node = node)
            else -> Deepen(Level.SCHEMA, catalog = parts[0], schema = parts[1], node = node)
        }
    }

    enum class Level { CATALOG, SCHEMA }

    /** A resolved deepening target: which level, the catalog/schema names, and the model node. */
    data class Deepen(val level: Level, val catalog: String, val schema: String?, val node: Node)
}
