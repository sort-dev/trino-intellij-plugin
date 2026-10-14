package dev.sort.trino.sql;

import com.intellij.database.types.DasType;
import com.intellij.psi.tree.IElementType;
import com.intellij.sql.psi.SqlTableType;
import com.intellij.sql.psi.impl.SqlDasTypeAwareDeepRecursiveElement;
import com.intellij.sql.psi.impl.SqlFunctionCallExpressionImpl;
import dev.brikk.house.sql.intellij.relation.RelationTableTypes;
import dev.sort.trino.brikk.BrikkTrinoProfile;
import dev.sort.trino.brikk.TrinoRelationProfile;
import kotlin.DeepRecursiveScope;
import kotlin.coroutines.Continuation;

/** Java preserves the stock suspend implementation's scalar fallback without a second type pass. */
public final class TrinoTableFunctionCall extends SqlFunctionCallExpressionImpl {
    public TrinoTableFunctionCall(IElementType type) {
        super(type);
    }

    @Override
    public Object computeDasType(DeepRecursiveScope<SqlDasTypeAwareDeepRecursiveElement, DasType> scope,
                                 Continuation<? super DasType> continuation) {
        var host = BrikkTrinoProfile.Companion.forFile(getContainingFile());
        var name = getNameElement();
        // Slot calls keep the shared resolve/rename path, without native slot typing.
        if (host == null || name == null || !host.getSlots().contains(name.getName())) {
            if (RelationTableTypes.INSTANCE.of(this, TrinoRelationProfile.INSTANCE) == null) {
                SqlTableType type = RelationTableTypes.INSTANCE.ofTableFunction(this, TrinoRelationProfile.INSTANCE);
                if (type != null) return type;
            }
        }
        return super.computeDasType(scope, continuation);
    }
}
