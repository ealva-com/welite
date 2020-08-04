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

package com.ealva.welite.db.expr

import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.PersistentType

interface SqlTypeExpression<T> : Expression<T> {
  val sqlType: String
  val persistentType: PersistentType<T?>
  fun bind(bindable: Bindable, index: Int, value: T?)
}

abstract class BaseSqlTypeExpression<T> : BaseExpression<T>(), SqlTypeExpression<T> {
  override val sqlType: String
    get() = persistentType.sqlType

  override fun bind(bindable: Bindable, index: Int, value: T?) {
    persistentType.bind(bindable, index, value)
  }
}