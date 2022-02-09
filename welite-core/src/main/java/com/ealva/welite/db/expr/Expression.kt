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

  /**
   * If this expression is an alias, an alias should be returned, otherwise returns self. This is
   * used when the column should be referenced as the alias and not the original column.
   */
  public fun aliasOrSelf(): Expression<T> = this

  /**
   * If this expression is an alias, return the original column, and if not an alias return self
   */
  public fun originalOrSelf(): Expression<T> = this
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

/**
 * This is basically a backdoor into WeLite's expression building. It requires knowing what SQL is
 * needed and is not really typesafe even though Expression requires a type. The only time I've
 * found it necessary to use is to use the strftime function which is not yet included in WeLite
 * design. There may be other times it's needed but it's suggested to find a supported method or
 * issue a pull request.
 *
 * Example:
 * ```
 * "(strftime('%s','now','${calc(units)} $unitsName') * 1000)".wrapAsExpression()
 * ```
 */
public fun <T : Any> String.wrapAsExpression(): BaseExpression<T> = object : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(this@wrapAsExpression)
  }
}
