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

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.DbConfig
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import java.util.Arrays
import kotlin.sequences.sequence as ksequence
import kotlinx.coroutines.flow.flow as kflow

/**
 * Interface with functions that perform a query. Query implements these functions as does
 * [QueryBuilder]. A Query can be built and reused whereas QueryBuilder is providing a fast
 * path call that builds the query and executes the call on the Query. Invoking these functions
 * on the QueryBuilder makes the Query single use and it's not exposed to clients.
 *
 * Query functions perform an internal iteration and don't expose the underlying cursor. Instead
 * implementations control the iteration and lifetime of the Cursor. Therefore, this does not
 * implement the [Iterable] interface. Instead of iterable prefer the [flow] or [sequence]
 * functions.
 */
interface PerformQuery {
  /**
   * Do any necessary [bindArgs], execute the query, and invoke [action] for each row in the
   * results.
   */
  fun forEach(bindArgs: (ParamBindings) -> Unit = NO_BIND, action: (Cursor) -> Unit)

  /**
   * Creates a flow, first doing any necessary [bindArgs], execute the query, and emit a [T]
   * created using [factory] for each row in the query results
   */
  fun <T> flow(bindArgs: (ParamBindings) -> Unit = NO_BIND, factory: (Cursor) -> T): Flow<T>

  /**
   * After any necessary [bindArgs] generate a [Sequence] of [T] using [factory] for each Cursor
   * row and yields a [T] into the [Sequence]
   */
  fun <T> sequence(bindArgs: (ParamBindings) -> Unit = NO_BIND, factory: (Cursor) -> T): Sequence<T>

  /**
   * Do any necessary [bindArgs], execute the query, and return the value in the first column of the
   * first row. Especially useful for ```COUNT``` queries
   */
  fun longForQuery(bindArgs: (ParamBindings) -> Unit = NO_BIND): Long

  /**
   * Do any necessary [bindArgs], execute the query, and return the value in the first column of the
   * first row.
   */
  fun stringForQuery(bindArgs: (ParamBindings) -> Unit = NO_BIND): String

  /**
   * Do any necessary [bindArgs], execute the query for count similar to
   * ```COUNT(*) FROM ( $thisQuery )```, and return the value in the first column of the
   * first row
   */
  fun count(bindArgs: (ParamBindings) -> Unit = NO_BIND): Long
}

interface Query : PerformQuery {

  val sql: String

  val requiresBindArguments: Boolean
    get() = expectedArgCount > 0

  val expectedArgCount: Int

  companion object {
    var logQueryPlans: Boolean = false

    /**
     * Make a Query instance. eg.
     * ```
     * val query = Query(db, fields, sql, bindables)
     * ```
     */
    internal operator fun invoke(
      dbConfig: DbConfig,
      fields: List<SqlTypeExpression<*>>,
      builder: SqlBuilder
    ): Query {
      return QueryImpl(dbConfig, fields, builder)
    }
  }
}

/** Take a subset of the [SelectFrom.resultColumns]  */
fun SelectFrom.subset(vararg columns: SqlTypeExpression<*>): SelectFrom =
  subset(columns.asList())

/** Take a subset of the [SelectFrom.resultColumns]  */
fun SelectFrom.subset(columns: List<SqlTypeExpression<*>>): SelectFrom =
  SelectFrom(columns.distinct(), sourceSet)

private typealias ACursor = android.database.Cursor

private val LOG by lazyLogger(Query::class)
private const val UNBOUND = "Unbound"

private class QueryArgs(private val argTypes: List<PersistentType<*>>) : ParamBindings {
  private val args = Array(argTypes.size) { UNBOUND }

  override operator fun set(index: Int, value: Any?) {
    require(index in argTypes.indices) { "Arg types $index out of bounds ${argTypes.indices}" }
    require(index in args.indices) { "Args $index out of bounds ${args.indices}" }
    args[index] = argTypes[index].valueToString(value)
  }

  override val paramCount: Int
    get() = argTypes.size

  val arguments: Array<String>
    get() = args.copyOf()
}

