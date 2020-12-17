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

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.ExpressionToIndexMap
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val LOG by lazyLogger(Statement::class, WeLiteLog.marker)

public interface Statement<C : ColumnSet> {
  public fun execute(db: SQLiteDatabase, bindArgs: C.(ArgBindings) -> Unit): Long
}

public interface StatementAndTypes<C : ColumnSet> : Bindable, ArgBindings {
  public fun executeInsert(bindArgs: C.(ArgBindings) -> Unit): Long
  public fun executeDelete(bindArgs: C.(ArgBindings) -> Unit): Long
  public fun executeUpdate(bindArgs: C.(ArgBindings) -> Unit): Long

  public companion object {
    public operator fun <C : ColumnSet> invoke(
      columnSet: C,
      statement: SQLiteStatement,
      expressionToIndexMap: ExpressionToIndexMap,
      types: List<PersistentType<*>>,
      logSql: Boolean = WeLiteLog.logSql
    ): StatementAndTypes<C> {
      return StatementAndTypesImpl(columnSet, statement, expressionToIndexMap, types, logSql)
    }
  }
}

public abstract class BaseStatement<C : ColumnSet>(
  protected val columnSet: C
) : Statement<C> {

  protected abstract val sql: String
  protected abstract val expressionToIndexMap: ExpressionToIndexMap
  protected abstract val types: List<PersistentType<*>>
  private val executeLock: Lock = ReentrantLock(true) // fair lock

  /**
   * Execute binding arguments and execution of the statement under lock in case the same statement
   * is used across threads. For performance it's encouraged to save pre-build statements and
   * execute/bind when needed, so statements may be used across threads concurrently.
   */
  final override fun execute(
    db: SQLiteDatabase,
    bindArgs: C.(ArgBindings) -> Unit
  ): Long = executeLock.withLock { doExecute(db, bindArgs) }

  public abstract fun doExecute(db: SQLiteDatabase, bindArgs: C.(ArgBindings) -> Unit): Long

  override fun toString(): String = sql

  private val _statementAndTypes: StatementAndTypesImpl<C>? = null
  protected fun getStatementAndTypes(
    db: SQLiteDatabase
  ): StatementAndTypes<C> = _statementAndTypes ?: StatementAndTypes(
    columnSet,
    db.compileStatement(sql),
    expressionToIndexMap,
    types
  )
}

private class StatementAndTypesImpl<C : ColumnSet>(
  private val columnSet: C,
  private val statement: SQLiteStatement,
  private val expressionToIndexMap: ExpressionToIndexMap,
  private val types: List<PersistentType<*>>,
  private val logSql: Boolean
) : StatementAndTypes<C> {
  override val argCount: Int
    get() = types.size
  private val argRange: IntRange
    get() = types.indices

  /**
   * This is only used for logging
   */
  private val bindings: Array<Any?> = Array(argCount) { null }

  override fun executeInsert(bindArgs: C.(ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeInsert()
  }

  override fun executeDelete(bindArgs: C.(ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeUpdateDelete().toLong()
  }

  override fun executeUpdate(bindArgs: C.(ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeUpdateDelete().toLong()
  }

  private fun bindArgsToStatement(bindArgs: C.(ArgBindings) -> Unit) {
    clearBindings()
    columnSet.bindArgs(this)
    if (logSql) LOG.i { it("%s args:%s", statement, bindings.contentToString()) }
  }

  private fun clearBindings() {
    statement.clearBindings()
    bindings.fill(null)
  }

  override fun bindNull(index: Int) {
    ensureIndexInBounds(index)
    bindings[index] = null
    statement.bindNull(index + 1)
  }

  override fun bind(index: Int, value: Long) {
    ensureIndexInBounds(index)
    bindings[index] = value
    statement.bindLong(index + 1, value)
  }

  override fun bind(index: Int, value: Double) {
    ensureIndexInBounds(index)
    bindings[index] = value
    statement.bindDouble(index + 1, value)
  }

  override fun bind(index: Int, value: String) {
    ensureIndexInBounds(index)
    bindings[index] = value
    statement.bindString(index + 1, value)
  }

  override fun bind(index: Int, value: ByteArray) {
    ensureIndexInBounds(index)
    bindings[index] = value
    statement.bindBlob(index + 1, value)
  }

  // PersistentType will call back on this to do the actual bind and the index should just be
  // passed through, so we won't check the index until one of the other bind functions are called.
  override fun <T> set(index: Int, value: T?) = types[index].bind(this, index, value)

  override fun <T> set(expression: Expression<T>, value: T?) {
    set(expressionToIndexMap[expression], value)
  }

  private fun ensureIndexInBounds(index: Int) {
    require(index in argRange) { "Out of bounds index=$index indices=$argRange" }
  }
}
