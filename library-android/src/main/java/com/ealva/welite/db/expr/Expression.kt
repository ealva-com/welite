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

interface Expression<T> {
  fun asDefaultValue(): String

  fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder
}

abstract class BaseExpression<T> : Expression<T> {
  private val _hashCode: Int by lazy { toString().hashCode() }

  override fun asDefaultValue(): String = "(${toString()})"

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Expression<*>) return false

    if (toString() != other.toString()) return false

    return true
  }

  override fun hashCode(): Int = _hashCode

  override fun toString(): String = SqlBuilder().append(this).toString()

  companion object {
    inline fun <T, E : Expression<T>> build(builder: ExpressionBuilder.() -> E): E =
      ExpressionBuilder.builder()
  }
}

fun <T> Iterable<T>.appendTo(
  builder: SqlBuilder,
  separator: CharSequence = ", ",
  prefix: CharSequence = "",
  postfix: CharSequence = "",
  append: SqlBuilder.(T) -> Unit
): SqlBuilder =
  builder.apply { append(separator, prefix, postfix, append) }

