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

import java.sql.SQLException
import java.util.Locale

public interface DatabaseLifecycle {
  /**
   * After the database is open [block] is called with a [DatabaseConfiguration] to allow the
   * database connection to be configured
   */
  public fun onConfigure(block: (DatabaseConfiguration) -> Unit)

  /**
   * After the database has been opened, configured, all tables created, and all table post
   * processing, call [block] with the fully created Database instance with the receiver being a
   * TransactionInProgress. This function is only called if the database tables needed to be created
   * (first app run or DB file deleted).
   */
  public fun onCreate(block: suspend TransactionInProgress.(Database) -> Unit)

  /**
   * After the database has been opened, configured, and either created or migrated, as
   * necessary, call [block] with the fully configured Database instance with a
   * TransactionInProgress as the receiver. The open transaction is separate from the txn used
   * during [onCreate]. If an exception is thrown from [block] all db changes made in the scope of
   * [block] are rolled back.
   */
  public fun onOpen(block: suspend TransactionInProgress.(Database) -> Unit)

  /**
   * Call [block] when database corruption is detected. [useDefaultHandler] indicates if default
   * error handling should occur before calling [block]. The default
   * behavior is to close the Database, if open, and delete the database file
   */
  public fun onCorruption(useDefaultHandler: Boolean, block: (Database) -> Unit = {})
}

public interface PragmaExec {
  /**
   * "PRAGMA " is prepended to [statement] and then it's executed as SQL.
   * @throws SQLException if the SQL string is invalid
   */
  public fun execPragma(statement: String)

  /**
   * Execute the pragma as a query which returns a single row, single column, string
   */
  public fun queryPragmaString(statement: String): String

  /**
   * Execute the pragma as a query which returns a single row, single column, long
   */
  public fun queryPragmaLong(statement: String): Long
}

/**
 * If [JournalMode.UNKNOWN] is returned, an error occurred. If [JournalMode.UNKNOWN] is set an
 * IllegalArgumentException is thrown
 */
public var PragmaExec.journalMode: JournalMode
  get() = queryPragmaString("journal_mode").toJournalMode()
  set(value) {
    require(value != JournalMode.UNKNOWN) {
      "${JournalMode.UNKNOWN} indicates an error. Do not set."
    }
    queryPragmaString("journal_mode=$value")
  }

/**
 * If [SynchronousMode.UNKNOWN] is returned, an error occurred. If [SynchronousMode.UNKNOWN] is
 * set an IllegalArgumentException is thrown
 */
public var PragmaExec.synchronousMode: SynchronousMode
  get() = queryPragmaLong("synchronous").toSynchronousMode()
  set(value) {
    require(value != SynchronousMode.UNKNOWN) {
      "${SynchronousMode.UNKNOWN} indicates an error. Do not set."
    }
    execPragma("synchronous=$value")
  }

/**
 * Provides the interface to configure the database connection. Do not retain a reference to this
 * and call it's functions later.
 */
public interface DatabaseConfiguration : PragmaExec {
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
  public fun setLocale(locale: Locale)

  /**
   * the maximum size the database will grow to. The maximum size cannot
   * be set below the current size.
   */
  public var maximumSize: Long
}
