package dev.sort.trino.catalog

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.application.ApplicationInfo
import dev.brikk.house.sql.intellij.testing.assertFallbackExtensionsAudited
import dev.sort.trino.TrinoDbms

class TrinoFallbackExtensionAuditTest : BasePlatformTestCase() {
    fun testInheritedExtensionsAreAudited() {
        val allowed = mutableMapOf(
            "com.intellij.database.dataImporter" to "Retain existing import behavior; PostgreSQL target-type defaults need a separate Trino review.",
            "com.intellij.database.databaseViewStructureExtension" to "Retain existing tree structure; PostgreSQL-only node grouping needs a separate Trino review.",
            "com.intellij.database.definitionProvider" to "Retain existing definition lookup; PostgreSQL definition assumptions need a separate Trino review.",
            "com.intellij.database.dmlHelper" to "Retain existing grid DML behavior; PostgreSQL editable-object policies need a separate Trino review.",
            "com.intellij.database.domainRegistry" to "Retain the PostgreSQL model's domain registry; Trino type domains need a separate review.",
            "com.intellij.database.executionEnvironmentHelper" to "Retain existing session handling; PostgreSQL-specific environment operations need a separate Trino review.",
            "com.intellij.database.geoHelper" to "Retain existing geometry presentation; PostgreSQL geometry encodings need a separate Trino review.",
            "com.intellij.database.gridColumnsManagerFactory" to "The UNKNOWN column manager provides generic result-grid column handling.",
            "com.intellij.database.gridHelper" to "The UNKNOWN grid helper provides generic result-grid editing and presentation.",
            "com.intellij.database.hookUpHelper" to "The UNKNOWN hookup helper provides generic editor-to-data-source association.",
            "com.intellij.database.jdbcMetadataWrapper" to "Retain existing JDBC metadata handling; PostgreSQL metadata transformations need a separate Trino review.",
            "com.intellij.database.jdbcSourceLoader" to "Retain existing source loading; PostgreSQL definition queries need a separate Trino review.",
            "com.intellij.database.namingService" to "PostgreSQL double-quoted identifier naming matches Trino's identifier delimiter.",
            "com.intellij.database.objectEditorFactory" to "Retain existing object editors; PostgreSQL-only schema options need a separate Trino review.",
            "com.intellij.database.objectEditorModelFactory" to "Retain existing editor models; PostgreSQL-only schema properties need a separate Trino review.",
            "com.intellij.database.predicatesHelper" to "Retain existing predicate generation; PostgreSQL-specific predicates need a separate Trino review.",
            "com.intellij.database.routineExecutionHelper" to "PostgreSQL registers the generic routine request builder rather than a PostgreSQL-specific executor.",
            "com.intellij.database.scriptGenerator" to "Retain existing DDL generation; PostgreSQL syntax needs a separate Trino review.",
            "com.intellij.database.sqlEffectAnalyzer" to "Retain existing model-refresh analysis; PostgreSQL statement effects need a separate Trino review.",
            "com.intellij.database.sqlObjectBuilder" to "Retain the object builder paired with the PostgreSQL PSI grammar used by Trino.",
            "com.intellij.database.typeSystem" to "Retain the PostgreSQL model's type system; Trino type semantics need a separate review.",
            "com.intellij.sql.navigationHelper" to "The UNKNOWN navigation helper supplies generic SQL PSI navigation.",
        )
        if (ApplicationInfo.getInstance().build.baselineVersion >= 263) {
            allowed["com.intellij.database.transactionControlLanguageHandler"] =
                "PostgreSQL registers the base transaction-control handler for standard SQL transaction statements."
        }
        assertFallbackExtensionsAudited(TrinoDbms.TRINO_BRIKK, allowed)
    }
}
