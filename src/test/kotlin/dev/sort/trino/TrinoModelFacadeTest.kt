package dev.sort.trino

import com.intellij.database.dialects.postgres.model.PgMetaModel
import com.intellij.database.dialects.postgres.model.PgRoot
import com.intellij.database.model.ModelFacade
import com.intellij.database.model.ModelFactory
import com.intellij.database.model.ObjectKind
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TrinoModelFacadeTest : BasePlatformTestCase() {
    fun testRegisteredFacadeKeepsThePostgresModelFamily() {
        val facade = ModelFacade.forDbms(TrinoDbms.TRINO_BRIKK)
        assertTrue(facade is TrinoModelFacade)
        assertSame(PgMetaModel.MODEL, facade.metaModel)
        assertTrue(ModelFactory.BLACK_HOLE.createModel(TrinoDbms.TRINO_BRIKK).root is PgRoot)
    }

    fun testDatabaseLevelIsNamedCatalog() {
        val helper = ModelFacade.forDbms(TrinoDbms.TRINO_BRIKK).modelHelper
        assertEquals("catalog", helper.getCustomName(ObjectKind.DATABASE, false))
        assertEquals("catalogs", helper.getCustomName(ObjectKind.DATABASE, true))
    }
}
