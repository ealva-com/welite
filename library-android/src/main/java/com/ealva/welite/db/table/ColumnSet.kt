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

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.ExpressionBuilder
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlBuilder

/**
 * Represents a set of columns. Conceptually a [ColumnSet] is the ```FROM``` part of a query, eg.
 * Table, Join, Alias,...
 */
interface ColumnSet {
  val columns: List<Column<*>>

  /**
   * Appends the ```FROM``` clause, without the "FROM", of this ColumnSet to
   * [queryStringBuilder]. Implementations will append table, subquery, or join clause.
   * ```
   * select_or_values
   *  : K_SELECT ( K_DISTINCT | K_ALL )? result_column ( ',' result_column )*
   *    ( K_FROM ( table_or_subquery ( ',' table_or_subquery )* | join_clause ) )?
   * ```
   */
  fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder

  fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>? = null,
    otherColumn: Expression<*>? = null,
    additionalConstraint: (ExpressionBuilder.() -> Op<Boolean>)? = null
  ): Join

  fun innerJoin(joinTo: ColumnSet): Join

  fun leftJoin(joinTo: ColumnSet): Join

  fun crossJoin(joinTo: ColumnSet): Join

  fun naturalJoin(joinTo: ColumnSet): Join
}

