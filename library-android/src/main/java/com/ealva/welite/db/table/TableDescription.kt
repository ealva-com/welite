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

data class TableDescription(val tableName: String, val columnsMetadata: List<ColumnMetadata>)

enum class FieldType(val type: Int, val display: String) {
  @Suppress("unused")
  NullField(android.database.Cursor.FIELD_TYPE_NULL, "NULL"),
  IntegerField(android.database.Cursor.FIELD_TYPE_INTEGER, "INTEGER"),
  RealField(android.database.Cursor.FIELD_TYPE_FLOAT, "REAL"),
  TextField(android.database.Cursor.FIELD_TYPE_STRING, "TEXT"),
  BlobField(android.database.Cursor.FIELD_TYPE_BLOB, "BLOB"),
  UnknownField(Int.MAX_VALUE, "UNKNOWN");

  companion object {
    private val allValues = values()

    fun fromType(type: String, defaultValue: FieldType = UnknownField): FieldType =
      allValues.find { it.display == type } ?: defaultValue

    fun fromInt(type: Int, defaultValue: FieldType = UnknownField): FieldType =
      allValues.find { it.type == type } ?: defaultValue
  }
}

data class ColumnMetadata(
  val id: Int,
  val name: String,
  val type: FieldType,
  val nullable: Boolean,
  val defVal: String,
  val pk: Int
)
