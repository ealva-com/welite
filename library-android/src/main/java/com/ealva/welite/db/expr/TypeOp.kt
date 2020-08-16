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

import com.ealva.welite.db.type.BooleanPersistentType
import com.ealva.welite.db.type.BytePersistentType
import com.ealva.welite.db.type.DoublePersistentType
import com.ealva.welite.db.type.FloatPersistentType
import com.ealva.welite.db.type.IntegerPersistentType
import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.ShortPersistentType
import com.ealva.welite.db.type.StringPersistentType
import com.ealva.welite.db.type.UBytePersistentType
import com.ealva.welite.db.type.UIntegerPersistentType
import com.ealva.welite.db.type.ULongPersistentType
import com.ealva.welite.db.type.UShortPersistentType

class LiteralOp<T>(
  override val persistentType: PersistentType<T>,
  val value: T
) : BaseSqlTypeExpression<T>() {
  override fun asDefaultValue() = toString()

  override fun appendTo(
    sqlBuilder: SqlBuilder
  ): SqlBuilder = sqlBuilder {
    append(persistentType.valueToString(value))
  }
}

fun booleanLiteral(value: Boolean): LiteralOp<Boolean> =
  LiteralOp(BooleanPersistentType(), value)

fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(BytePersistentType(), value)

@ExperimentalUnsignedTypes
fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(UBytePersistentType(), value)

fun shortLiteral(value: Short): LiteralOp<Short> =
  LiteralOp(ShortPersistentType(), value)

@ExperimentalUnsignedTypes
fun ushortLiteral(value: UShort): LiteralOp<UShort> =
  LiteralOp(UShortPersistentType(), value)

fun intLiteral(value: Int): LiteralOp<Int> =
  LiteralOp(IntegerPersistentType(), value)

@ExperimentalUnsignedTypes
fun uintLiteral(value: UInt): LiteralOp<UInt> =
  LiteralOp(UIntegerPersistentType(), value)

fun longLiteral(value: Long): LiteralOp<Long> =
  LiteralOp(LongPersistentType(), value)

@ExperimentalUnsignedTypes
fun ulongLiteral(value: ULong): LiteralOp<ULong> =
  LiteralOp(ULongPersistentType(), value)

fun floatLiteral(value: Float): LiteralOp<Float> =
  LiteralOp(FloatPersistentType(), value)

fun doubleLiteral(value: Double): LiteralOp<Double> =
  LiteralOp(DoublePersistentType(), value)

fun stringLiteral(value: String): LiteralOp<String> =
  LiteralOp(StringPersistentType(), value)

class ModOp<T : Number?, S : Number?>(
  private val lhs: Expression<T>,
  private val rhs: Expression<S>,
  override val persistentType: PersistentType<T>
) : BaseSqlTypeExpression<T>() {
  override fun appendTo(
    sqlBuilder: SqlBuilder
  ): SqlBuilder = sqlBuilder {
    append('(')
    append(lhs)
    append(" % ")
    append(rhs)
    append(')')
  }
}

@Suppress("unused")
class NoOpConversion<T, S>(
  private val expr: Expression<T>,
  override val persistentType: PersistentType<S>
) : BaseSqlTypeExpression<S>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder { append(expr) }
}

class InListOrNotInListOp<T>(
  private val expr: SqlTypeExpression<T>,
  private val list: Iterable<T>,
  private val isInList: Boolean = true
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      list.iterator().let { i ->
        if (!i.hasNext()) {
          if (isInList) {
            append(FALSE)
          } else {
            append(TRUE)
          }
        } else {
          val first = i.next()
          if (!i.hasNext()) {
            append(expr)
            when {
              isInList -> append(" = ")
              else -> append(" != ")
            }
            registerArgument(expr.persistentType, first)
          } else {
            append(expr)
            when {
              isInList -> append(" IN (")
              else -> append(" NOT IN (")
            }
            registerArguments(expr.persistentType, list)
            append(")")
          }
        }
      }
    }
}

class PlusOp<T, S : T>(lhs: Expression<T>, rhs: Expression<S>, persistentType: PersistentType<T>) :
  CustomOperator<T>("+", persistentType, lhs, rhs)

class MinusOp<T, S : T>(
  lhs: Expression<T>,
  rhs: Expression<S>,
  persistentType: PersistentType<T>
) :
  CustomOperator<T>("-", persistentType, lhs, rhs)

class TimesOp<T, S : T>(
  lhs: Expression<T>,
  rhs: Expression<S>,
  persistentType: PersistentType<T>
) :
  CustomOperator<T>("*", persistentType, lhs, rhs)

class DivideOp<T, S : T>(
  rhs: Expression<T>,
  lhs: Expression<S>,
  persistentType: PersistentType<T>
) :
  CustomOperator<T>("/", persistentType, rhs, lhs)

class Between(
  private val expr: Expression<*>,
  private val from: LiteralOp<*>,
  private val to: LiteralOp<*>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append(expr)
      append(" BETWEEN ")
      append(from)
      append(" AND ")
      append(to)
    }
}
