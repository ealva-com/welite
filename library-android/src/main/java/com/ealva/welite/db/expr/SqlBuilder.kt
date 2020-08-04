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

import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.type.DefaultValueMarker
import com.ealva.welite.db.type.PersistentType

class SqlBuilder {
  private val internalBuilder = StringBuilder()

  private val _types = mutableListOf<PersistentType<*>>()
  val types: List<PersistentType<*>>
    get() = _types.toList()

  fun <T> Iterable<T>.appendEach(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    append: SqlBuilder.(T) -> Unit
  ) {
    internalBuilder.append(prefix)
    forEachIndexed { index, element ->
      if (index > 0) internalBuilder.append(separator)
      append(element)
    }
    internalBuilder.append(postfix)
  }

  fun append(value: Char): SqlBuilder = apply { internalBuilder.append(value) }

  fun append(value: String): SqlBuilder = apply { internalBuilder.append(value) }

  fun append(value: Long): SqlBuilder = apply { internalBuilder.append(value) }

  fun append(value: Expression<*>): SqlBuilder = value.appendTo(this)

  fun <T> registerBindable(sqlType: PersistentType<T?>) {
    _types.add(sqlType)
    append("?")
  }

  fun <T> registerArgument(column: Column<T>, argument: T) {
    when (argument) {
      is Expression<*> -> append(argument)
      DefaultValueMarker -> column.dbDefaultValue?.let { append(it) } ?: append("NULL")
      else -> registerArgument(column.persistentType, argument)
    }
  }

  fun <T> registerArgument(sqlType: PersistentType<T?>, argument: T): Unit =
    registerArguments(sqlType, listOf(argument))

  fun <T> registerArguments(sqlType: PersistentType<T?>, arguments: Iterable<T>) {
    arguments.forEach { append(sqlType.valueToString(it)) }
  }

  override fun toString(): String = internalBuilder.toString()
}

fun SqlBuilder.append(vararg values: Any): SqlBuilder = apply {
  values.forEach { value ->
    when (value) {
      is Expression<*> -> append(value)
      is String -> append(value)
      is QueryBuilder -> value.appendTo(this)
      is Long -> append(value)
      is Char -> append(value)
      else -> append(value.toString())
    }
  }
}

inline operator fun SqlBuilder.invoke(body: SqlBuilder.() -> Unit) = apply {
  body()
}