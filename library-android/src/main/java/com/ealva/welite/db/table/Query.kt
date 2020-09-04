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

/**
 * QuerySeed contains all the info to build a Query
 */
data class QuerySeed(
  /**
   * The list of columns selected in the query. Used when reading the query results.
   */
  val columns: List<SqlTypeExpression<*>>,
  /**
   * The full sql of the query
   */
  val sql: String,
  /**
   * The list of the types of arguments which need to be bound for each query execution. This is
   * each place a "?" appears in the [sql]. The [PersistentType] is responsible for accepting
   * an argument from the client, converting if necessary, and binding it into query args.
   */
  val types: List<PersistentType<*>>
)

fun QueryBuilder.toQuery(): Query = Query(build())

interface Query {
  val seed: QuerySeed

  companion object {
    var logQueryPlans: Boolean = false

    operator fun invoke(queryBuilder: QueryBuilder): Query = Query(queryBuilder.build())

    /**
     * Make a Query instance. eg.
     * ```
     * val query = Query(querySeed)
     * ```
     */
    internal operator fun invoke(
      querySeed: QuerySeed
    ): Query = QueryImpl(querySeed)
  }
}

private class QueryImpl(override val seed: QuerySeed) : Query
