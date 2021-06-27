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

package com.ealva.welite.db

import android.os.Build
import androidx.annotation.RequiresApi
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDescription
import com.ealva.welite.db.table.WeLiteMarker

/**
 * These functions are scoped to a Queryable interface in an attempt to only initiate queries while
 * a transaction is in progress. See [Database.query] for starting a query only under an explicit
 * transaction. Using Queryable is done under a transaction but transaction boundaries are
 * handled by [Database]. Use [Transaction] obtained via [Database.transaction] to control
 * transaction commit/rollback.
 */
@WeLiteMarker
public interface Queryable {
  /**
   * Ensures that current coroutine scope is active. If the job is no longer active, throws
   * CancellationException. If the job was cancelled, thrown exception contains the original
   * cancellation cause. This function does not do anything if there is no Job in the scope's
   * coroutineContext. This method is a drop-in replacement for the following code, but with more
   * precise exception:
   * ```
   * if (!isActive) {
   *   throw CancellationException()
   * }
   * ```
   * Transactions created by the WeLite framework contain a [kotlinx.coroutines.CoroutineScope] to
   * facilitate cooperative coroutine cancellation. A [TransactionInProgress] created via the
   * [Database.ongoingTransaction] may not contain a coroutine scope unless supplied by the user. If
   * no coroutine scope is present this function is effectively a no-op
   */
  public fun ensureActive()

  /**
   * Returns true when the current coroutine scope is still active (has not completed and was not
   * cancelled yet). Coroutine cancellation is cooperative, so this Boolean should be regularly
   * checked or [ensureActive] should be called in long running loops or between expensive DB calls.
   *
   * Transactions created by the WeLite framework contain a [kotlinx.coroutines.CoroutineScope] to
   * facilitate cooperative coroutine cancellation. A [TransactionInProgress] created via the
   * [Database.ongoingTransaction] may not contain a coroutine scope unless supplied by the user. If
   * no coroutine scope is present this value will always return true.
   */
  public val isActive: Boolean

  /**
   * Do any necessary [bind], execute the query, and invoke [action] for each row in the
   * results, and return the number of rows reported by [Cursor.count] which would be zero in the
   * case of no results returned by the query.
   */
  public fun <C : ColumnSet> Query<C>.forEach(
    bind: C.(ArgBindings) -> Unit = { },
    action: C.(Cursor) -> Unit
  ): Int

  /**
   * Same as [Query.forEach] except the resulting Query is not reusable as it's not visible to the
   * client. Returns the number of rows reported by [Cursor.count] which would be zero in the case
   * of no results returned by the query.
   */
  public fun <C : ColumnSet> QueryBuilder<C>.forEach(
    bind: C.(ArgBindings) -> Unit = { },
    action: C.(Cursor) -> Unit
  ): Int

  /**
   * After any necessary [bind] generate a [Sequence] of [T] using [factory] for each Cursor
   * row and yields a [T] into the [Sequence]
   */
  public fun <C : ColumnSet, T> Query<C>.sequence(
    bind: C.(ArgBindings) -> Unit = { },
    factory: C.(Cursor) -> T
  ): Sequence<T>

  /**
   * Same as [Query.sequence] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <C : ColumnSet, T> QueryBuilder<C>.sequence(
    bind: C.(ArgBindings) -> Unit = { },
    factory: C.(Cursor) -> T
  ): Sequence<T>

  /**
   * Do any necessary [bind], execute the query, and return the value in the first column of the
   * first row. Especially useful for ```COUNT``` queries
   */
  public fun <C : ColumnSet> Query<C>.longForQuery(
    bind: C.(ArgBindings) -> Unit = { }
  ): Long

  /**
   * Same as [Query.longForQuery] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <C : ColumnSet> QueryBuilder<C>.longForQuery(
    bind: C.(ArgBindings) -> Unit = { }
  ): Long

  /**
   * Do any necessary [bind], execute the query, and return the value in the first column of the
   * first row.
   */
  public fun <C : ColumnSet> Query<C>.stringForQuery(bind: C.(ArgBindings) -> Unit = { }): String

  /**
   * Same as [Query.stringForQuery] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <C : ColumnSet> QueryBuilder<C>.stringForQuery(
    bind: C.(ArgBindings) -> Unit = { }
  ): String

  /**
   * Do any necessary [bind], execute the query for count similar to
   * ```COUNT(*) FROM ( $thisQuery )```, and return the value in the first column of the
   * first row
   */
  public fun <C : ColumnSet> Query<C>.count(bind: C.(ArgBindings) -> Unit = { }): Long

  /**
   * Same as [Query.count] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <C : ColumnSet> QueryBuilder<C>.count(bind: C.(ArgBindings) -> Unit = { }): Long

  /**
   * True if the Creatable, as known via [Creatable.identity], exists in the database, else false.
   * Examples of Creatable implementations are Table, Index, View, Trigger
   */
  public val Creatable.exists: Boolean

  /**
   * The description of the table in the database
   * @throws IllegalArgumentException if this [Table] does not exist in the database
   * @see [Table.exists]
   */
  public val Table.description: TableDescription

  /**
   * Full create SQL of this [Table] as stored by SQLite. Throws [IllegalArgumentException] if
   * this [Table] does not exist in the database.
   * @see [Table.exists]
   */
  public val Creatable.sql: SchemaSql

  /**
   * Does an integrity check of the entire database. Looks for out-of-order records, missing pages,
   * malformed records, missing index entries, and UNIQUE, CHECK, and NOT NULL constraint errors.
   * Returns a list of strings describing any problems found. Will return at
   * most [maxErrors] errors (default 100) before the analysis quits. If integrity check
   * finds no errors, a single string with the value 'ok' is returned.
   *
   * Does not find FOREIGN KEY errors. Use [foreignKeyCheck] to find errors in FOREIGN
   * KEY constraints.
   */
  public fun tegridyCheck(maxErrors: Int = 100): List<String>

  /**
   * Returns a [ForeignKeyInfo] for each foreign key constraint created by a REFERENCES clause in
   * the CREATE TABLE statement of this [Table]. Requires api [Build.VERSION_CODES.O] and higher
   */
  public val Table.foreignKeyList: List<ForeignKeyInfo>
    @RequiresApi(Build.VERSION_CODES.O) get

  /**
   * Checks this table for failing foreign key constraints. Returns list containing a
   * [ForeignKeyViolation] for each violation or an empty list if none. The only
   * foreign key constraints checked are those created by REFERENCES clauses in the CREATE TABLE
   * statement for this [Table]. Requires api [Build.VERSION_CODES.O] and higher
   */
  @RequiresApi(Build.VERSION_CODES.O)
  public fun Table.foreignKeyCheck(): List<ForeignKeyViolation>

  /**
   * Returns the version string for the SQLite library that is running
   */
  public val sqliteVersion: String
}
