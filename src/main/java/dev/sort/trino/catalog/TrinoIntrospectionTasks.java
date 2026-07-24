package dev.sort.trino.catalog;

import com.intellij.database.introspection.IntrospectionTask;
import com.intellij.database.introspection.IntrospectionTasks;
import com.intellij.database.model.basic.BasicElement;

/**
 * Java shim for {@link IntrospectionTasks} — the factory is Kotlin-{@code internal} in metadata
 * (public in bytecode), so Kotlin sources cannot reference it while Java can (same technique as
 * {@link dev.sort.trino.PgModelAccess} and the sibling doris plugin's {@code PipeIntrospectionTasks}).
 *
 * <p>{@link TrinoAutoIntrospect} uses this to build the TARGETED one-element refresh — the round-18
 * doris lesson: a general/scope task re-introspects the ENTIRE widened scope and hangs on slow
 * externals; a Trino {@code hive}/{@code iceberg} catalog can hold thousands of schemas/tables, so
 * we must refresh EXACTLY the one node being deepened (its direct children only), never the scope.
 */
public final class TrinoIntrospectionTasks {
    private TrinoIntrospectionTasks() {}

    /** One-element (one-level) refresh of exactly {@code element} — never a scope-wide sync. */
    public static IntrospectionTask oneElementRefresh(String dataSourceId, BasicElement element) {
        return IntrospectionTasks.prepareOneElementRefreshTask(dataSourceId, element);
    }
}
