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
import com.ealva.welite.db.expr.ExprList
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder

/**
 * Represents a set of columns. Conceptually a [ColumnSet] is the ```FROM``` part of a query, eg.
 * Table, Join, Alias,...
 */
interface ColumnSet {
  val columns: List<Column<*>>
  val identity: Identity

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
    additionalConstraint: (() -> Op<Boolean>)? = null
  ): Join

  fun innerJoin(joinTo: ColumnSet): Join
  fun leftJoin(joinTo: ColumnSet): Join
  fun crossJoin(joinTo: ColumnSet): Join
  fun naturalJoin(joinTo: ColumnSet): Join
}

/**
 * Start select by selecting all [columns]
 */
fun ColumnSet.select(vararg columns: Expression<*>): SelectFrom = select(columns.distinct())

/**
 * Start select with [columns] which defaults to all columns of this [ColumnSet]
 */
fun ColumnSet.select(columns: ExprList = this.columns): SelectFrom =
  SelectFrom(columns.distinct(), this)

/**
 * Select COUNT(*) (no columns) using [where]
 */
fun ColumnSet.selectCount(where: () -> Op<Boolean>): QueryBuilder =
  QueryBuilder(set = select(emptyList()), where = where(), count = true)

/**
 * Select all columns [where]
 */
fun ColumnSet.selectWhere(where: Op<Boolean>?): QueryBuilder = select().where(where)

/**
 * Select all columns and all rows
 */
fun ColumnSet.selectAll(): QueryBuilder = QueryBuilder(SelectFrom(columns, this), null)

/**
 * Select all columns of this [ColumnSet] and call [where] to make the where expression
 */
fun ColumnSet.selectWhere(where: () -> Op<Boolean>): QueryBuilder = selectWhere(where())

/**
 * SelectFrom is a subset of columns from a ColumnSet, plus any added expressions which appear as
 * a result column, which are the fields to be read in a query. The columns know how to read
 * themselves from the underlying DB layer. Also contains the ```FROM``` part of a query.
 */
interface SelectFrom {
  fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder

  fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder

  fun sourceSetColumnsInResult(): List<Column<*>>

  fun <T> findSourceSetOriginal(original: Column<T>): Column<*>?

  /** Take a subset of the result columns  */
  fun subset(vararg columns: Expression<*>): SelectFrom

  /** Take a subset of the result columns  */
  fun subset(columns: ExprList): SelectFrom

  fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>?

  /** Result columns as they appear in a Select */
  val resultColumns: ExprList

  companion object {
    /**
     * Make a SelectFrom from [resultColumns] and an optional [sourceSet] ColumnSet
     */
    operator fun invoke(
      resultColumns: ExprList,
      sourceSet: ColumnSet?
    ): SelectFrom = SelectFromImpl(resultColumns, sourceSet)
  }
}

private data class SelectFromImpl(
  private val columns: ExprList,
  private val sourceSet: ColumnSet?
) : SelectFrom {
  override fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    sourceSet?.let { columnSet ->
      append(" FROM ")
      columnSet.appendTo(this)
    }
  }

  override fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    columns.appendEach { append(it) }
  }

  override fun sourceSetColumnsInResult(): List<Column<*>> {
    return sourceSet?.columns?.filterTo(mutableListOf()) { it in columns } ?: emptyList()
  }

  override fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? {
    return sourceSet?.columns?.find { it == original }
  }

  override fun subset(vararg columns: Expression<*>): SelectFrom = subset(columns.asList())

  override fun subset(columns: ExprList): SelectFrom =
    SelectFrom(columns.distinct(), sourceSet)

  override fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? {
    @Suppress("UNCHECKED_CAST")
    return columns.find { it == original } as? SqlTypeExpressionAlias<T>
  }

  override val resultColumns: ExprList
    get() = columns.toList()
}

fun SelectFrom.where(where: Op<Boolean>?): QueryBuilder = QueryBuilder(this, where)

/**
 * Calls [where] to create the where clause and then makes a [QueryBuilder] from this [SelectFrom]
 * and the where clause.
 */
fun SelectFrom.where(where: () -> Op<Boolean>): QueryBuilder = where(where())

/** All rows will be returned */
fun SelectFrom.all() = where(null)
