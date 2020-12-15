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

package com.ealva.welite.db.expr

import com.ealva.welite.db.type.AppendsToSqlBuilder
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.buildStr

public interface Expression<T> : AppendsToSqlBuilder {
  public fun asDefaultValue(): String
}

public abstract class BaseExpression<T> : Expression<T> {
  override fun asDefaultValue(): String = buildStr {
    append('(')
    appendTo(this)
    append(')')
  }

  override fun toString(): String = buildStr { appendTo(this) }

  public companion object {
    public inline fun <T, E : Expression<T>> build(builder: () -> E): E = builder()
  }
}

public fun <T> Iterable<T>.appendTo(
  builder: SqlBuilder,
  separator: CharSequence = ", ",
  prefix: CharSequence = "",
  postfix: CharSequence = "",
  append: SqlBuilder.(T) -> Unit
): SqlBuilder = builder.apply { appendEach(separator, prefix, postfix, append) }
