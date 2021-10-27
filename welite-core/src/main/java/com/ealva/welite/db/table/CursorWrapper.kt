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

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.type.buildStr

internal interface CursorWrapper<C : ColumnSet> : Cursor, AutoCloseable {
  fun moveToNext(): Boolean

  companion object {
    fun <C : ColumnSet> select(
      seed: QuerySeed<C>,
      db: SQLiteDatabase,
      bind: C.(ArgBindings) -> Unit
    ): CursorWrapper<C> = CursorWrapperImpl(
      db.select(seed.sql, doBind(seed, bind)),
      seed.columns
    )
  }
}

private typealias ACursor = android.database.Cursor

private class CursorWrapperImpl<C : ColumnSet>(
  private val cursor: ACursor,
  columns: List<Expression<*>>
) : CursorWrapper<C> {
  private val exprMap = ExpressionToIndexMap(columns)

  override val count: Int
    get() = cursor.count

  override val position: Int
    get() = cursor.position

  override val columnCount: Int
    get() = cursor.columnCount

  override fun moveToNext(): Boolean = cursor.moveToNext()

  override fun <T> getOptional(expression: SqlTypeExpression<T>): T? =
    expression.persistentType.columnValue(this, exprMap[expression])

  override fun <T> get(expression: SqlTypeExpression<T>): T =
    getOptional(expression) ?: throw IllegalStateException(unexpectedNullMessage(expression))

  override fun getBlob(columnIndex: Int): ByteArray = cursor.getBlob(columnIndex)
  override fun getString(columnIndex: Int): String = cursor.getString(columnIndex)
  override fun getShort(columnIndex: Int) = cursor.getShort(columnIndex)
  override fun getInt(columnIndex: Int) = cursor.getInt(columnIndex)
  override fun getLong(columnIndex: Int) = cursor.getLong(columnIndex)
  override fun getFloat(columnIndex: Int) = cursor.getFloat(columnIndex)
  override fun getDouble(columnIndex: Int) = cursor.getDouble(columnIndex)
  override fun isNull(columnIndex: Int) = cursor.isNull(columnIndex)
  override fun columnName(columnIndex: Int): String = cursor.getColumnName(columnIndex)

  override fun close() {
    if (!cursor.isClosed) cursor.close()
  }

  private fun <T> unexpectedNullMessage(expression: Expression<T>) =
    "Unexpected NULL reading column=${expression.name()}"

  private fun <T> Expression<T>.name() = cursor.getColumnName(exprMap[this])
}

internal fun SQLiteDatabase.select(sql: String, args: Array<String> = emptyArray()): ACursor {
  if (WeLiteLog.logQueryPlans) logQueryPlan(sql, args)
  return rawQuery(sql, args)
}

internal fun SQLiteDatabase.longForQuery(sql: String, args: Array<String> = emptyArray()): Long {
  if (WeLiteLog.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.longForQuery(this, sql, args)
}

internal fun <C : ColumnSet> SQLiteDatabase.longForQuery(
  seed: QuerySeed<C>,
  bind: C.(ArgBindings) -> Unit
): Long = longForQuery(seed.sql, doBind(seed, bind))

internal fun SQLiteDatabase.stringForQuery(
  sql: String,
  args: Array<String> = emptyArray()
): String {
  if (WeLiteLog.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.stringForQuery(this, sql, args)
}

internal fun <C : ColumnSet> SQLiteDatabase.stringForQuery(
  seed: QuerySeed<C>,
  bind: C.(ArgBindings) -> Unit
): String = stringForQuery(seed.sql, doBind(seed, bind))

private val QP_LOG by lazyLogger(WeLiteLog.QUERY_PLAN_TAG, WeLiteLog.marker)
private fun SQLiteDatabase.logQueryPlan(sql: String, selectionArgs: Array<String>) {
  QP_LOG.i { it("Plan for:\nSQL:%s\nargs:%s", sql, selectionArgs.contentToString()) }
  rawQuery("""EXPLAIN QUERY PLAN $sql""", selectionArgs).use { cursor ->
    while (cursor.moveToNext()) {
      QP_LOG.i { it("%d: %s", cursor.position, cursor.rowContentToString()) }
    }
  }
}

/**
 * Convert the current row of the cursor to a string containing column "name:value" pairs
 * delimited with ", "
 */
private fun ACursor.rowContentToString(): String = buildStr {
  for (i in 0 until columnCount) {
    append(getColumnName(i)).append(':').append(getStringOrNull(i)).append(", ")
  }
}

private fun ACursor.getStringOrNull(columnIndex: Int): String =
  if (isNull(columnIndex)) "NULL" else getString(columnIndex)

private const val ID_COLUMN = 0
private const val NAME_COLUMN = 1
private const val TYPE_COLUMN = 2
private const val NULLABLE_COLUMN = 3
private const val DEF_VAL_COLUMN = 4
private const val PK_COLUMN = 5
private const val NOT_NULLABLE = 1
internal val ACursor.columnMetadata: ColumnMetadata
  get() = ColumnMetadata(
    getInt(ID_COLUMN),
    getString(NAME_COLUMN),
    FieldType.fromType(getString(TYPE_COLUMN)),
    getInt(NULLABLE_COLUMN) != NOT_NULLABLE, // 1 = not nullable
    getStringOrNull(DEF_VAL_COLUMN),
    getInt(PK_COLUMN)
  )

private fun <C : ColumnSet> doBind(
  seed: QuerySeed<C>,
  bind: C.(ArgBindings) -> Unit
): Array<String> = QueryArgs(seed).let { queryArgs ->
  seed.sourceSet.bind(queryArgs)
  check(queryArgs.allBound) {
    "Unbound indices:${queryArgs.unboundIndices} in QueryArgs:$queryArgs"
  }
  queryArgs.args
}

private val LOG by lazyLogger(QueryArgs::class, WeLiteLog.marker)

private class QueryArgs<C : ColumnSet>(seed: QuerySeed<C>) : ArgBindings {
  private val argTypes = seed.types
  private val expressionToIndexMap = seed.expressionToIndexMap
  private val arguments = Array(argTypes.size) { UNBOUND }

  override operator fun <T> set(index: Int, value: T?) {
    require(index in argTypes.indices) { "Arg types $index out of bounds ${argTypes.indices}" }
    require(index in arguments.indices) { "Args $index out of bounds ${arguments.indices}" }
    if (arguments[index] !== UNBOUND) {
      LOG.w { it("Arg at index:$index previously bound as ${arguments[index]}") }
    }
    arguments[index] = argTypes[index].valueToString(value, false)
  }

  override fun <T> set(expression: Expression<T>, value: T?) {
    set(expressionToIndexMap[expression], value)
  }

  override val argCount: Int
    get() = argTypes.size

  val args: Array<String>
    get() = arguments.copyOf()

  val allBound: Boolean
    get() = arguments.indexOfFirst { it === UNBOUND } < 0

  val unboundIndices: List<Int>
    get() = arguments.mapIndexedNotNullTo(ArrayList(arguments.size)) { index, arg ->
      if (arg === UNBOUND) index else null
    }

  override fun toString(): String {
    return arguments.contentToString()
  }

  companion object {
    /**
     * Marker in bind parameter array indicating the arg at an index has not been bound. Use object
     * identity for comparison as this marker value might be a valid bound parameter.
     */
    private const val UNBOUND = "NULL"
  }
}
