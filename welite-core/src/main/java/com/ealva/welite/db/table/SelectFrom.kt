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
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.SqlBuilder

/**
 * Begin a SELECT with a set of columns. Defaults to all
 * columns of the this ColumnSet Useful when the source set receiver saves specifying column table
 * names.
 */
public inline fun <C : ColumnSet> C.selects(
  columnsProducer: C.() -> List<Expression<*>> = { columns }
): SelectFrom<C> = SelectFrom(columnsProducer(), this)

/**
 * Begin a select with a single column. Useful when selecting a single column and the
 * source set receiver saves specifying the column table.
 */
public inline fun <C : ColumnSet> C.select(
  columnProducer: C.() -> Expression<*>
): SelectFrom<C> = selects { listOf(columnProducer()) }

/**
 * Begin a select with one or more [columns]. Typically prefer [selects] as it uses the source
 * column set as a receiver (this version may save typing on some source sets such as a Join).
 */
public fun <C : ColumnSet> C.select(
  vararg columns: Expression<*>
): SelectFrom<C> = selects { columns.toList() }

/**
 * Begin a select with one or more [columns]. Typically prefer [selects] as it uses the source
 * column set as a receiver.
 */
public fun <C : ColumnSet> C.select(
  columns: List<Expression<*>> = this.columns
): SelectFrom<C> = selects { columns }

/**
 * SelectFrom is a subset of columns from a ColumnSet, plus any added expressions which appear as
 * a result column, which are the fields to be read in a query. The columns know how to read
 * themselves from the underlying DB layer. Also contains the ```FROM``` part of a query.
 */
public interface SelectFrom<out C : ColumnSet> {
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
    ): SelectFrom<C> = SelectFromImpl(resultColumns.distinct(), sourceSet)
  }
}

private data class SelectFromImpl<out C : ColumnSet>(
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
