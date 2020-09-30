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

@file:Suppress("unused")

package com.ealva.welite.db.expr

import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.StringPersistentType

typealias ExprList = List<Expression<*>>

abstract class Function<T>(
  override val persistentType: PersistentType<T>
) : BaseSqlTypeExpression<T>()

class CustomFunction<T>(
  private val functionName: String,
  persistentType: PersistentType<T>,
  private val exprList: ExprList
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(functionName).append('(')
    exprList.appendEach { append(it) }
    append(')')
  }
}

open class CustomOperator<T>(
  private val operatorName: String,
  persistentType: PersistentType<T>,
  private val expr1: Expression<*>,
  private val expr2: Expression<*>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
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
    sqlBuilder.apply { append("RANDOM()") }
}

class LowerCase(private val expr: Expression<String>) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("LOWER(")
    append(expr)
    append(")")
  }
}

class UpperCase(private val expr: Expression<String>) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("UPPER(")
    append(expr)
    append(")")
  }
}

class Concat(
  private val separator: String,
  private val exprList: List<Expression<String>>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    exprList.appendTo(
      builder = this,
      separator = if (separator.isEmpty()) " || " else " || '$separator' || "
    ) { append(it) }

    if (separator.isEmpty()) {
      exprList.appendTo(this, separator = " || ") { append(it) }
    } else {
      exprList.appendTo(this, separator = " || '$separator' || ") { append(it) }
    }
  }
}

class GroupConcat<T : String?>(
  private val expr: Expression<T>,
  private val separator: String?
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("GROUP_CONCAT(")
    append(expr)
    separator?.let {
      append(" , ")
      append('\'')
      append(it)
      append('\'')
    }
    append(")")
  }
}

class Substring(
  private val expr: Expression<String>,
  private val start: Expression<Int>,
  private val subLength: Expression<Int>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("substr")
    append("(")
    append(expr)
    append(", ")
    append(start)
    append(", ")
    append(subLength)
    append(")")
  }
}

class Trim(
  private val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("TRIM(")
    append(expr)
    append(")")
  }
}

class Min<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("MIN(")
    append(expr)
    append(")")
  }
}

class Max<T : Comparable<T>>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("MAX(")
    append(expr)
    append(")")
  }
}

class Avg<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("AVG(")
    append(expr)
    append(")")
  }
}

class Sum<T>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("SUM(")
    append(expr)
    append(")")
  }
}

class Count(
  private val expr: Expression<*>,
  private val distinct: Boolean = false
) : Function<Long>(LongPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("COUNT(")
    if (distinct) {
      append("DISTINCT ")
    }
    append(expr)
    append(")")
  }
}

class Case(private val caseExpression: Expression<*>? = null) {
  @Suppress("FunctionName")
  fun <T> caseWhen(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> =
    CaseWhen<T>(caseExpression).caseWhen(cond, result)
}

class CaseWhen<T>(private val caseExpression: Expression<*>?) : BaseExpression<T>() {
  val cases: MutableList<Pair<Expression<Boolean>, Expression<out T>>> = mutableListOf()

  @Suppress("UNCHECKED_CAST", "FunctionName")
  fun <R : T> caseWhen(cond: Expression<Boolean>, result: Expression<R>): CaseWhen<R> {
    cases.add(cond to result)
    return this as CaseWhen<R>
  }

  @Suppress("FunctionName")
  fun <R : T> caseElse(e: Expression<R>): Expression<R> = CaseWhenElse(this, e)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("CASE ")

    caseExpression?.let { expr ->
      append(expr)
      append(" ")
    }

    cases.forEach { (first, second) ->
      append("WHEN ")
      append(first)
      append(" THEN ")
      append(second)
    }

    append(" END")
  }
}

class CaseWhenElse<T, R : T>(
  private val caseWhen: CaseWhen<T>,
  private val elseResult: Expression<R>
) : BaseExpression<R>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    caseWhen.appendTo(sqlBuilder)
    append(" ELSE ")
    append(elseResult)
    append(" END")
  }
}

fun raiseIgnore(): BaseExpression<String> = Raise(Raise.RaiseType.IGNORE)
fun raiseRollback(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.ROLLBACK, msg)
fun raiseAbort(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.ABORT, msg)
fun raiseFail(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.FAIL, msg)

private class Raise private constructor(
  private val raiseType: RaiseType,
  private val msg: String
) : BaseExpression<String>() {
  enum class RaiseType(val value: String) {
    IGNORE("IGNORE"),
    ROLLBACK("ROLLBACK"),
    ABORT("ABORT"),
    FAIL("FAIL");

    override fun toString(): String = value
  }

  fun SqlBuilder.append(raiseType: RaiseType) = apply { append(raiseType.value) }

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("RAISE(")
    append(raiseType)
    if (raiseType != RaiseType.IGNORE && msg.isNotEmpty()) {
      append(", '")
      append(msg)
    }
    append("')")
  }

  companion object {
    /**
     * Make a Raise function from the [raiseType] and [msg] to be included
     *
     * [SQLite Raise function](https://www.sqlite.org/syntax/raise-function.html)
     */
    operator fun invoke(raiseType: RaiseType, msg: String = ""): BaseExpression<String> {
      return Raise(raiseType, msg)
    }
  }
}

class Cast<T>(
  private val expr: Expression<*>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("CAST(")
    append(expr)
    append(" AS ")
    append(persistentType.sqlType)
    append(")")
  }
}
