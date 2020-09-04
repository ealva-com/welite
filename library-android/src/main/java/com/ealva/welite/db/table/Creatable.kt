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
 * Denotes implementations can be created and dropped. Creates are always IF NOT EXISTS
 */
interface Creatable {
  /**
   * Ask this object to create itself in the database. This may result in multiple statements
   * being executed. If [temporary] is not supported, such as with Index, it is ignored.
   */
  fun create(executor: SqlExecutor, temporary: Boolean = false)

  /**
   * Ask this object to drop itself from the database. This may result in multiple statements
   * being executed (typically a single DROP)
   */
  fun drop(executor: SqlExecutor)
}
