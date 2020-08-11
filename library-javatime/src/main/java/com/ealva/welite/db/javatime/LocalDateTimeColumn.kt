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

import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.ealvalog.w
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

private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

fun Table.localDateTime(
  name: String,
  block: SetConstraints<LocalDateTime> = {}
): Column<LocalDateTime> = registerColumn(name, LocalDateTimeType(), block)

fun Table.optLocalDateTime(
  name: String,
  block: SetConstraints<LocalDateTime?> = {}
): Column<LocalDateTime?> = registerOptColumn(name, NullableLocalDateTimeType(), block)

private val LOG by lazyLogger(NullableLocalDateTimeType::class)

private const val CONVERT_WARNING_MSG =
  "Converting LocalDate to LocalDateDate.atStartOfDay may lead to unexpected errors." +
    " Prefer LocalDateTime for this column."

open class NullableLocalDateTimeType<T : LocalDateTime?>(
  private val textColumn: NullableStringPersistentType<String?>
) : BasePersistentType<T>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is LocalDateTime -> textColumn.bind(bindable, index, formatter.format(value))
      is LocalDate -> {
        LOG.w { +it(CONVERT_WARNING_MSG) }
        value.atStartOfDay()
      }
      is String -> textColumn.bind(bindable, index, value)
      else -> value.cannotConvert("LocalDateTime")
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = LocalDateTime.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): Any {
    return valueToLocalDateTime(value)
  }

  override fun nonNullValueToString(value: Any): String = "'${valueToLocalDateTime(value)}'"

  private fun valueToLocalDateTime(value: Any): LocalDateTime = when (value) {
    is LocalDateTime -> value
    is LocalDate -> value.atStartOfDay()
    is String -> LocalDateTime.parse(value)
    else -> LocalDateTime.parse(value.toString())
  }

  companion object {
    operator fun <T : LocalDateTime?> invoke(): NullableLocalDateTimeType<T> =
      NullableLocalDateTimeType(NullableStringPersistentType())
  }

  override fun clone(): PersistentType<T?> {
    return NullableLocalDateTimeType()
  }
}

class LocalDateTimeType : NullableLocalDateTimeType<LocalDateTime>(NullableStringPersistentType())
