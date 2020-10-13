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

import com.ealva.welite.db.type.BooleanPersistentType
import com.ealva.welite.db.type.BytePersistentType
import com.ealva.welite.db.type.DoublePersistentType
import com.ealva.welite.db.type.FloatPersistentType
import com.ealva.welite.db.type.IntegerPersistentType
import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.ShortPersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.StringPersistentType
import com.ealva.welite.db.type.UBytePersistentType
import com.ealva.welite.db.type.UIntegerPersistentType
import com.ealva.welite.db.type.ULongPersistentType
import com.ealva.welite.db.type.UShortPersistentType
import com.ealva.welite.db.type.toStatementString

public abstract class Op<T> : BaseExpression<T>() {
  public object TRUE : Op<Boolean>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
      append(true.toStatementString())
    }
  }

  public object FALSE : Op<Boolean>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
      append(false.toStatementString())
    }
  }

  public companion object {
    public inline fun <T> build(op: () -> Op<T>): Op<T> = op()
  }
}

public class NotOp<T>(private val expr: Expression<T>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("NOT (")
    append(expr)
    append(")")
  }
}

public abstract class CompoundBooleanOp<T : CompoundBooleanOp<T>>(
  private val operator: String,
  internal val expressions: List<Expression<Boolean>>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    expressions.appendTo(this, separator = operator) { appendExpression(it) }
  }
}

public class AndOp(expressions: List<Expression<Boolean>>) :
  CompoundBooleanOp<AndOp>(" AND ", expressions)

public class OrOp(expressions: List<Expression<Boolean>>) :
  CompoundBooleanOp<AndOp>(" OR ", expressions)

public fun not(op: Expression<Boolean>): Op<Boolean> = NotOp(op)

public infix fun Expression<Boolean>.and(op: Expression<Boolean>): Op<Boolean> = when {
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

public infix fun Expression<Boolean>.or(op: Expression<Boolean>): Op<Boolean> = when {
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

public fun List<Op<Boolean>>.compoundAnd(): Op<Boolean> = reduce(Op<Boolean>::and)
public fun List<Op<Boolean>>.compoundOr(): Op<Boolean> = reduce(Op<Boolean>::or)

public abstract class ComparisonOp(
  private val lhs: Expression<*>,
  private val rhs: Expression<*>,
  private val opSign: String
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    appendExpression(lhs)
    append(' ')
    append(opSign)
    append(' ')
    appendExpression(rhs)
  }
}

public class EqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "=")
public class NeqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<>")
public class LessOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<")
public class LessEqOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "<=")
public class GreaterOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, ">")
public class GreaterEqOp(expr1: Expression<*>, expr2: Expression<*>) :
  ComparisonOp(expr1, expr2, ">=")

public class IsNullOp(private val expr: Expression<*>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" IS NULL")
  }
}

public class IsNotNullOp(private val expr: Expression<*>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" IS NOT NULL")
  }
}

public class LikeOp(lhs: Expression<*>, rhs: Expression<*>) : ComparisonOp(lhs, rhs, "LIKE")
public class NotLikeOp(lhs: Expression<*>, rhs: Expression<*>) : ComparisonOp(lhs, rhs, "NOT LIKE")

public fun byteParam(value: Byte): Expression<Byte> = QueryParameter(value, BytePersistentType())
public fun shortParam(value: Short): Expression<Short> =
  QueryParameter(value, ShortPersistentType())

public fun intParam(value: Int): QueryParameter<Int> =
  QueryParameter(value, IntegerPersistentType())

public fun longParam(value: Long): Expression<Long> = QueryParameter(value, LongPersistentType())
public fun floatParam(value: Float): Expression<Float> =
  QueryParameter(value, FloatPersistentType())

public fun doubleParam(value: Double): Expression<Double> =
  QueryParameter(value, DoublePersistentType())

public fun stringParam(value: String): Expression<String> =
  QueryParameter(value, StringPersistentType())

public fun booleanParam(value: Boolean): Expression<Boolean> =
  QueryParameter(value, BooleanPersistentType())

@ExperimentalUnsignedTypes
public fun ubyteParam(value: UByte): Expression<UByte> =
  QueryParameter(value, UBytePersistentType())

@ExperimentalUnsignedTypes
public fun ushortParam(value: UShort): Expression<UShort> =
  QueryParameter(value, UShortPersistentType())

@ExperimentalUnsignedTypes
public fun uintParam(value: UInt): Expression<UInt> =
  QueryParameter(value, UIntegerPersistentType())

@ExperimentalUnsignedTypes
public fun ulongParam(value: ULong): Expression<ULong> =
  QueryParameter(value, ULongPersistentType())

public fun bindByte(): BindExpression<Byte> = BindExpression(BytePersistentType())
public fun bindShort(): BindExpression<Short> = BindExpression(ShortPersistentType())
public fun bindInt(): BindExpression<Int> = BindExpression(IntegerPersistentType())
public fun bindLong(): BindExpression<Long> = BindExpression(LongPersistentType())
public fun bindFloat(): BindExpression<Float> = BindExpression(FloatPersistentType())
public fun bindDouble(): BindExpression<Double> = BindExpression(DoublePersistentType())
public fun bindString(): BindExpression<String> = BindExpression(StringPersistentType())
public fun bindBoolean(): BindExpression<Boolean> = BindExpression(BooleanPersistentType())

@ExperimentalUnsignedTypes
public fun bindUbyte(): BindExpression<UByte> = BindExpression(UBytePersistentType())

@ExperimentalUnsignedTypes
public fun bindUshort(): BindExpression<UShort> = BindExpression(UShortPersistentType())

@ExperimentalUnsignedTypes
public fun bindUint(): BindExpression<UInt> = BindExpression(UIntegerPersistentType())

@ExperimentalUnsignedTypes
public fun bindUlong(): BindExpression<ULong> = BindExpression(ULongPersistentType())

public class BindExpression<T>(private val sqlType: PersistentType<T>) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder.apply { registerBindable(sqlType) }
}

private fun SqlBuilder.appendExpression(expr: Expression<*>) {
  if (expr is CompoundBooleanOp<*>) {
    append("(")
    append(expr)
    append(")")
  } else append(expr)
}

public class QueryParameter<T>(
  private val value: T,
  private val sqlType: PersistentType<T>
) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder.apply { registerArgument(sqlType, value) }
}
