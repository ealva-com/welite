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

/**
 * [Syntax](https://sqlite.org/images/syntax/conflict-clause.gif)
 *
 * The ON CONFLICT clause is a non-standard extension specific to SQLite that can appear in many
 * other SQL commands. It is given its own section in this document because it is not part of
 * standard SQL and therefore might not be familiar.
 *
 * The ON CONFLICT clause described here has been a part of SQLite since before version 3.0.0
 * (2004-06-18). The phrase "ON CONFLICT" is also part of UPSERT, which is an extension to INSERT
 * added in version 3.24.0 (2018-06-04). Do not confuse these two separate uses of the "ON CONFLICT"
 * phrase.
 *
 * The syntax for the ON CONFLICT clause is as shown above for the CREATE TABLE command. For the
 * INSERT and UPDATE commands, the keywords "ON CONFLICT" are replaced by "OR" so that the syntax
 * reads more naturally. For example, instead of "INSERT ON CONFLICT IGNORE" we have "INSERT OR
 * IGNORE". The keywords change but the meaning of the clause is the same either way.
 *
 * The ON CONFLICT clause applies to UNIQUE, NOT NULL, CHECK, and PRIMARY KEY constraints. The ON
 * CONFLICT algorithm does not apply to FOREIGN KEY constraints. There are five conflict resolution
 * algorithm choices: ROLLBACK, ABORT, FAIL, IGNORE, and REPLACE. The default conflict resolution
 * algorithm is ABORT.
 *
 * [SQLite ON CONFLICT](https://sqlite.org/lang_conflict.html)
 */
@Suppress("unused")
enum class OnConflict(private val onConflict: String, val insertOr: String, val updateOr: String) {
  /**
   * When an applicable constraint violation occurs, the ROLLBACK resolution algorithm aborts the
   * current SQL statement with an SQLITE_CONSTRAINT error and rolls back the current transaction.
   * If no transaction is active (other than the implied transaction that is created on every
   * command) then the ROLLBACK resolution algorithm works the same as the ABORT algorithm.
   */
  Rollback("ON CONFLICT ROLLBACK", "INSERT OR ROLLBACK", "UPDATE OR ROLLBACK "),

  /**
   * When an applicable constraint violation occurs, the ABORT resolution algorithm aborts the
   * current SQL statement with an SQLITE_CONSTRAINT error and backs out any changes made by the
   * current SQL statement; but changes caused by prior SQL statements within the same transaction
   * are preserved and the transaction remains active. This is the default behavior and the behavior
   * specified by the SQL standard.
   */
  Abort("ON CONFLICT ABORT", "INSERT OR ABORT", "UPDATE OR ABORT "),

  /**
   * When an applicable constraint violation occurs, the FAIL resolution algorithm aborts the
   * current SQL statement with an SQLITE_CONSTRAINT error. But the FAIL resolution does not back
   * out prior changes of the SQL statement that failed nor does it end the transaction. For
   * example, if an UPDATE statement encountered a constraint violation on the 100th row that it
   * attempts to update, then the first 99 row changes are preserved but changes to rows 100 and
   * beyond never occur.
   *
   * The FAIL behavior only works for uniqueness, NOT NULL, and CHECK constraints. A foreign key
   * constraint violation causes an ABORT.
   */
  Fail("ON CONFLICT FAIL", "INSERT OR FAIL", "UPDATE OR FAIL "),

  /**
   * When an applicable constraint violation occurs, the IGNORE resolution algorithm skips the one
   * row that contains the constraint violation and continues processing subsequent rows of the SQL
   * statement as if nothing went wrong. Other rows before and after the row that contained the
   * constraint violation are inserted or updated normally. No error is returned for uniqueness, NOT
   * NULL, and UNIQUE constraint errors when the IGNORE conflict resolution algorithm is used.
   * However, the IGNORE conflict resolution algorithm works like ABORT for foreign key constraint
   * errors.
   */
  Ignore("ON CONFLICT IGNORE", "INSERT OR IGNORE", "UPDATE OR IGNORE "),

  /**
   * When a UNIQUE or PRIMARY KEY constraint violation occurs, the REPLACE algorithm deletes
   * pre-existing rows that are causing the constraint violation prior to inserting or updating the
   * current row and the command continues executing normally. If a NOT NULL constraint violation
   * occurs, the REPLACE conflict resolution replaces the NULL value with the default value for that
   * column, or if the column has no default value, then the ABORT algorithm is used. If a CHECK
   * constraint or foreign key constraint violation occurs, the REPLACE conflict resolution
   * algorithm works like ABORT.
   *
   * When the REPLACE conflict resolution strategy deletes rows in order to satisfy a constraint,
   * delete triggers fire if and only if recursive triggers are enabled.
   *
   * The update hook is not invoked for rows that are deleted by the REPLACE conflict resolution
   * strategy. Nor does REPLACE increment the change counter. The exceptional behaviors defined in
   * this paragraph might change in a future release.
   */
  Replace("ON CONFLICT REPLACE", "INSERT OR REPLACE", "UPDATE OR REPLACE "),

  /**
   * Typically this is the WeLite default and specifies no "ON CONFLICT" or no INSERT/UPDATE
   * "OR" clause
   */
  Unspecified("", "INSERT", "UPDATE ");

  override fun toString() = onConflict
}
