package dev.sort.trino.brikk

import com.intellij.database.model.*
import com.intellij.database.symbols.DasColumnSymbol
import com.intellij.database.symbols.DasPsiSymbol
import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.sql.psi.SqlReference
import com.intellij.sql.psi.SqlScopeProcessor
import com.intellij.sql.psi.impl.SqlResolveExtension
import com.intellij.util.containers.JBIterable
import dev.brikk.house.intellij.ColumnContract
import dev.brikk.house.intellij.ColumnNullability
import dev.sort.trino.TrinoDbms
import dev.sort.trino.sql.TrinoSqlDialect

class BrikkSqlResolveExtension : SqlResolveExtension {
    override fun process(reference: SqlReference, processor: SqlScopeProcessor): Boolean {
        val psi: PsiReference = reference
        val element = psi.element
        val file = element.containingFile ?: return true
        val ref = BrikkSqlEditorSupport.reference(element, element.textRange.startOffset + psi.rangeInElement.startOffset) ?: return true
        if (ref.kind != BrikkSqlScopes.Kind.COLUMN) return true
        for ((row, column) in ref.scope.candidates(ref.name, ref.qualifier)) {
            if (row.managed && !processor.execute(ContractColumnSymbol(file, row, column), ResolveState.initial())) return false
        }
        return true
    }
}

private class ContractColumnSymbol(private val file: PsiFile, row: BrikkSqlScopes.Relation, private val column: ColumnContract) :
    LightElement(file.manager, TrinoSqlDialect), DasPsiSymbol, DasColumnSymbol {
    private val identity = Triple(row.alias.lowercase(java.util.Locale.ROOT), row.slot, row.sourceStart)
    private val model = ContractColumn(row, column)
    override fun equals(other: Any?): Boolean = other is ContractColumnSymbol && file.originalFile === other.file.originalFile &&
        identity == other.identity && column.name == other.column.name && column.kotlinClassId == other.column.kotlinClassId &&
        column.nullability == other.column.nullability && column.sqlFamily == other.column.sqlFamily
    override fun hashCode() = listOf(System.identityHashCode(file.originalFile), identity, column.name, column.kotlinClassId, column.nullability, column.sqlFamily).hashCode()
    override fun getName() = column.name
    override fun setName(name: String): PsiElement = throw com.intellij.util.IncorrectOperationException("Rename the Kotlin property")
    override fun isQuoted() = false
    override fun getKind() = ObjectKind.COLUMN
    override fun getDbms() = TrinoDbms.TRINO_BRIKK
    override fun getDasObject(): DasColumn = model
    override fun getContainingFile() = file
    override fun getContextElement(): PsiElement = file
    override fun getNavigationElement(): PsiElement = BrikkSqlEditorSupport.navigation(file, column.navigation) ?: this
    override fun getText() = column.name
    override fun toString() = "Brikk relation column ${column.name}"
    private class ContractTable(private val row: BrikkSqlScopes.Relation) : DasTable {
        override fun getName() = row.alias.ifBlank { row.slot.orEmpty() }
        override fun getKind() = ObjectKind.TABLE
        override fun isQuoted() = false
        override fun isSystem() = false
        override fun isTemporary() = false
        override fun getColumnAttrs(column: DasColumn?): Set<DasColumn.Attribute> = emptySet()
        override fun getDasChildren(kind: ObjectKind?): JBIterable<out DasObject> =
            if (kind == null || kind == ObjectKind.COLUMN) JBIterable.from(row.columns.map { ContractColumn(row, it) }) else JBIterable.empty()
    }
    private class ContractColumn(private val row: BrikkSqlScopes.Relation, private val column: ColumnContract) : DasColumn {
        override fun getName() = column.name
        override fun getKind() = ObjectKind.COLUMN
        override fun isQuoted() = false
        override fun getTable(): DasTable = ContractTable(row)
        override fun getDasParent(): DasObject = table
        override fun getPosition(): Short = -1
        override fun isNotNull() = column.nullability == ColumnNullability.NON_NULL
        override fun getDefault(): String? = null
        override fun getDasType(): com.intellij.database.types.DasType = object : com.intellij.database.types.DasType {
            override fun getTypeClass() = com.intellij.database.types.DasSimpleTypeClass("UNKNOWN")
            override fun getDescription() = "Kotlin relation contract; native SQL type unknown"
            override fun toDataType() = DataType.UNKNOWN
        }
    }
}
