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

public fun Table.localDate(
  name: String,
  block: ColumnConstraints<LocalDate>.() -> Unit = {}
): Column<LocalDate> = registerColumn(name, LocalDateAsTextType(), block)

public fun Table.optLocalDate(
  name: String,
  block: ColumnConstraints<LocalDate?>.() -> Unit = {}
): Column<LocalDate?> = registerOptColumn(name, LocalDateAsTextType(), block)

private fun String.toLocalDate(): LocalDate = LocalDate.parse(this)

private fun Any.valueToLocalDate(): LocalDate = when (this) {
  is LocalDate -> this
  is LocalDateTime -> toLocalDate()
  is String -> toLocalDate()
  else -> toString().toLocalDate()
}

/**
 * Text form nicely compares in the database
 */
public class LocalDateAsTextType<T : LocalDate?>(
  private val textColumn: StringPersistentType<String?>
) : BasePersistentType<T, String>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    textColumn.bind(bindable, index, value.valueToLocalDate().toString())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = LocalDate.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): String = value.valueToLocalDate().toString()

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'${value.valueToLocalDate()}'" else "${value.valueToLocalDate()}"

  override fun clone(): PersistentType<T?> {
    return LocalDateAsTextType()
  }

  public companion object {
    public operator fun <T : LocalDate?> invoke(): LocalDateAsTextType<T> =
      LocalDateAsTextType(StringPersistentType())
  }
}
