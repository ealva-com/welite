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

package com.ealva.welite.db.table

import com.ealva.welite.db.type.BasePersistentType
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

public fun Table.duration(
  name: String,
  durationUnit: DurationUnit,
  block: ColumnConstraints<Duration>.() -> Unit = {}
): Column<Duration> = registerColumn(name, DurationAsLongType(durationUnit), block)

public fun Table.optDuration(
  name: String,
  durationUnit: DurationUnit,
  block: ColumnConstraints<Duration?>.() -> Unit = {}
): Column<Duration?> = registerOptColumn(name, DurationAsLongType(durationUnit), block)

private fun Any.valueToDuration(durationUnit: DurationUnit): Duration = when (this) {
  is Duration -> this
  is Long -> toDuration(durationUnit)
  is String -> Duration.parse(this)
  else -> Duration.parse(toString())
}

public class DurationAsLongType<T : Duration?>(
  private val longColumn: LongPersistentType<Long?>,
  private val durationUnit: DurationUnit
) : BasePersistentType<T, Long>(longColumn.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    longColumn.bind(bindable, index, value.valueToDuration(durationUnit).toLong(durationUnit))

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index).toDuration(durationUnit) as T

  override fun notNullValueToDB(value: Any): Long =
    value.valueToDuration(durationUnit).toLong(durationUnit)

  override fun clone(): PersistentType<T?> = DurationAsLongType(durationUnit)

  public companion object {
    public operator fun <T : Duration?> invoke(durationUnit: DurationUnit): DurationAsLongType<T> =
      DurationAsLongType(LongPersistentType(), durationUnit)
  }
}
