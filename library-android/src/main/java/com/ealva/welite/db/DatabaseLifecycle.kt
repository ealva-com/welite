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

import java.sql.SQLException
import java.util.Locale

interface DatabaseLifecycle {
  /**
   * Call [block] before the database is opened and pass it [PreOpenParams] to configure
   * open parameters.
   */
  fun preOpen(block: (PreOpenParams) -> Unit)

  /**
   * After the database is open call [block] with a [DatabaseConfiguration] to allow the
   * database connection to be configured
   */
  fun onConfigure(block: (DatabaseConfiguration) -> Unit)

  /**
   * After the database has been opened, configured, all tables created, and all table post
   * processing, call [block] with the fully created Database instance
   */
  fun onCreate(block: (Database) -> Unit)

  /**
   * After the database has been opened, configured, and either created or migrated, call [block]
   * with the fully configured Database instance
   */
  fun onOpen(block: (Database) -> Unit)
}

interface PreOpenParams {
  var allowWorkOnUiThread: Boolean

  fun enableWriteAheadLogging(enable: Boolean)

  /** No-op if Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 */
  fun setLookasideConfig(slotSize: Int, slotCount: Int)
}

interface DatabaseConfiguration {
  /**
   * Sets whether foreign key constraints are enabled for the database.
   * <p>
   * By default, foreign key constraints are not enforced by the database.
   * This method allows an application to enable foreign key constraints.
   * It must be called each time the database is opened to ensure that foreign
   * key constraints are enabled for the session.
   * </p><p>
   * When foreign key constraints are disabled, the database does not check whether
   * changes to the database will violate foreign key constraints.  Likewise, when
   * foreign key constraints are disabled, the database will not execute cascade
   * delete or update triggers.  As a result, it is possible for the database
   * state to become inconsistent.
   * </p><p>
   * This method must not be called while a transaction is in progress.
   * </p><p>
   * See also <a href="http://sqlite.org/foreignkeys.html">SQLite Foreign Key Constraints</a>
   * for more details about foreign key constraint support.
   * </p>
   *
   * @param enable True to enable foreign key constraints, false to disable them.
   *
   * @throws IllegalStateException if the are transactions is in progress
   * when this method is called.
   */
  fun enableForeignKeyConstraints(enable: Boolean)

  /**
   * Sets the locale for this database.  Does nothing if this database has
   * the {@link #NO_LOCALIZED_COLLATORS} flag set or was opened read only.
   *
   * @param locale The new locale.
   *
   * @throws SQLException if the locale could not be set.  The most common reason
   * for this is that there is no collator available for the locale you requested.
   * In this case the database remains unchanged.
   */
  fun setLocale(locale: Locale)

  /**
   * the maximum size the database will grow to. The maximum size cannot
   * be set below the current size.
   */
  var maximumSize: Long

  /**
   * "PRAGMA " is prepended to [statement] and then it's executed as SQL. For example:
   * ```
   * execPragma("synchronous=NORMAL")
   * ```
   * results in [android.database.sqlite.SQLiteDatabase.execSQL] ("PRAGMA synchronous=NORMAL")
   * @throws SQLException if the SQL string is invalid
   */
  @Throws(SQLException::class)
  fun execPragma(statement: String)
}
