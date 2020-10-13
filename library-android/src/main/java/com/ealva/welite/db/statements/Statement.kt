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
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType

private val LOG by lazyLogger(Statement::class, WeLiteLog.marker)

public interface Statement {
  public fun execute(db: SQLiteDatabase, bindArgs: (ArgBindings) -> Unit): Long
}

public abstract class BaseStatement : Statement {
  protected abstract val sql: String
  protected abstract val types: List<PersistentType<*>>

  override fun toString(): String = sql

  private val _statementAndTypes: StatementAndTypes? = null
  internal fun getStatementAndTypes(db: SQLiteDatabase): StatementAndTypes {
    return (_statementAndTypes ?: StatementAndTypes(db.compileStatement(sql), types))
  }
}

internal class StatementAndTypes(
  private val statement: SQLiteStatement,
  private val types: List<PersistentType<*>>,
  private val logSql: Boolean = WeLiteLog.logSql
) : Bindable, ArgBindings {
  override val argCount: Int
    get() = types.size
  private val argRange: IntRange
    get() = types.indices

  /**
   * This is only used for logging
   */
  private val bindings: Array<Any?> = Array(argCount) { null }

  fun executeInsert(bindArgs: (ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeInsert()
  }

  fun executeDelete(bindArgs: (ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeUpdateDelete().toLong()
  }

  fun executeUpdate(bindArgs: (ArgBindings) -> Unit): Long {
    bindArgsToStatement(bindArgs)
    return statement.executeUpdateDelete().toLong()
  }

  private fun bindArgsToStatement(bindArgs: (ArgBindings) -> Unit) {
    clearBindings()
    bindArgs(this)
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

  private fun ensureIndexInBounds(index: Int) {
    require(index in argRange) { "Out of bounds index=$index indices=$argRange" }
  }
}
