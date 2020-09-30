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

package com.ealva.welite.db.statements

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.type.DefaultValueMarker

/**
 * Denotes a column whose value will be bound as the statement/query is executed. This is used
 * so that statements/queries may be reused (no need to form and compile the SQL again).
 */
interface BindArgument {
  fun bindArg()
}

/**
 * Represents a group of columns to which values are assigned, such as during an insert or update.
 * For example:
 * ```
 * insertValues {
 *   it[MyTable.columnA] = "Important"
 *   it[MyTable.columnB] = 5
 *   it[MyTable.columnC] = null
 * }
 * ```
 */
interface ColumnValues {
  val columnValueList: MutableList<ColumnValue<*>>

  operator fun <T> set(column: Column<T>, value: T)

  operator fun <T, E : Expression<T>> set(column: Column<T>, expression: E)

  operator fun <T> get(column: Column<T>): BindArgument

  companion object {
    /**
     * Make a ColumnValues instance which contains columns and their respective value/expression.
     */
    operator fun invoke(): ColumnValues = ColumnValuesImpl()
  }
}

private class ColumnValuesImpl : ColumnValues {
  override val columnValueList = mutableListOf<ColumnValue<*>>()

  override operator fun <S> set(column: Column<S>, value: S) {
    columnValueList.add(ColumnValueWithValue(column, value))
  }

  override operator fun <T, E : Expression<T>> set(column: Column<T>, expression: E) {
    columnValueList.add(ColumnValueWithExpression(column, expression))
  }

  override fun <T> get(column: Column<T>): BindArgument {
    return object : BindArgument {
      override fun bindArg() {
        columnValueList.add(ColumnValueWithExpression(column, column.bindArg()))
      }
    }
  }
}

interface ColumnValue<S> {
  val column: Column<S>
  fun appendColumnTo(builder: SqlBuilder) = builder.append(column.identity().value)
  fun appendValueTo(builder: SqlBuilder)
  fun appendColumnEqValueTo(builder: SqlBuilder) {
    appendColumnTo(builder)
    builder.append("=")
    appendValueTo(builder)
  }
}

private fun <T> SqlBuilder.registerArgument(column: Column<T>, argument: T) {
  when (argument) {
    is Expression<*> -> append(argument)
    DefaultValueMarker -> column.dbDefaultValue?.let { append(it) } ?: append("NULL")
    else -> registerArgument(column.persistentType, argument)
  }
}

class ColumnValueWithValue<S>(
  override val column: Column<S>,
  private val value: S
) : ColumnValue<S> {
  override fun appendValueTo(builder: SqlBuilder) {
    builder.registerArgument(column, value)
  }
}

class ColumnValueWithExpression<S>(
  override val column: Column<S>,
  private val expression: Expression<S>
) : ColumnValue<S> {
  override fun appendValueTo(builder: SqlBuilder) {
    builder.append(expression)
  }
}

fun SqlBuilder.append(columnValue: ColumnValue<*>) = columnValue.appendColumnEqValueTo(this)
fun SqlBuilder.appendName(columnValue: ColumnValue<*>) = columnValue.appendColumnTo(this)
fun SqlBuilder.appendValue(columnValue: ColumnValue<*>) = columnValue.appendValueTo(this)
