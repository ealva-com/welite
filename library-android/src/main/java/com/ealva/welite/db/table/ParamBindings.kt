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
 * ParamBindings is the interface presented to the client to bind parameters to the
 * sql statement.
 */
interface ParamBindings {
  /**
   * Set the bind parameter at [index] to [value]. The underlying persistent type object handles any
   * necessary conversions of value or throws on error
   */
  operator fun set(index: Int, value: Any?)

  /**
   * Total number of bindable parameters
   */
  val paramCount: Int
}

val NO_BIND: (ParamBindings) -> Unit = {}