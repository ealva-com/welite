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

@Suppress("unused")
enum class OnConflict(private val onConflict: String, val insertOr: String, val updateOr: String) {
  Rollback("ON CONFLICT ROLLBACK", "INSERT OR ROLLBACK", "UPDATE OR ROLLBACK "),
  Abort("ON CONFLICT ABORT", "INSERT OR ABORT", "UPDATE OR ABORT "),
  Fail("ON CONFLICT FAIL", "INSERT OR FAIL", "UPDATE OR FAIL "),
  Ignore("ON CONFLICT IGNORE", "INSERT OR IGNORE", "UPDATE OR IGNORE "),
  Replace("ON CONFLICT REPLACE", "INSERT OR REPLACE", "UPDATE OR REPLACE "),
  Unspecified("", "INSERT", "UPDATE ");

  override fun toString() = onConflict
}
