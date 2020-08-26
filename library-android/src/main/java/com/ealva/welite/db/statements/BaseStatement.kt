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

package com.ealva.welite.db.statements

import android.database.sqlite.SQLiteStatement
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType

abstract class BaseStatement : Bindable, ArgBindings {

  protected abstract val statement: SQLiteStatement
  protected abstract val types: List<PersistentType<*>>

  override val argCount: Int
    get() = types.size
  private val argRange: IntRange
    get() = types.indices

  override fun bindNull(index: Int) {
    ensureIndexInBounds(index)
    statement.bindNull(index + 1)
  }

  override fun bind(index: Int, value: Long) {
    ensureIndexInBounds(index)
    statement.bindLong(index + 1, value)
  }

  override fun bind(index: Int, value: Double) {
    ensureIndexInBounds(index)
    statement.bindDouble(index + 1, value)
  }

  override fun bind(index: Int, value: String) {
    ensureIndexInBounds(index)
    statement.bindString(index + 1, value)
  }

  override fun bind(index: Int, value: ByteArray) {
    ensureIndexInBounds(index)
    statement.bindBlob(index + 1, value)
  }

  // PersistentType will call back on this to do the actual bind and the index should just be
  // passed through, so we won't check the index until one of the other bind functions are called.
  override fun <T> set(index: Int, value: T?) = types[index].bind(this, index, value)

  private fun ensureIndexInBounds(index: Int) {
    require(index in argRange) { "Out of bounds index=$index indices=$argRange" }
  }
}
