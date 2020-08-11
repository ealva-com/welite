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

import com.ealva.welite.db.type.NullableBooleanPersistentType
import com.ealva.welite.db.type.NullableBytePersistentType
import com.ealva.welite.db.type.NullableDoublePersistentType
import com.ealva.welite.db.type.NullableFloatPersistentType
import com.ealva.welite.db.type.NullableIntegerPersistentType
import com.ealva.welite.db.type.NullableLongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.NullableShortPersistentType
import com.ealva.welite.db.type.NullableStringPersistentType
import com.ealva.welite.db.type.NullableUBytePersistentType
import com.ealva.welite.db.type.NullableUIntegerPersistentType
import com.ealva.welite.db.type.NullableULongPersistentType
import com.ealva.welite.db.type.NullableUShortPersistentType

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
  LiteralOp(NullableBooleanPersistentType(), value)

fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(NullableBytePersistentType(), value)

@ExperimentalUnsignedTypes
fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(NullableUBytePersistentType(), value)

fun shortLiteral(value: Short): LiteralOp<Short> =
  LiteralOp(NullableShortPersistentType(), value)

@ExperimentalUnsignedTypes
fun ushortLiteral(value: UShort): LiteralOp<UShort> =
  LiteralOp(NullableUShortPersistentType(), value)

fun intLiteral(value: Int): LiteralOp<Int> =
  LiteralOp(NullableIntegerPersistentType(), value)

@ExperimentalUnsignedTypes
fun uintLiteral(value: UInt): LiteralOp<UInt> =
  LiteralOp(NullableUIntegerPersistentType(), value)

fun longLiteral(value: Long): LiteralOp<Long> =
  LiteralOp(NullableLongPersistentType(), value)

@ExperimentalUnsignedTypes
fun ulongLiteral(value: ULong): LiteralOp<ULong> =
  LiteralOp(NullableULongPersistentType(), value)

fun floatLiteral(value: Float): LiteralOp<Float> =
  LiteralOp(NullableFloatPersistentType(), value)

fun doubleLiteral(value: Double): LiteralOp<Double> =
  LiteralOp(NullableDoublePersistentType(), value)

fun stringLiteral(value: String): LiteralOp<String> =
  LiteralOp(NullableStringPersistentType(), value)

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
