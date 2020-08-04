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

import com.ealva.welite.db.expr.SqlTypeExpression

interface Cursor {
  /**
   * Total number of rows returned
   */
  val count: Int

  /**
   * Current position of the cursor
   */
  val position: Int

  /**
   * Get value [T], which is possibly null, of the column [expression] at the current
   * cursor [position]
   */
  fun <T : Any> getOptional(expression: SqlTypeExpression<T>): T?

  /**
   * Get value [T] of the column [expression] at the current cursor [position]
   * @throws IllegalStateException if the value in the DB is null
   */
  operator fun <T : Any> get(expression: SqlTypeExpression<T>): T

  /**
   * Get value [T] of the column [expression] at the current cursor [position] or return
   * [defaultValue] if the value in the DB is null
   */
  operator fun <T : Any> get(expression: SqlTypeExpression<T>, defaultValue: T): T =
    getOptional(expression) ?: defaultValue
}
