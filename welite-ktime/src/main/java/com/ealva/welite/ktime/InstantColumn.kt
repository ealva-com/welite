/*
 * Copyright 2022 eAlva.com
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

package com.ealva.welite.ktime

import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnConstraints
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.BasePersistentType
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import com.ealva.welite.db.type.StringPersistentType
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

public fun Table.instantText(
  name: String,
  block: ColumnConstraints<Instant>.() -> Unit = {}
): Column<Instant> = registerColumn(name, InstantAsTextType(), block)

public fun Table.optInstantText(
  name: String,
  block: ColumnConstraints<Instant?>.() -> Unit = {}
): Column<Instant?> = registerOptColumn(name, InstantAsTextType(), block)

public fun Table.instantEpochMillis(
  name: String,
  block: ColumnConstraints<Instant>.() -> Unit = {}
): Column<Instant> = registerColumn(name, InstantAsLongType(), block)

public fun Table.optInstantEpochMillis(
  name: String,
  block: ColumnConstraints<Instant?>.() -> Unit = {}
): Column<Instant?> = registerOptColumn(name, InstantAsLongType(), block)

private fun Any.valueToInstant(): Instant = when (this) {
  is Instant -> this
  is Long -> Instant.fromEpochMilliseconds(this)
  is Number -> Instant.fromEpochMilliseconds(this.toLong())
  is String -> toInstant()
  else -> toString().toInstant()
}

public class InstantAsTextType<T : Instant?>(
  private val textColumn: StringPersistentType<String?>
) : BasePersistentType<T, String>(textColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    textColumn.bind(bindable, index, value.valueToInstant().toString())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = Instant.parse(getString(index)) as T

  override fun notNullValueToDB(value: Any): String = value.valueToInstant().toString()

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    notNullValueToDB(value).maybeQuote(quoteAsLiteral)

  override fun clone(): PersistentType<T?> {
    return InstantAsTextType()
  }

  public companion object {
    public operator fun <T : Instant?> invoke(): InstantAsTextType<T> =
      InstantAsTextType(StringPersistentType())
  }
}

public class InstantAsLongType<T : Instant?>(
  private val longColumn: LongPersistentType<Long?>
) : BasePersistentType<T, Long>(longColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    longColumn.bind(bindable, index, value.valueToInstant().toEpochMilliseconds())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T =
    Instant.fromEpochMilliseconds(getLong(index)) as T

  override fun notNullValueToDB(value: Any): Long = value.valueToInstant().toEpochMilliseconds()

  override fun clone(): PersistentType<T?> {
    return InstantAsLongType()
  }

  public companion object {
    public operator fun <T : Instant?> invoke(): InstantAsLongType<T> =
      InstantAsLongType(LongPersistentType())
  }
}
