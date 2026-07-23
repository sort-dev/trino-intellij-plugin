# Third-party notices

## Trino (parser + JDBC driver)

This plugin bundles **io.trino:trino-parser** (and depends at test/tool time on
**io.trino:trino-jdbc**), Copyright the Trino contributors, licensed under the
Apache License, Version 2.0 — <https://www.apache.org/licenses/LICENSE-2.0>.
"Trino" is a trademark of the Trino Software Foundation. This plugin is an independent
community project by Sortdev SRL, not affiliated with or endorsed by the Trino Software
Foundation; the name is used only to identify the query engine this plugin supports.

## StarRocks Support (parsing technique lineage)

Portions of the statement-boundary parsing approach (lenient consume-to-`;` dispatch with
bounded look-ahead) are adapted from **StarRocks Support**
(<https://github.com/ycyz97/starrocks-datagrip-plugin>), Copyright the StarRocks Support
contributors, licensed under the Apache License, Version 2.0, by way of our own
doris-intellij-plugin and duckdb-intellij-plugin (<https://github.com/sort-dev>).
Files carrying adapted portions state so in their header.

## JetBrains IntelliJ Platform

Built against the IntelliJ Platform SDK and the Database Tools and SQL plugin,
Copyright JetBrains s.r.o., under the JetBrains plugin development terms.
