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

package com.ealva.welite.db

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.table.ColumnMetadata
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.FieldType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.type.Row
import com.ealva.welite.db.type.buildStr
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

private typealias ExpressionToIndexMap = Object2IntMap<Expression<*>>

private fun List<Expression<*>>.mapExprToIndex(): ExpressionToIndexMap {
  return Object2IntOpenHashMap<Expression<*>>(size).apply {
    defaultReturnValue(-1)
    this@mapExprToIndex.forEachIndexed { index, expression -> put(expression, index) }
  }
}private typealias ACursor = android.database.Cursor

internal class CursorWrapper(
  private val cursor: ACursor,
  columns: List<Expression<*>>
) : Cursor, Row, AutoCloseable {
  private val exprMap = columns.mapExprToIndex()

  override val count: Int
    get() = cursor.count

  override val position: Int
    get() = cursor.position

  fun moveToNext(): Boolean = cursor.moveToNext()

  @Suppress("NOTHING_TO_INLINE")
  private inline fun <T> SqlTypeExpression<T>.index() = exprMap.getInt(this)

  override fun <T> getOptional(expression: SqlTypeExpression<T>): T? =
    expression.persistentType.columnValue(this, expression.index())

  override fun <T> get(expression: SqlTypeExpression<T>): T {
    return getOptional(expression) ?: throw IllegalStateException(unexpectedNullMessage(expression))
  }

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

  private fun <T> unexpectedNullMessage(expression: SqlTypeExpression<T>) =
    "Unexpected NULL reading column=${expression.name()} of expected type ${expression.sqlType}"

  private fun <T> SqlTypeExpression<T>.name() = cursor.getColumnName(index())
}

internal fun SQLiteDatabase.select(sql: String, args: Array<String> = emptyArray()): ACursor {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return rawQuery(sql, args)
}

internal fun SQLiteDatabase.longForQuery(sql: String, args: Array<String> = emptyArray()): Long {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.longForQuery(this, sql, args)
}

internal fun SQLiteDatabase.stringForQuery(
  sql: String,
  args: Array<String> = emptyArray()
): String {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.stringForQuery(this, sql, args)
}

private val LOG by lazyLogger("QueryPlan")
private fun SQLiteDatabase.logQueryPlan(sql: String, selectionArgs: Array<String>) {
  LOG.i { it("Plan for:\nSQL:%s\nargs:%s", sql, selectionArgs.contentToString()) }
  explainQueryPlan(sql, selectionArgs).forEachIndexed { index, row ->
    LOG.i { it("%d: %s", index, row) }
  }
}

private fun SQLiteDatabase.explainQueryPlan(
  sql: String,
  selectionArgs: Array<String>
): List<String> {
  return mutableListOf<String>().apply {
    rawQuery("""EXPLAIN QUERY PLAN $sql""", selectionArgs).use { cursor ->
      while (cursor.moveToNext()) add(cursor.rowToString())
    }
  }
}

/**
 * Convert the current row of the cursor to a string containing column "name:value" pairs
 * delimited with ", "
 */
private fun ACursor.rowToString(): String = buildStr {
  for (i in 0 until columnCount) {
    append(getColumnName(i)).append(':').append(getStringOrNull(i)).append(", ")
  }
}

internal fun ACursor.getOptLong(columnIndex: Int, ifNullValue: Long = -1): Long =
  if (isNull(columnIndex)) ifNullValue else getLong(columnIndex)

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
