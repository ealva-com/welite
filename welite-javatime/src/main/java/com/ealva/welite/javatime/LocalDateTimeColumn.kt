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

package com.ealva.welite.javatime

import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnConstraints
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.BasePersistentType
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import com.ealva.welite.db.type.StringPersistentType
import java.time.LocalDate
import java.time.LocalDateTime

public fun Table.localDateTimeText(
  name: String,
  block: ColumnConstraints<LocalDateTime>.() -> Unit = {}
): Column<LocalDateTime> = registerColumn(name, LocalDateTimeAsTextType(), block)

public fun Table.optLocalDateTimeText(
  name: String,
  block: ColumnConstraints<LocalDateTime?>.() -> Unit = {}
): Column<LocalDateTime?> = registerOptColumn(name, LocalDateTimeAsTextType(), block)

private fun String.toLocalDateTime(): LocalDateTime = LocalDateTime.parse(this)

private fun Any.valueToLocalDateTime(): LocalDateTime = when (this) {
  is LocalDateTime -> this
  is LocalDate -> atStartOfDay()
  is String -> toLocalDateTime()
  else -> toString().toLocalDateTime()
}

/**
 * Text form nicely compares in the database
 */
public class LocalDateTimeAsTextType<T : LocalDateTime?>(
  private val textColumn: StringPersistentType<String?>
) : BasePersistentType<T, String>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    textColumn.bind(bindable, index, value.valueToLocalDateTime().toString())
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = LocalDateTime.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): String = value.valueToLocalDateTime().toString()

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'${value.valueToLocalDateTime()}'" else "${value.valueToLocalDateTime()}"

  public companion object {
    public operator fun <T : LocalDateTime?> invoke(): LocalDateTimeAsTextType<T> =
      LocalDateTimeAsTextType(StringPersistentType())
  }

  override fun clone(): PersistentType<T?> {
    return LocalDateTimeAsTextType()
  }
}
