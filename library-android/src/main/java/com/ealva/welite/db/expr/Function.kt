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

@file:Suppress("unused")

package com.ealva.welite.db.expr

import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.StringPersistentType

abstract class Function<T>(override val persistentType: PersistentType<T>) :
  BaseSqlTypeExpression<T>()

open class CustomFunction<T>(
  private val functionName: String,
  persistentType: PersistentType<T>,
  private vararg val expr: Expression<*>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append(functionName)
      append('(')
      expr.toList().append { append(it) }
      append(')')
    }
}

open class CustomOperator<T>(
  private val operatorName: String,
  persistentType: PersistentType<T>,
  private val expr1: Expression<*>,
  private val expr2: Expression<*>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append('(')
      append(expr1)
      append(' ')
      append(operatorName)
      append(' ')
      append(expr2)
      append(')')
    }
}

class Random : Function<Long>(LongPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("RANDOM()")
    }
}

class LowerCase(
  private val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("LOWER(")
      append(expr)
      append(")")
    }
}

class UpperCase(
  private val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("UPPER(")
      append(expr)
      append(")")
    }
}

class Concat(
  private val separator: String,
  private vararg val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      expr.toList().appendTo(
        builder = this,
        separator = if (separator.isEmpty()) " || " else " || '$separator' || "
      ) { append(it) }

      if (separator.isEmpty()) {
        expr.toList().appendTo(this, separator = " || ") { append(it) }
      } else {
        expr.toList().appendTo(this, separator = " || '$separator' || ") { append(it) }
      }
    }
}

class GroupConcat(
  private val expr: Expression<String>,
  private val separator: String?
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("GROUP_CONCAT(")
      append(expr)
      separator?.let {
        append(" SEPARATOR '$it'")
      }
      append(")")
    }
}

class Substring(
  private val expr: Expression<String>,
  private val start: Expression<Int>,
  private val length: Expression<Int>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("substr")
      append("(")
      append(expr)
      append(", ")
      append(start)
      append(", ")
      append(length)
      append(")")
    }
}

class Trim(
  private val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("TRIM(")
      append(expr)
      append(")")
    }
}

class Min<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("MIN(")
      append(expr)
      append(")")
    }
}

class Max<T : Comparable<T>>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("MAX(")
      append(expr)
      append(")")
    }
}

class Avg<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("AVG(")
      append(expr)
      append(")")
    }
}

class Sum<T>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("SUM(")
      append(expr)
      append(")")
    }
}

class Count(
  private val expr: Expression<*>,
  private val distinct: Boolean = false
) : Function<Long>(LongPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("COUNT(")
      append(if (distinct) "DISTINCT " else "")
      append(expr)
      append(")")
    }
}

class Case(val value: Expression<*>? = null) {
  @Suppress("FunctionName")
  fun <T> When(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> =
    CaseWhen<T>(value).When(cond, result)
}

class CaseWhen<T>(val value: Expression<*>?) {
  val cases: MutableList<Pair<Expression<Boolean>, Expression<out T>>> = mutableListOf()

  @Suppress("UNCHECKED_CAST", "FunctionName")
  fun <R : T> When(cond: Expression<Boolean>, result: Expression<R>): CaseWhen<R> {
    cases.add(cond to result)
    return this as CaseWhen<R>
  }

  @Suppress("FunctionName")
  fun <R : T> Else(e: Expression<R>): Expression<R> =
    CaseWhenElse(this, e)
}

class CaseWhenElse<T, R : T>(
  private val caseWhen: CaseWhen<T>,
  private val elseResult: Expression<R>
) :
  BaseExpression<R>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("CASE ")
      if (caseWhen.value != null) append(caseWhen.value)

      for ((first, second) in caseWhen.cases) {
        append(" WHEN ")
        append(first)
        append(" THEN ")
        append(second)
      }

      append(" ELSE ")
      append(elseResult)
      append(" END")
    }
}

//class Coalesce<out T, S : T?, R : T>(
//  private val expr: ExpressionWithColumnType<S>,
//  private val alternate: ExpressionWithColumnType<out T>
//) : Function<R>(alternate.columnType) {
//  override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit =
//    queryBuilder { append("COALESCE(", expr, ", ", alternate, ")") }
//}

class Cast<T>(
  private val expr: Expression<*>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("CAST(")
      append(expr)
      append(" AS ")
      append(persistentType.sqlType)
      append(")")
    }
}
