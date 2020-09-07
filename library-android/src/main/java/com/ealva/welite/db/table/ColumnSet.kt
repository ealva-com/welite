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
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.SqlBuilder

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
    additionalConstraint: (() -> Op<Boolean>)? = null
  ): Join

  fun innerJoin(joinTo: ColumnSet): Join
  fun leftJoin(joinTo: ColumnSet): Join
  fun crossJoin(joinTo: ColumnSet): Join
  fun naturalJoin(joinTo: ColumnSet): Join
}

fun ColumnSet.select(vararg columns: Expression<*>): SelectFrom = select(columns.distinct())

fun ColumnSet.select(columns: List<Expression<*>> = this.columns): SelectFrom =
  SelectFrom(columns.distinct(), this)

fun ColumnSet.selectWhere(where: Op<Boolean>?): QueryBuilder = select().where(where)

fun ColumnSet.selectAll(): QueryBuilder = QueryBuilder(SelectFrom(columns, this), null)

/**
 * Select all columns of this [ColumnSet] and call [build] to make the where expression
 */
fun ColumnSet.selectWhere(build: () -> Op<Boolean>): QueryBuilder = selectWhere(build())

/**
 * A SelectFrom is a subset of columns from a ColumnSet, [resultColumns]s, which are the
 * fields to be read in a query. The columns know how to read themselves from the underlying DB
 * layer. Also contains the [sourceSet] which is the ```FROM``` part of a query.
 */
interface SelectFrom {
  /** Result columns as they appear in a Select */
  val resultColumns: List<Expression<*>>

  /** Represents the ```FROM``` clause of a query */
  val sourceSet: ColumnSet

  companion object {
    operator fun invoke(
      resultColumns: List<Expression<*>>,
      sourceSet: ColumnSet
    ): SelectFrom = SelectFromImpl(resultColumns, sourceSet)
  }
}

private data class SelectFromImpl(
  override val resultColumns: List<Expression<*>>,
  override val sourceSet: ColumnSet
) : SelectFrom

fun SelectFrom.where(where: Op<Boolean>?): QueryBuilder =  QueryBuilder(this, where)

/**
 * Calls [where] to create the where clause and then makes a [QueryBuilder] from this [SelectFrom]
 * and the where clause.
 */
fun SelectFrom.where(where: () -> Op<Boolean>): QueryBuilder = where(where())

/** All rows will be returned */
fun SelectFrom.all() = where(null)

/** Take a subset of the [SelectFrom.resultColumns]  */
fun SelectFrom.subset(vararg columns: SqlTypeExpression<*>): SelectFrom = subset(columns.asList())

/** Take a subset of the [SelectFrom.resultColumns]  */
fun SelectFrom.subset(columns: List<SqlTypeExpression<*>>): SelectFrom =
  SelectFrom(columns.distinct(), sourceSet)

fun ColumnSet.selectCase(vararg columns: Expression<*>): SelectFrom = select(columns.distinct())

fun ColumnSet.selectCase(columns: List<Expression<*>> = this.columns): SelectFrom =
  SelectFrom(columns.distinct(), this)

interface SelectCase {

}

