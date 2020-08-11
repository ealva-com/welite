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

import com.ealva.welite.db.type.NullableBooleanPersistentType
import com.ealva.welite.db.type.NullableBytePersistentType
import com.ealva.welite.db.type.NullableDoublePersistentType
import com.ealva.welite.db.type.NullableFloatPersistentType
import com.ealva.welite.db.type.NullableIntegerPersistentType
import com.ealva.welite.db.type.NullableLongPersistentType
import com.ealva.welite.db.type.NullableShortPersistentType
import com.ealva.welite.db.type.NullableStringPersistentType
import com.ealva.welite.db.type.NullableUIntegerPersistentType
import com.ealva.welite.db.type.NullableULongPersistentType
import com.ealva.welite.db.type.NullableUShortPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.UBytePersistentType
import com.ealva.welite.db.type.toStatementString

abstract class Op<T> : BaseExpression<T>() {
  object TRUE : Op<Boolean>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
      append(true.toStatementString())
    }
  }

  object FALSE : Op<Boolean>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
      append(false.toStatementString())
    }
  }

  companion object {
    inline fun <T> build(op: () -> Op<T>): Op<T> = op()
  }
}

class NotOp<T>(private val expr: Expression<T>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
    append("NOT (").append(expr).append(")")
  }
}

abstract class CompoundBooleanOp<T : CompoundBooleanOp<T>>(
  private val operator: String,
  internal val expressions: List<Expression<Boolean>>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
    expressions.appendTo(this, separator = operator) { appendExpression(it) }
  }
}

class AndOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" AND ", expressions)
class OrOp(expressions: List<Expression<Boolean>>) : CompoundBooleanOp<AndOp>(" OR ", expressions)

fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

infix fun Expression<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
  this is AndOp && op is AndOp -> AndOp(expressions + op.expressions)
  this is AndOp -> AndOp(expressions + op)
  op is AndOp -> AndOp(
    ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
      it.add(this)
      it.addAll(op.expressions)
    }
  )
  else -> AndOp(listOf(this, op))
}

infix fun Expression<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = when {
  this is OrOp && op is OrOp -> OrOp(expressions + op.expressions)
  this is OrOp -> OrOp(expressions + op)
  op is OrOp -> OrOp(
    ArrayList<Expression<Boolean>>(op.expressions.size + 1).also {
      it.add(this)
      it.addAll(op.expressions)
    }
  )
  else -> OrOp(listOf(this, op))
}

fun List<Op<Boolean>>.compoundAnd(): Op<Boolean> = reduce(Op<Boolean>::and)
fun List<Op<Boolean>>.compoundOr(): Op<Boolean> = reduce(Op<Boolean>::or)

abstract class ComparisonOp(
  private val lhs: Expression<*>,
  private val rhs: Expression<*>,
  private val opSign: String
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
    appendExpression(lhs)
    append(" $opSign ")
    appendExpression(rhs)
  }
}

class EqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "=")
class NeqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<>")
class LessOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<")
class LessEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<=")
class GreaterOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">")
class GreaterEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">=")

class IsNullOp(private val expr: Expression<*>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
    append(expr)
    append(" IS NULL")
  }
}

class IsNotNullOp(private val expr: Expression<*>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder {
    append(expr)
    append(" IS NOT NULL")
  }
}

class LikeOp(lhs: Expression<*>, rhs: Expression<*>) : ComparisonOp(lhs, rhs, "LIKE")
class NotLikeOp(lhs: Expression<*>, rhs: Expression<*>) : ComparisonOp(lhs, rhs, "NOT LIKE")

fun byteParam(value: Byte): Expression<Byte> = QueryParameter(value, NullableBytePersistentType())
fun shortParam(value: Short): Expression<Short> = QueryParameter(
  value,
  NullableShortPersistentType()
)

fun intParam(value: Int): QueryParameter<Int> =
  QueryParameter(value, NullableIntegerPersistentType())

fun longParam(value: Long): Expression<Long> = QueryParameter(value, NullableLongPersistentType())
fun floatParam(value: Float): Expression<Float> =
  QueryParameter(value, NullableFloatPersistentType())

fun doubleParam(value: Double): Expression<Double> =
  QueryParameter(value, NullableDoublePersistentType())

fun stringParam(value: String): Expression<String> =
  QueryParameter(value, NullableStringPersistentType())

fun booleanParam(value: Boolean): Expression<Boolean> =
  QueryParameter(value, NullableBooleanPersistentType())

@ExperimentalUnsignedTypes
fun ubyteParam(value: UByte): Expression<UByte> = QueryParameter(value, UBytePersistentType())

@ExperimentalUnsignedTypes
fun ushortParam(value: UShort): Expression<UShort> =
  QueryParameter(value, NullableUShortPersistentType())

@ExperimentalUnsignedTypes
fun uintParam(value: UInt): Expression<UInt> =
  QueryParameter(value, NullableUIntegerPersistentType())

@ExperimentalUnsignedTypes
fun ulongParam(value: ULong): Expression<ULong> =
  QueryParameter(value, NullableULongPersistentType())

class BindableParameter<T>(private val sqlType: PersistentType<T>) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      registerBindable(sqlType)
    }
}

private fun SqlBuilder.appendExpression(expr: Expression<*>) {
  if (expr is CompoundBooleanOp<*>) {
    append("(")
    append(expr)
    append(")")
  } else append(expr)
}

class QueryParameter<T>(
  private val value: T,
  private val sqlType: PersistentType<T>
) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder { registerArgument(sqlType, value) }
}
