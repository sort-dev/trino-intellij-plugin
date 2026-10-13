package dev.sort.trino.brikk.kotlin

import dev.brikk.house.intellij.*
import dev.brikk.house.sql.intellij.completion.SqlLookupKind
import dev.brikk.house.sql.intellij.relation.*

class TrinoBrikkRelationFacts : ExternalRelationFacts by adapter()

private fun adapter(): ExternalRelationFacts {
    val brikk = BrikkRelationFacts(BrikkDialect.TRINO)
    val labels = BrikkRelationFacts.LABELS
    val kind = if (labels.brikkColumnStyle) SqlLookupKind.BRIKK_COLUMN else SqlLookupKind.RELATION_COLUMN
    return ExternalRelationFacts.of(RelationColumnLabels(declared = labels.declared, declaredKind = kind,
        contract = labels.contract, contractKind = kind), navigationTarget = brikk::navigationTarget) { file ->
        when (val result = brikk.facts(file)) {
            is BrikkRelationFactsResult.Available -> RelationFactsResult.Available(result.facts, result.revision)
            is BrikkRelationFactsResult.Pending -> RelationFactsResult.Pending(result.reason)
            null -> null
        }
    }
}
