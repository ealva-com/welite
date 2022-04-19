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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

public fun Table.offsetDateTimeText(
  name: String,
  block: ColumnConstraints<OffsetDateTime>.() -> Unit = {}
): Column<OffsetDateTime> = registerColumn(name, OffsetDateTimeAsTextType(), block)

public fun Table.optOffsetDateTimeText(
  name: String,
  block: ColumnConstraints<OffsetDateTime?>.() -> Unit = {}
): Column<OffsetDateTime?> = registerOptColumn(name, OffsetDateTimeAsTextType(), block)

private fun Any.valueToOffsetDateTime(): OffsetDateTime = when (this) {
  is OffsetDateTime -> this
  is LocalDateTime -> atOffset(ZoneOffset.UTC)
  is String -> OffsetDateTime.parse(this)
  else -> OffsetDateTime.parse(toString())
}

/**
 * Text form nicely compares in the database
 */
public open class OffsetDateTimeAsTextType<T : OffsetDateTime?>(
  private val textColumn: StringPersistentType<String?>
) : BasePersistentType<T, String>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    textColumn.bind(bindable, index, value.valueToOffsetDateTime().toString())
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = OffsetDateTime.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): String = value.valueToOffsetDateTime().toString()

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'${value.valueToOffsetDateTime()}'" else "${value.valueToOffsetDateTime()}"

  public companion object {
    public operator fun <T : OffsetDateTime?> invoke(): OffsetDateTimeAsTextType<T> =
      OffsetDateTimeAsTextType(StringPersistentType())
  }

  override fun clone(): PersistentType<T?> = OffsetDateTimeAsTextType()
}
