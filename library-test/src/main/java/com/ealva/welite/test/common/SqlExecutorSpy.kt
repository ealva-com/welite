/*
 * Copyright 2020 Eric A. Snell
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ealva.welite.test.common

import com.ealva.welite.db.table.SqlExecutor

class SqlExecutorSpy : SqlExecutor {
  val execSqlList = mutableListOf<String>()
  override fun exec(sql: String, vararg bindArgs: Any) { execSqlList += sql }
  override fun exec(sql: List<String>) { execSqlList += sql }
}