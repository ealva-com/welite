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

package com.ealva.welite.db.compound

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.SqlTypeExpressionAlias
import com.ealva.welite.db.type.SqlBuilder

/**
 * All selected columns should refer to the results columns of the first simple select of this
 * CompoundSelect.
 *
 * All other CompoundSelect select functions should eventually call here to build the
 * CompoundSelectFrom.
 * If a column is of type Column and is found in the result columns of the first select,
 * it is converted
 */
public fun <C : ColumnSet> CompoundSelect<C>.select(
  columns: List<Expression<*>> = this.columns
): CompoundSelectFrom<C> {
  val resultColumns = columns.distinct().map { column -> mapSelectToResult[column] ?: column }
  return CompoundSelectFrom(resultColumns, firstColumnSet, this, mapSelectToResult.copy())
}

public fun <C : ColumnSet> CompoundSelect<C>.select(
  vararg columns: Expression<*>
): CompoundSelectFrom<C> {
  return select(columns.distinct())
}

/**
 * Begin a SELECT with a set of columns. Defaults to all
 * columns of the this ColumnSet Useful when the source set receiver saves specifying column table
 * names.
 */
public inline fun <C : ColumnSet> CompoundSelect<C>.selects(
  columnsProducer: C.() -> List<Expression<*>> = { columns }
): CompoundSelectFrom<C> = select(firstColumnSet.columnsProducer())

/**
 * Begin a select with a single column. Useful when selecting a single column and the
 * source set receiver saves specifying the column table.
 */
public inline fun <C : ColumnSet> CompoundSelect<C>.select(
  columnProducer: C.() -> Expression<*>
): SelectFrom<C> = selects { listOf(columnProducer()) }

public interface CompoundSelectFrom<out C : ColumnSet> : SelectFrom<C> {

  public val mapSelectToResult: SelectColumnToResultColumnMap

  public companion object {
    public operator fun <C : ColumnSet> invoke(
      resultColumns: List<Expression<*>>,
      sourceSet: C,
      compoundSelect: CompoundSelect<C>,
      selectToResultMap: SelectColumnToResultColumnMap
    ): CompoundSelectFrom<C> {
      return CompoundSelectFromImpl(
        resultColumns.distinct(),
        sourceSet,
        compoundSelect,
        selectToResultMap
      )
    }
  }
}

private class CompoundSelectFromImpl<out C : ColumnSet>(
  override val resultColumns: List<Expression<*>>,
  override val sourceSet: C,
  private val compoundSelect: CompoundSelect<C>,
  override val mapSelectToResult: SelectColumnToResultColumnMap
) : CompoundSelectFrom<C> {

  override fun appendFrom(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    compoundSelect.appendFromTo(this)
  }

  override fun appendResultColumns(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    resultColumns.appendEach { append(it) }
  }

  override fun sourceSetColumnsInResult(): List<Column<*>> =
    compoundSelect.columnsIntersect(resultColumns)

  override fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? =
    compoundSelect.find(original)

  override fun subset(vararg columns: Expression<*>): CompoundSelectFrom<C> {
    return subset(columns.asList())
  }

  override fun subset(columns: List<Expression<*>>): CompoundSelectFrom<C> =
    CompoundSelectFrom(
      columns
        .asSequence()
        .distinct()
        .mapNotNull { column -> resultColumns.find { it == column } }
        .toList(),
      sourceSet,
      compoundSelect,
      mapSelectToResult
    )

  override fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? {
    @Suppress("UNCHECKED_CAST")
    return resultColumns.find { it == original } as? SqlTypeExpressionAlias<T>
  }
}
