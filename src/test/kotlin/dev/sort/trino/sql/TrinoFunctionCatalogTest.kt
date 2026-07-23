package dev.sort.trino.sql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the bundled catalog resources (no IDE fixture needed — pure resource parsing): the harvested
 * inventories load, classify into the four Trino function kinds, and stay identifier-clean.
 */
class TrinoFunctionCatalogTest {

    @Test
    fun catalogLoadsRichInventory() {
        // Trino 483: 442 harvested rows / 441 names. The sanity floor is 300 (task spec).
        assertTrue("functions: ${TrinoFunctionCatalog.functions.size}", TrinoFunctionCatalog.functions.size > 300)
        // Reserved set only (83 for 483) — smaller than a full keyword vocabulary by design.
        assertTrue("keywords: ${TrinoFunctionCatalog.keywords.size}", TrinoFunctionCatalog.keywords.size > 50)
    }

    @Test
    fun allFourKindsAreRepresentedAndClassified() {
        val kindsByName = TrinoFunctionCatalog.functions.groupBy({ it.name }, { it.kind })
        // Single-kind exemplars from the live harvest.
        assertTrue("abs scalar", TrinoFunctionCatalog.Kind.SCALAR in kindsByName.getValue("abs"))
        assertTrue("approx_distinct aggregate", TrinoFunctionCatalog.Kind.AGGREGATE in kindsByName.getValue("approx_distinct"))
        assertTrue("row_number window", TrinoFunctionCatalog.Kind.WINDOW in kindsByName.getValue("row_number"))
        assertTrue("exclude_columns table", TrinoFunctionCatalog.Kind.TABLE in kindsByName.getValue("exclude_columns"))
        // Every kind actually occurs.
        val allKinds = TrinoFunctionCatalog.functions.mapTo(HashSet()) { it.kind }
        assertTrue(
            "all four kinds present: $allKinds",
            allKinds.containsAll(
                listOf(
                    TrinoFunctionCatalog.Kind.SCALAR,
                    TrinoFunctionCatalog.Kind.AGGREGATE,
                    TrinoFunctionCatalog.Kind.WINDOW,
                    TrinoFunctionCatalog.Kind.TABLE,
                ),
            ),
        )
    }

    @Test
    fun kindOfMapsShowFunctionsTypesCaseInsensitively() {
        assertEquals(TrinoFunctionCatalog.Kind.SCALAR, TrinoFunctionCatalog.kindOf("scalar"))
        assertEquals(TrinoFunctionCatalog.Kind.AGGREGATE, TrinoFunctionCatalog.kindOf("AGGREGATE"))
        assertEquals(TrinoFunctionCatalog.Kind.WINDOW, TrinoFunctionCatalog.kindOf(" window "))
        assertEquals(TrinoFunctionCatalog.Kind.TABLE, TrinoFunctionCatalog.kindOf("table"))
        assertEquals(TrinoFunctionCatalog.Kind.OTHER, TrinoFunctionCatalog.kindOf("something_new"))
    }

    @Test
    fun noOperatorNamesAndNoCommentLeakage() {
        assertTrue(
            "operator-named rows leaked into the completable list",
            TrinoFunctionCatalog.functions.none { !TrinoFunctionCatalog.isCompletableName(it.name) },
        )
        // `#` header lines must have been skipped, not parsed as a function/keyword.
        assertFalse(TrinoFunctionCatalog.functions.any { it.name.startsWith("#") })
        assertFalse(TrinoFunctionCatalog.keywords.any { it.startsWith("#") })
    }

    @Test
    fun returnTypesAreCaptured() {
        val abs = TrinoFunctionCatalog.functions.first { it.name == "abs" && it.kind == TrinoFunctionCatalog.Kind.SCALAR }
        assertTrue("abs return type present: '${abs.returnType}'", abs.returnType.isNotBlank())
    }

    @Test
    fun keywordsAreUppercaseReservedWords() {
        val kw = TrinoFunctionCatalog.keywords
        for (r in listOf("SELECT", "FROM", "WHERE", "GROUP", "UNNEST")) {
            assertTrue("reserved word $r present", r in kw)
        }
        assertTrue("keywords are uppercase", kw.all { it == it.uppercase() })
    }
}
