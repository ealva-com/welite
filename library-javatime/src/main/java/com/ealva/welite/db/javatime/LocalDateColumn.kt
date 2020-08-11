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

package com.ealva.welite.db.javatime

import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.SetConstraints
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.BasePersistentType
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.NullableStringPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import com.ealva.welite.db.type.cannotConvert
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

fun Table.localDate(name: String, block: SetConstraints<LocalDate> = {}): Column<LocalDate> =
  registerColumn(name, LocalDateType(), block)

fun Table.optLocalDate(
  name: String,
  block: SetConstraints<LocalDate?> = {}
): Column<LocalDate?> = registerOptColumn(name, NullableLocalDateType(), block)

open class NullableLocalDateType<T : LocalDate?>(
  private val textColumn: NullableStringPersistentType<String?>
) : BasePersistentType<T>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is LocalDate -> textColumn.bind(bindable, index, formatter.format(value))
      is LocalDateTime -> textColumn.bind(bindable, index, formatter.format(value.toLocalDate()))
      is String -> textColumn.bind(bindable, index, value)
      else -> value.cannotConvert("LocalDate")
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = LocalDate.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): Any {
    return valueToLocalDate(value)
  }

  override fun nonNullValueToString(value: Any): String = "'${valueToLocalDate(value)}'"

  private fun valueToLocalDate(value: Any): LocalDate = when (value) {
    is LocalDate -> value
    is LocalDateTime -> value.toLocalDate()
    is String -> LocalDate.parse(value)
    else -> LocalDate.parse(value.toString())
  }

  companion object {
    operator fun <T : LocalDate?> invoke(): NullableLocalDateType<T> =
      NullableLocalDateType(NullableStringPersistentType())
  }

  override fun clone(): PersistentType<T?> {
    return NullableLocalDateType()
  }
}

class LocalDateType : NullableLocalDateType<LocalDate>(NullableStringPersistentType())
