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
import com.ealva.welite.db.table.ParamBindings
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType

abstract class BaseStatement : Bindable, ParamBindings {

  protected abstract val statement: SQLiteStatement
  protected abstract val types: List<PersistentType<*>>

  override val paramCount: Int
    get() = types.size
  private val paramRange: IntRange
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

  override fun <T> set(index: Int, value: T?) {
    // PersistentType will call back on this to do the actual bind and the index should just be
    // passed through, so we won't check the index until one of the other bind functions are called
    if (value == null) bindNull(index + 1) else types[index].bind(this, index, value)
  }

  private fun ensureIndexInBounds(index: Int) {
    require(index in paramRange) { "Out of bounds index=$index indices=$paramRange" }
  }
}