private class QueryImpl(
  private val dbConfig: DbConfig,
  private val fields: List<SqlTypeExpression<*>>,
  builder: SqlBuilder
) : Query {
  private val db = dbConfig.db
  override val sql = builder.toString()
  private val queryArgs = QueryArgs(builder.types)

  override fun forEach(bindArgs: (ParamBindings) -> Unit, action: (Cursor) -> Unit) {
    bindArgs(queryArgs)
    DbCursorWrapper(
      db.select(sql, queryArgs.arguments),
      fields.mapExprToIndex()
    ).use { cursor ->
      while (cursor.moveToNext()) action(cursor)
    }
  }

  override fun <T> flow(bindArgs: (ParamBindings) -> Unit, factory: (Cursor) -> T) = kflow {
    bindArgs(queryArgs)
    DbCursorWrapper(
      db.select(sql, queryArgs.arguments),
      fields.mapExprToIndex()
    ).use { cursor ->
      while (cursor.moveToNext()) emit(factory(cursor))
    }
  }.flowOn(dbConfig.dispatcher)

  override fun <T> sequence(
    bindArgs: (ParamBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = ksequence {
    bindArgs(queryArgs)
    DbCursorWrapper(
      db.select(sql, queryArgs.arguments),
      fields.mapExprToIndex()
    ).use { cursor ->
      while (cursor.moveToNext()) yield(factory(cursor))
    }
  }

  override fun longForQuery(bindArgs: (ParamBindings) -> Unit): Long = doLongForQuery(sql, bindArgs)

  private fun doLongForQuery(sql: String, binding: (ParamBindings) -> Unit): Long {
    binding(queryArgs)
    return db.longForQuery(sql, queryArgs.arguments)
  }

  override fun stringForQuery(bindArgs: (ParamBindings) -> Unit): String =
    doStringForQuery(sql, bindArgs)

  private fun doStringForQuery(sql: String, binding: (ParamBindings) -> Unit): String {
    binding(queryArgs)
    return db.stringForQuery(sql, queryArgs.arguments)
  }

  override fun count(bindArgs: (ParamBindings) -> Unit): Long {
    val alreadyCountQuery = sql.trim().startsWith("SELECT COUNT(*)", ignoreCase = true)
    return doLongForQuery(if (alreadyCountQuery) sql else "SELECT COUNT(*) FROM ( $sql )", bindArgs)
  }

  override val expectedArgCount: Int
    get() = queryArgs.paramCount
}

typealias ExpressionToIndexMap = Object2IntMap<SqlTypeExpression<*>>

private fun List<SqlTypeExpression<*>>.mapExprToIndex(): ExpressionToIndexMap {
  return Object2IntOpenHashMap<SqlTypeExpression<*>>(size).apply {
    defaultReturnValue(-1)
    this@mapExprToIndex.forEachIndexed { index, expression -> put(expression, index) }
  }
}

private class DbCursorWrapper(
  private val cursor: ACursor,
  private val exprMap: ExpressionToIndexMap
) : Cursor, Row, AutoCloseable {
  override val count: Int
    get() = cursor.count

  override val position: Int
    get() = cursor.position

  fun moveToNext(): Boolean = cursor.moveToNext()

  private fun <T : Any> SqlTypeExpression<T>.index() = exprMap.getInt(this)

  override fun <T : Any> getOptional(expression: SqlTypeExpression<T>): T? =
    expression.persistentType.columnValue(this, expression.index())

  override fun <T : Any> get(expression: SqlTypeExpression<T>): T =
    checkNotNull(getOptional(expression)) { unexpectedNullMessage(expression) }

  override fun getBlob(columnIndex: Int): ByteArray = cursor.getBlob(columnIndex)

  override fun getString(columnIndex: Int): String = cursor.getString(columnIndex)

  override fun getShort(columnIndex: Int) = cursor.getShort(columnIndex)

  override fun getInt(columnIndex: Int) = cursor.getInt(columnIndex)

  override fun getLong(columnIndex: Int) = cursor.getLong(columnIndex)

  override fun getFloat(columnIndex: Int) = cursor.getFloat(columnIndex)

  override fun getDouble(columnIndex: Int) = cursor.getDouble(columnIndex)

  override fun isNull(columnIndex: Int) = cursor.isNull(columnIndex)

  override fun columnName(columnIndex: Int): String = cursor.getColumnName(columnIndex)

  override fun close() = cursor.close()

  private fun <T : Any> unexpectedNullMessage(expression: SqlTypeExpression<T>) =
    "Unexpected NULL reading column=${expression.name()} of expected type ${expression.sqlType}"

  private fun <T : Any> SqlTypeExpression<T>.name() = cursor.getColumnName(index())
}

internal fun SQLiteDatabase.select(sql: String, args: Array<String>? = null): ACursor {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return rawQuery(sql, args)
}

internal fun SQLiteDatabase.longForQuery(sql: String, args: Array<String>? = null): Long {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.longForQuery(this, sql, args)
}

internal fun SQLiteDatabase.stringForQuery(sql: String, args: Array<String>? = null): String {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.stringForQuery(this, sql, args)
}

fun SQLiteDatabase.logQueryPlan(sql: String, selectionArgs: Array<String>?) {
  LOG.i { it("Plan for:\nSQL:%s\nargs:%s", sql, Arrays.toString(selectionArgs)) }
  explainQueryPlan(sql, selectionArgs).forEachIndexed { index, toLog ->
    LOG.i { it("%d: %s", index, toLog) }
  }
}

fun SQLiteDatabase.explainQueryPlan(
  sql: String,
  selectionArgs: Array<String>?
): List<String> {
  return mutableListOf<String>().apply {
    rawQuery("""EXPLAIN QUERY PLAN $sql""", selectionArgs).use { c ->
      while (c.moveToNext()) {
        add(
          buildString {
            for (i in 0 until c.columnCount) {
              append(c.getColumnName(i)).append(":").append(c.getString(i)).append(", ")
            }
          }
        )
      }
    }
  }
}
