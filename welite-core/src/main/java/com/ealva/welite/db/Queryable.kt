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
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.NO_ARGS
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDescription
import com.ealva.welite.db.table.WeLiteMarker
import kotlinx.coroutines.flow.Flow

/**
 * These functions are scoped to a Queryable interface in an attempt to only initiate queries while
 * a transaction is in progress. See [Database.query] for starting a query only under an explicit
 * transaction (with no commit/rollback concerns).
 */
@WeLiteMarker
public interface Queryable {
  /**
   * Do any necessary [bind], execute the query, and invoke [action] for each row in the
   * results.
   */
  public fun Query.forEach(bind: (ArgBindings) -> Unit = NO_ARGS, action: (Cursor) -> Unit)

  /**
   * Same as [Query.forEach] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun QueryBuilder.forEach(bind: (ArgBindings) -> Unit = NO_ARGS, action: (Cursor) -> Unit)

  /**
   * Creates a flow, first doing any necessary [bind], execute the query, and emit a [T]
   * created using [factory] for each row in the query results
   */
  public fun <T> Query.flow(bind: (ArgBindings) -> Unit = NO_ARGS, factory: (Cursor) -> T): Flow<T>

  /**
   * Same as [Query.flow] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <T> QueryBuilder.flow(
    bind: (ArgBindings) -> Unit = NO_ARGS,
    factory: (Cursor) -> T
  ): Flow<T>

  /**
   * After any necessary [bind] generate a [Sequence] of [T] using [factory] for each Cursor
   * row and yields a [T] into the [Sequence]
   */
  public fun <T> Query.sequence(
    bind: (ArgBindings) -> Unit = NO_ARGS,
    factory: (Cursor) -> T
  ): Sequence<T>

  /**
   * Same as [Query.sequence] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun <T> QueryBuilder.sequence(
    bind: (ArgBindings) -> Unit = NO_ARGS,
    factory: (Cursor) -> T
  ): Sequence<T>

  /**
   * Do any necessary [bind], execute the query, and return the value in the first column of the
   * first row. Especially useful for ```COUNT``` queries
   */
  public fun Query.longForQuery(bind: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Same as [Query.longForQuery] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun QueryBuilder.longForQuery(bind: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Do any necessary [bind], execute the query, and return the value in the first column of the
   * first row.
   */
  public fun Query.stringForQuery(bind: (ArgBindings) -> Unit = NO_ARGS): String

  /**
   * Same as [Query.stringForQuery] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun QueryBuilder.stringForQuery(bind: (ArgBindings) -> Unit = NO_ARGS): String

  /**
   * Do any necessary [bind], execute the query for count similar to
   * ```COUNT(*) FROM ( $thisQuery )```, and return the value in the first column of the
   * first row
   */
  public fun Query.count(bind: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Same as [Query.count] except the resulting Query is not reusable as it's not
   * visible to the client
   */
  public fun QueryBuilder.count(bind: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * True if the Creatable, as known via [Creatable.identity], exists in the database, else false.
   * Creatable implementations are Table, Index, View, Trigger
   */
  public val Creatable.exists: Boolean

  /**
   * The description of the table in the database
   * @throws IllegalArgumentException if this [Table] does not exist in the database
   * @see [Table.exists]
   */
  public val Table.description: TableDescription

  /**
   * Full create sql of this [Table] as stored by SQLite. Throws [IllegalArgumentException] if
   * this [Table] does not exist in the database.
   * @see [Table.exists]
   */
  public val Table.sql: TableSql

  /**
   * Does an integrity check of the entire database. Looks for out-of-order records, missing pages,
   * malformed records, missing index entries, and UNIQUE, CHECK, and NOT NULL constraint errors.
   * Returns a list of strings describe any problems found. Will return at
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
