/*
 * Copyright 2020 eAlva.com
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

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.StatementSeed

/**
 * QuerySeed contains all the info to build a Query
 */
public interface QuerySeed {
  /**
   * The list of the types of arguments which need to be bound for each query execution. This is
   * each place a "?" appears in the [sql]. The [PersistentType] is responsible for accepting
   * an argument from the client, converting if necessary, and binding it into query args.
   */
  public val types: List<PersistentType<*>>

  public val expressionToIndexMap: ExpressionToIndexMap

  /**
   * The full sql of the query
   */
  public val sql: String

  /**
   * The list of columns selected in the query. Used when reading the query results.
   */
  public val columns: List<Expression<*>>

  /**
   * Make a copy but update the sql
   */
  public fun copy(sql: String): QuerySeed

  public companion object {
    /**
     * Create a QuerySeed from the [StatementSeed] [seed] and the [columns] to be read from the
     * query result
     */
    public operator fun invoke(seed: StatementSeed, columns: List<Expression<*>>): QuerySeed {
      class QuerySeedImpl(
        private val statementSeed: StatementSeed,
        override val columns: List<Expression<*>>
      ) : QuerySeed {

        override fun copy(sql: String): QuerySeed {
          return QuerySeed(statementSeed.copy(sql = sql), columns)
        }

        override val types: List<PersistentType<*>>
          get() = seed.types
        override val expressionToIndexMap: ExpressionToIndexMap
          get() = seed.expressionToIndexMap
        override val sql: String
          get() = seed.sql
      }
      return QuerySeedImpl(seed, columns)
    }
  }
}

public fun <C : ColumnSet> QueryBuilder<C>.toQuery(): Query = Query(build())

public interface Query {
  public val seed: QuerySeed

  public companion object {
    /**
     * Create a reusable [Query] from [queryBuilder]
     */
    public operator fun <C : ColumnSet> invoke(queryBuilder: QueryBuilder<C>): Query =
      queryBuilder.toQuery()

    /**
     * Make a Query instance. eg.
     * ```
     * val query = Query(querySeed)
     * ```
     */
    internal operator fun invoke(querySeed: QuerySeed): Query = QueryImpl(querySeed)
  }
}

private class QueryImpl(override val seed: QuerySeed) : Query
