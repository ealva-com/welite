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
    thisColumn: Expression<*>? = null,
    otherColumn: Expression<*>? = null,
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

/**
 * Start select with [columns] which defaults to all columns of this [ColumnSet]. As currently
 * designed all [select]s come through here, then any 'where' is added.
 */
public fun <C : ColumnSet> C.select(columns: List<Expression<*>> = this.columns): SelectFrom<C> =
  SelectFrom(columns.distinct(), this)

/**
 * Start select by selecting all [columns] arguments
 */
public fun <C : ColumnSet> C.select(
  vararg columns: Expression<*>
): SelectFrom<C> = select(columns.distinct())

/**
 * SelectFrom is a subset of columns from a ColumnSet, plus any added expressions which appear as
 * a result column, which are the fields to be read in a query. The columns know how to read
 * themselves from the underlying DB layer. Also contains the ```FROM``` part of a query.
 */
public interface SelectFrom<C : ColumnSet> {
  public val sourceSet: C

  public fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder

  public fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder

  public fun sourceSetColumnsInResult(): List<Column<*>>

  public fun <T> findSourceSetOriginal(original: Column<T>): Column<*>?

  /** Take a subset of the result columns  */
  public fun subset(vararg columns: Expression<*>): SelectFrom<C>

  /** Take a subset of the result columns  */
  public fun subset(columns: List<Expression<*>>): SelectFrom<C>

  public fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>?

  /** Result columns as they appear in a Select */
  public val resultColumns: List<Expression<*>>

  public companion object {
    /**
     * Make a SelectFrom from [resultColumns] and an optional [sourceSet] ColumnSet
     */
    public operator fun <C : ColumnSet> invoke(
      resultColumns: List<Expression<*>>,
      sourceSet: C
    ): SelectFrom<C> = SelectFromImpl(resultColumns, sourceSet)
  }
}

private data class SelectFromImpl<C : ColumnSet>(
  override val resultColumns: List<Expression<*>>,
  override val sourceSet: C
) : SelectFrom<C> {
  override fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    sourceSet.appendFromTo(this)
  }

  override fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    resultColumns.appendEach { append(it) }
  }

  override fun sourceSetColumnsInResult(): List<Column<*>> =
    sourceSet.columnsIntersect(resultColumns)

  override fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? = sourceSet.find(original)

  override fun subset(vararg columns: Expression<*>): SelectFrom<C> = subset(columns.asList())

  override fun subset(columns: List<Expression<*>>): SelectFrom<C> =
    SelectFromImpl(
      columns
        .asSequence()
        .distinct()
        .mapNotNull { column -> resultColumns.find { it == column } }
        .toList(),
      sourceSet
    )

  override fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? {
    @Suppress("UNCHECKED_CAST")
    return resultColumns.find { it == original } as? SqlTypeExpressionAlias<T>
  }
}
