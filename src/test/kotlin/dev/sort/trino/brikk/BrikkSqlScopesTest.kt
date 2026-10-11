package dev.sort.trino.brikk

import dev.brikk.house.intellij.*
import org.junit.Test
import kotlin.test.*

class BrikkSqlScopesTest {
    private fun shape(vararg names: String, full: Boolean = true) = ShapeResult.Available(RelationShape(
        if (full) ShapeKnowledge.FULL else ShapeKnowledge.PARTIAL,
        names.map { ColumnContract(it, "kotlin.Long", ColumnNullability.NON_NULL, SqlFamily.WIDE_INTEGER) }, ShapeOrigin.DECLARED))
    @Test fun joinAmbiguityAndOuterNullability() {
        val a = assertNotNull(BrikkSqlScopes.analyze("SELECT a.id, b.code FROM lefts() a LEFT JOIN rights() b ON a.id=b.id",
            mapOf("lefts" to shape("id"), "rights" to shape("id", "code"))))
        val ref = a.references.first { it.name == "code" }
        assertEquals(2, ref.scope.candidates("id").size)
        assertEquals(ColumnNullability.NULLABLE, ref.scope.candidates("code", "b").single().second.nullability)
    }
    @Test fun explicitProjectionHidesInputAliases() {
        val a = assertNotNull(BrikkSqlScopes.analyze("FROM src() AS r |> SELECT id AS chosen |> WHERE chosen > 1",
            mapOf("src" to shape("id", "hidden"))))
        val ref = a.references.last { it.name == "chosen" }
        assertNull(ref.scope.relation("r"))
        assertEquals(listOf("chosen"), ref.scope.columns.map { it.second.name })
    }
    @Test fun partialAndUnavailableStayDifferent() {
        for (contract in listOf(shape("id", full = false), ShapeResult.Unavailable("cold"))) {
            val a = assertNotNull(BrikkSqlScopes.analyze("SELECT extra FROM src()", mapOf("src" to contract)))
            val ref = a.references.first { it.name == "extra" }
            assertFalse(ref.scope.complete)
            assertTrue(ref.scope.candidates("extra").isEmpty())
            assertEquals(contract is ShapeResult.Available, ref.scope.relations.single().available)
        }
    }
    @Test fun extendComputedColumnsAreAvailableWithoutAnAvailableInput() {
        for (contract in listOf(shape("id"), ShapeResult.Unavailable("cold"))) {
            val analysis = assertNotNull(BrikkSqlScopes.analyze(
                "FROM src() |> EXTEND id + 1 AS computed, id AS copied |> WHERE computed > copied", mapOf("src" to contract)))
            val scope = analysis.references.last { it.name == "computed" }.scope
            assertTrue(scope.candidates("computed").single().first.available)
            assertEquals(contract is ShapeResult.Available, scope.candidates("copied").single().first.available)
        }
    }
    @Test fun extendPassthroughAvailabilityUnwrapsAliasesAndParentheses() {
        for (contract in listOf(shape("id"), ShapeResult.Unavailable("cold"))) {
            for (expression in listOf("id", "(id)", "((src.id))")) {
                val analysis = assertNotNull(BrikkSqlScopes.analyze(
                    "FROM src() |> EXTEND $expression AS copied |> WHERE copied > 0", mapOf("src" to contract)))
                val candidate = analysis.references.last { it.name == "copied" }.scope.candidates("copied").single()
                assertEquals(contract is ShapeResult.Available, candidate.first.available)
                if (contract is ShapeResult.Available) {
                    assertEquals(SqlFamily.WIDE_INTEGER, candidate.second.sqlFamily)
                    assertEquals(ColumnNullability.NON_NULL, candidate.second.nullability)
                }
            }
        }
    }
    @Test fun extendMissingPassthroughColumnIsUnavailable() {
        val analysis = assertNotNull(BrikkSqlScopes.analyze(
            "FROM src() |> EXTEND missing AS copied |> WHERE copied > 0", mapOf("src" to shape("id"))))
        assertFalse(analysis.references.last { it.name == "copied" }.scope.candidates("copied").single().first.available)
    }
    @Test fun parenthesizedSelectPreservesColumnNameAndType() {
        val analysis = assertNotNull(BrikkSqlScopes.analyze(
            "SELECT chosen.id FROM (SELECT (id) FROM src()) AS chosen", mapOf("src" to shape("id"))))
        val scope = analysis.references.single { it.name == "id" && it.qualifier == "chosen" }.scope
        val candidate = scope.candidates("id", "chosen").single()
        assertEquals("id", candidate.second.name)
        assertEquals("kotlin.Long", candidate.second.kotlinClassId)
        assertEquals(SqlFamily.WIDE_INTEGER, candidate.second.sqlFamily)
        assertEquals(ColumnNullability.NON_NULL, candidate.second.nullability)
    }
}
