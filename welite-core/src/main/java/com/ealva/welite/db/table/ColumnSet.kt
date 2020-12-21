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
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.type.AppendsToSqlBuilder
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder

/**
 * Represents a set of columns. Conceptually a [ColumnSet] is the ```FROM``` part of a query, eg.
 * Table, Join, Alias,...
 */
public interface ColumnSet : AppendsToSqlBuilder {
  public val columns: List<Column<*>>
  public val identity: Identity

  public fun find(column: Column<*>): Column<*>?
  public fun findColumnWithName(name: String): Column<*>?
  public fun columnsIntersect(other: List<Expression<*>>): List<Column<*>>

  /**
   * Appends the ```FROM``` clause, without the "FROM", of this ColumnSet to
   * [queryStringBuilder]. Implementations will append table, subquery, or join clause.
   * ```
   * select_or_values
   *  : K_SELECT ( K_DISTINCT | K_ALL )? result_column ( ',' result_column )*
   *    ( K_FROM ( table_or_subquery ( ',' table_or_subquery )* | join_clause ) )?
   * ```
   */
  public override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder

  /**
   * Appends " FROM " and then calls [appendTo] is applicable. An implementation of ColumnSet may
   * not append anything if it does not have a FROM clause. A simple select is not required to have
   * a FROM
   */
  public fun appendFromTo(sqlBuilder: SqlBuilder): SqlBuilder

  public fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    column: Expression<*>? = null,
    joinToColumn: Expression<*>? = null,
    additionalConstraint: (() -> Op<Boolean>)? = null
  ): Join

  public fun innerJoin(joinTo: ColumnSet): Join
  public fun leftJoin(joinTo: ColumnSet): Join
  public fun crossJoin(joinTo: ColumnSet): Join
  public fun naturalJoin(joinTo: ColumnSet): Join
}

public abstract class BaseColumnSet : ColumnSet {
  override fun find(column: Column<*>): Column<*>? = columns.find { it == column }

  override fun findColumnWithName(name: String): Column<*>? =
    columns.firstOrNull { name == it.name }

  override fun columnsIntersect(other: List<Expression<*>>): List<Column<*>> =
    columns.filterTo(mutableListOf()) { it in other }

  override fun appendFromTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(" FROM ")
    appendTo(this)
  }
}
