package dev.sort.trino;

import com.intellij.database.dialects.postgres.model.PgModelHelper;
import com.intellij.database.model.ModelHelper;

/**
 * Java shim: {@code PgModelHelper} is a Kotlin object that is public in bytecode but internal in
 * Kotlin metadata, so Kotlin sources cannot reference it — Java ignores Kotlin visibility
 * metadata (same technique as doris-intellij's PipeIntrospectionTasks shim).
 */
public final class PgModelAccess {
    private PgModelAccess() {}

    public static ModelHelper pgModelHelper() {
        return PgModelHelper.INSTANCE;
    }
}
