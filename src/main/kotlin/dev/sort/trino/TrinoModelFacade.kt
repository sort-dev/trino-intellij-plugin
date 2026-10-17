package dev.sort.trino

import com.intellij.database.Dbms
import dev.brikk.house.sql.intellij.catalog.PgFamilyModelFacade

class TrinoModelFacade(dbms: Dbms) : PgFamilyModelFacade(dbms, databasesAsCatalogs = true)
