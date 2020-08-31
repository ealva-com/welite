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

package com.ealva.welite.db.table

import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.PersistentType

data class QuerySeed(
  val fields: List<SqlTypeExpression<*>>,
  val sql: String,
  val types: List<PersistentType<*>>
)

interface Query {
  val seed: QuerySeed

  companion object {
    var logQueryPlans: Boolean = false

    /**
     * Make a Query instance. eg.
     * ```
     * val query = Query(db, fields, sql, bindables)
     * ```
     */
    internal operator fun invoke(
      querySeed: QuerySeed
    ): Query = QueryImpl(querySeed)
  }
}

private class QueryImpl(override val seed: QuerySeed) : Query
