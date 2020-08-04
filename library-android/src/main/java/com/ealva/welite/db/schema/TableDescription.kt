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

package com.ealva.welite.db.schema

import android.database.Cursor

@Suppress("unused")
enum class FieldType(val type: Int, val display: String) {
  NullField(Cursor.FIELD_TYPE_NULL, "NULL"),
  IntegerField(Cursor.FIELD_TYPE_INTEGER, "INTEGER"),
  RealField(Cursor.FIELD_TYPE_FLOAT, "REAL"),
  TextField(Cursor.FIELD_TYPE_STRING, "TEXT"),
  BlobField(Cursor.FIELD_TYPE_BLOB, "BLOB"),
  UnknownField(Int.MAX_VALUE, "UNKNOWN");

  companion object {
    private val allValues = values()

    fun fromType(type: String, defaultValue: FieldType = UnknownField): FieldType {
      return allValues.find { it.display == type } ?: defaultValue
    }

    fun fromInt(type: Int, defaultValue: FieldType = UnknownField): FieldType {
      return allValues.find { it.type == type } ?: defaultValue
    }
  }
}

fun Cursor.columnType(index: Int): FieldType {
  return FieldType.fromInt(getType(index))
}

data class TableDescription(val tableName: String, val columnsMetadata: List<ColumnMetadata>)

private const val ID_COLUMN = 0
private const val NAME_COLUMN = 1
private const val TYPE_COLUMN = 2
private const val NULLABLE_COLUMN = 3
private const val DEF_VAL_COLUMN = 4
private const val PK_COLUMN = 5
private const val NOT_NULLABLE = 1

data class ColumnMetadata(
  val id: Int,
  val name: String,
  val type: FieldType,
  val nullable: Boolean,
  val defVal: String,
  val pk: Int
) {
  companion object {
    fun fromCursor(cursor: Cursor): ColumnMetadata {
      return ColumnMetadata(
        cursor.getInt(ID_COLUMN),
        cursor.getString(NAME_COLUMN),
        FieldType.fromType(cursor.getString(TYPE_COLUMN)),
        cursor.getInt(NULLABLE_COLUMN) != NOT_NULLABLE, // 1 = not nullable
        if (cursor.isNull(DEF_VAL_COLUMN)) "NULL" else cursor.getString(DEF_VAL_COLUMN),
        cursor.getInt(PK_COLUMN)
      )
    }
  }
}