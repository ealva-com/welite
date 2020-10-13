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
public interface Row {
  public fun getBlob(columnIndex: Int): ByteArray
  public fun getString(columnIndex: Int): String
  public fun getShort(columnIndex: Int): Short
  public fun getInt(columnIndex: Int): Int
  public fun getLong(columnIndex: Int): Long
  public fun getFloat(columnIndex: Int): Float
  public fun getDouble(columnIndex: Int): Double
  public fun isNull(columnIndex: Int): Boolean
  public fun columnName(columnIndex: Int): String
}
