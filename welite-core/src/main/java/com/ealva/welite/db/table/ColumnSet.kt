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
import com.ealva.welite.db.expr.SqlTypeExpression
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

  public fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>? = null,
    otherColumn: Expression<*>? = null,
    additionalConstraint: (() -> Op<Boolean>)? = null
  ): Join

  public fun innerJoin(joinTo: ColumnSet): Join
  public fun leftJoin(joinTo: ColumnSet): Join
  public fun crossJoin(joinTo: ColumnSet): Join
  public fun naturalJoin(joinTo: ColumnSet): Join

  /**
   * Start select with [columns] which defaults to all columns of this [ColumnSet]. As currently
   * designed all [select]s come through here, then any 'where' is added.
   */
  public fun select(columns: List<Expression<*>> = this.columns): SelectFrom =
    SelectFrom(columns.distinct(), this)

  /**
   * Start select by selecting all [columns] arguments
   */
  public fun select(vararg columns: Expression<*>): SelectFrom = select(columns.distinct())

  /**
   * Select COUNT(*) (no columns) using optional [where]
   */
  public fun selectCount(where: (() -> Op<Boolean>)? = null): QueryBuilder =
    QueryBuilder(set = select(emptyList()), where = where?.invoke(), count = true)

  /**
   * Select all columns [where]
   */
  public fun selectWhere(where: Op<Boolean>?): QueryBuilder = select().where(where)

  /**
   * Select all columns and all rows
   */
  public fun selectAll(): QueryBuilder = selectWhere(null)

  /**
   * Select all columns of this [ColumnSet] and call [where] to make the where expression
   */
  public fun selectWhere(where: () -> Op<Boolean>): QueryBuilder = selectWhere(where())
}

/**
 * SelectFrom is a subset of columns from a ColumnSet, plus any added expressions which appear as
 * a result column, which are the fields to be read in a query. The columns know how to read
 * themselves from the underlying DB layer. Also contains the ```FROM``` part of a query.
 */
public interface SelectFrom {
  public fun where(where: Op<Boolean>?): QueryBuilder = QueryBuilder(this, where)

  /**
   * Calls [where] to create the where clause and then makes a [QueryBuilder] from this [SelectFrom]
   * and the where clause.
   */
  public fun where(where: () -> Op<Boolean>): QueryBuilder = where(where())

  /** All rows will be returned */
  public fun all(): QueryBuilder = where(null)

  public fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder

  public fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder

  public fun sourceSetColumnsInResult(): List<Column<*>>

  public fun <T> findSourceSetOriginal(original: Column<T>): Column<*>?

  /** Take a subset of the result columns  */
  public fun subset(vararg columns: Expression<*>): SelectFrom

  /** Take a subset of the result columns  */
  public fun subset(columns: List<Expression<*>>): SelectFrom

  public fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>?

  /** Result columns as they appear in a Select */
  public val resultColumns: List<Expression<*>>

  public companion object {
    /**
     * Make a SelectFrom from [resultColumns] and an optional [sourceSet] ColumnSet
     */
    public operator fun invoke(
      resultColumns: List<Expression<*>>,
      sourceSet: ColumnSet?
    ): SelectFrom = SelectFromImpl(resultColumns, sourceSet)
  }
}

private data class SelectFromImpl(
  private val columns: List<Expression<*>>,
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

  override fun subset(columns: List<Expression<*>>): SelectFrom =
    SelectFrom(columns.distinct(), sourceSet)

  override fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? {
    @Suppress("UNCHECKED_CAST")
    return columns.find { it == original } as? SqlTypeExpressionAlias<T>
  }

  override val resultColumns: List<Expression<*>>
    get() = columns.toList()
}
