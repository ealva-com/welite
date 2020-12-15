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

public typealias ExprList = List<Expression<*>>

public abstract class Function<T>(
  override val persistentType: PersistentType<T>
) : BaseSqlTypeExpression<T>() {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Function<*>

    if (persistentType != other.persistentType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + persistentType.hashCode()
    return result
  }
}

public class CustomFunction<T>(
  private val functionName: String,
  persistentType: PersistentType<T>,
  private val exprList: List<Expression<*>>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(functionName).append('(')
    exprList.appendEach { append(it) }
    append(')')
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as CustomFunction<*>

    if (functionName != other.functionName) return false
    if (exprList != other.exprList) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + functionName.hashCode()
    result = 31 * result + exprList.hashCode()
    return result
  }
}

public open class CustomOperator<T>(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as CustomOperator<*>

    if (operatorName != other.operatorName) return false
    if (expr1 != other.expr1) return false
    if (expr2 != other.expr2) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + operatorName.hashCode()
    result = 31 * result + expr1.hashCode()
    result = 31 * result + expr2.hashCode()
    return result
  }
}

public class Random : Function<Long>(LongPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder.apply { append("RANDOM()") }
}

public class LowerCase(private val expr: Expression<String>) :
  Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("LOWER(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as LowerCase

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class UpperCase(private val expr: Expression<String>) :
  Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("UPPER(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as UpperCase

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Concat(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Concat

    if (separator != other.separator) return false
    if (exprList != other.exprList) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + separator.hashCode()
    result = 31 * result + exprList.hashCode()
    return result
  }
}

public class GroupConcat<T : String?>(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as GroupConcat<*>

    if (expr != other.expr) return false
    if (separator != other.separator) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    result = 31 * result + (separator?.hashCode() ?: 0)
    return result
  }
}

public class Substring(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Substring

    if (expr != other.expr) return false
    if (start != other.start) return false
    if (subLength != other.subLength) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    result = 31 * result + start.hashCode()
    result = 31 * result + subLength.hashCode()
    return result
  }
}

public class Trim(
  private val expr: Expression<String>
) : Function<String>(StringPersistentType()) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("TRIM(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Trim

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Min<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("MIN(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Min<*>

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Max<T : Comparable<T>>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("MAX(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Max<*>

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Avg<T : Comparable<T>>(
  private val expr: Expression<in T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("AVG(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Avg<*>

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Sum<T>(
  private val expr: Expression<T>,
  persistentType: PersistentType<T>
) : Function<T>(persistentType) {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("SUM(")
    append(expr)
    append(")")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Sum<*>

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}

public class Count(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Count

    if (expr != other.expr) return false
    if (distinct != other.distinct) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    result = 31 * result + distinct.hashCode()
    return result
  }
}

public class Case(private val caseExpression: Expression<*>? = null) {
  @Suppress("FunctionName")
  public fun <T> caseWhen(cond: Expression<Boolean>, result: Expression<T>): CaseWhen<T> =
    CaseWhen<T>(caseExpression).caseWhen(cond, result)
}

public class CaseWhen<T>(private val caseExpression: Expression<*>?) : BaseExpression<T>() {
  public val cases: MutableList<Pair<Expression<Boolean>, Expression<out T>>> = mutableListOf()

  @Suppress("UNCHECKED_CAST", "FunctionName")
  public fun <R : T> caseWhen(cond: Expression<Boolean>, result: Expression<R>): CaseWhen<R> {
    cases.add(cond to result)
    return this as CaseWhen<R>
  }

  @Suppress("FunctionName")
  public fun <R : T> caseElse(e: Expression<R>): Expression<R> = CaseWhenElse(this, e)

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

public class CaseWhenElse<T, R : T>(
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

public fun raiseIgnore(): BaseExpression<String> = Raise(Raise.RaiseType.IGNORE)
public fun raiseRollback(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.ROLLBACK, msg)
public fun raiseAbort(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.ABORT, msg)
public fun raiseFail(msg: String): BaseExpression<String> = Raise(Raise.RaiseType.FAIL, msg)

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

public class Cast<T>(
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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as Cast<*>

    if (expr != other.expr) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + expr.hashCode()
    return result
  }
}
