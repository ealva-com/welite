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

package com.ealva.welite.db.type

/**
 * Represents a row in a query result
 */
interface Row {
  fun getBlob(columnIndex: Int): ByteArray
  fun getString(columnIndex: Int): String
  fun getShort(columnIndex: Int): Short
  fun getInt(columnIndex: Int): Int
  fun getLong(columnIndex: Int): Long
  fun getFloat(columnIndex: Int): Float
  fun getDouble(columnIndex: Int): Double
  fun isNull(columnIndex: Int): Boolean
  fun columnName(columnIndex: Int): String
}
