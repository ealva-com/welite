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

package com.ealva.welite.db.table

import com.ealva.welite.db.expr.SqlTypeExpression

public interface Cursor {
  /**
   * Total number of rows returned
   */
  public val count: Int

  /**
   * Current position of the cursor
   */
  public val position: Int

  /**
   * Number of columns in a row
   */
  public val columnCount: Int

  /**
   * Get value [T] of the column [expression] at the current cursor [position]. Typically accessed
   * as ```cursor[Table.column]```. Throws IllegalStateException if the value in the DB is null
   */
  public operator fun <T> get(expression: SqlTypeExpression<T>): T

  /**
   * Get value [T] of the column [expression] at the current cursor [position] or return
   * [valueIfNull] if the value in the DB is null. Typically accessed
   * as ```cursor[Table.column, valueIfNull]```
   */
  public operator fun <T> get(expression: SqlTypeExpression<T>, valueIfNull: T): T =
    getOptional(expression) ?: valueIfNull

  /**
   * Get value [T], which is possibly null, of the column [expression] at the current
   * cursor [position]. Prefer [get] with a default value for easier syntax of
   * ```cursor[Table.column, valueIfNull]``` and avoiding null.
   */
  public fun <T> getOptional(expression: SqlTypeExpression<T>): T?
}
