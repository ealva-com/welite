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

import com.ealva.welite.db.table.ForeignKeyAction

data class ForeignKeyInfo(
  val id: Long,
  val seq: Long,
  val table: String,
  val from: String,
  val to: String,
  val onUpdate: ForeignKeyAction,
  val onDelete: ForeignKeyAction
  // val match: String not currently supported in SQLite
)

/**
 * Returned from [Transaction.foreignKeyCheck]
 */
data class ForeignKeyViolation(
  /** The name of the table that contains the REFERENCES clause */
  val tableName: String,
  /**
   * The row id of the row that contains the invalid REFERENCES clause, or -1 if the child table is
   * a WITHOUT ROWID table.
   */
  val rowId: Long,
  /**
   * The name of the table that is referred to
   */
  val refersTo: String,
  /**
   * The index of the specific foreign key constraint that failed. This is the same integer as the
   * first column in the output of [Transaction.foreignKeyList]
   */
  val failingConstraintIndex: Long
)

