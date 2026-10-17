package dev.sort.trino.sql

import com.intellij.database.types.DasTableType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlFunctionCallExpression
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.relation.ExternalRelationFacts
import dev.brikk.house.sql.intellij.relation.RelationFactsResult
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import dev.brikk.house.sql.scope.ColumnFact
import dev.brikk.house.sql.scope.RelationFact
import dev.brikk.house.sql.scope.RelationFacts
import dev.sort.trino.brikk.TrinoRelationProfile

class TrinoTableFunctionTypingTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        setSqlDialectMapping(project, null, TrinoSqlDialect, testRootDisposable)
    }

    private fun call(sql: String, name: String): SqlFunctionCallExpression {
        myFixture.configureByText("table-function.sql", sql)
        return PsiTreeUtil.findChildrenOfType(myFixture.file, SqlFunctionCallExpression::class.java)
            .single { it.nameElement?.name.equals(name, ignoreCase = true) }
    }

    fun testUnknownTableFunctionHasOpenNativeColumns() {
        val type = call("SELECT * FROM demo_rows(7) AS r", "demo_rows").dasType as DasTableType
        assertEquals(0, type.columnCount)
    }

    fun testCallFactsGiveTheNativeTableTypeItsColumns() {
        TrinoRelationProfile.externalFacts.point.registerExtension(ExternalRelationFacts.of {
            RelationFactsResult.Available(RelationFacts(calls = mapOf("demo_rows" to RelationFact(listOf(
                ColumnFact("demo_key", sqlType = "BIGINT"), ColumnFact("demo_label", sqlType = "VARCHAR"),
            )))), 1)
        }, testRootDisposable)
        val type = call("SELECT * FROM demo_rows(7) AS r", "demo_rows").dasType as DasTableType
        assertEquals(listOf("demo_key", "demo_label"), (0 until type.columnCount).map(type::getColumnName))
        assertTrue((0 until type.columnCount).all { type.getColumnDasType(it).specification.isNotBlank() })
        assertTrue((0 until type.columnCount).none { '?' in type.getColumnDasType(it).specification })
    }
}
