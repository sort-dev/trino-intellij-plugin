# Third-party notices

## Embedded Brikk libraries

The plugin bundles `dev.brikk.house:brikk-sql-jvm:0.20.0-SNAPSHOT` and matching
`brikk-sql-metadata-jvm:0.20.0-SNAPSHOT`, `brikk-sql-verify-api:0.20.0-SNAPSHOT`,
`brikk-sql-verify-trino:0.20.0-SNAPSHOT`, `brikk-sql-intellij-support:0.2.0-SNAPSHOT`, and
`brikk-engine-kotlin-intellij-support:0.2.0`, Copyright Brikk / Sortdev SRL,
under the Apache License 2.0. Exact versions are declared in build.gradle.kts
and checked in the final distribution. The IntelliJ helpers include their notices.
The SQL engine includes work derived from SQLGlot, Copyright Toby Mao and the
SQLGlot contributors, under the MIT License reproduced below.

The cooperative console handling and Brikk SQL editing adapters are adapted from
Sortdev's Apache-2.0 Doris and DuckDB IntelliJ plugins and modified for shared
library use and Trino. Kotlin IDE and IntelliJ SDK classes are provided by the IDE,
not bundled in these helper libraries.

### SQLGlot, polyglot and DuckDB-derived portions

The parser, tokenizer, AST, optimizer and generators include a Kotlin port of
SQLGlot, https://github.com/tobymao/sqlglot. The DataFusion dialect references
polyglot, https://github.com/tobilg/polyglot.

Copyright (c) 2026 Toby Mao
Copyright (c) 2026 TobiLG <github@tobilg.com>
Copyright 2018-2025 Stichting DuckDB Foundation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

Generated function catalogs contain facts from DuckDB, Doris, StarRocks, Trino
and ClickHouse. Apache-2.0 source references: https://github.com/apache/doris,
https://github.com/StarRocks/starrocks, https://github.com/trinodb/trino and
https://github.com/ClickHouse/ClickHouse. Relevant upstream notices:

StarRocks: Copyright 2021-present, StarRocks Inc.

Apache Doris (incubating): Copyright 2018-2021 The Apache Software Foundation.
This product includes software developed at The Apache Software Foundation
(http://www.apache.org/).

Copyright 2016-2026 ClickHouse, Inc.

## Trino (parser + JDBC driver)

This plugin bundles **io.trino:trino-parser** (and depends at test/tool time on
**io.trino:trino-jdbc**), Copyright the Trino contributors, licensed under the
Apache License, Version 2.0 — <https://www.apache.org/licenses/LICENSE-2.0>.
"Trino" is a trademark of the Trino Software Foundation. This plugin is an independent
community project by Sortdev SRL, not affiliated with or endorsed by the Trino Software
Foundation; the name is used only to identify the query engine this plugin supports.

## ANTLR runtime

The plugin bundles `org.antlr:antlr4-runtime:4.13.2`, under the BSD-3-Clause license:

Copyright (c) 2012-2022 The ANTLR Project. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1. Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

3. Neither name of copyright holders nor the names of its contributors
may be used to endorse or promote products derived from this software
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

## Guava

The plugin bundles `com.google.guava:guava:33.5.0-jre` and its `failureaccess`
and empty `listenablefuture` compatibility artifacts, Copyright Google LLC,
under the Apache License 2.0, included in `META-INF/LICENSE`.

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
