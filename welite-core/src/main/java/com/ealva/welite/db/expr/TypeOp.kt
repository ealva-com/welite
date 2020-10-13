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

public class LiteralOp<T>(
  override val persistentType: PersistentType<T>,
  public val value: T
) : BaseSqlTypeExpression<T>() {
  override fun asDefaultValue(): String = toString()

  override fun appendTo(
    sqlBuilder: SqlBuilder
  ): SqlBuilder = sqlBuilder.apply {
    append(persistentType.valueToString(value))
  }
}

public fun booleanLiteral(value: Boolean): LiteralOp<Boolean> =
  LiteralOp(BooleanPersistentType(), value)

public fun byteLiteral(value: Byte): LiteralOp<Byte> = LiteralOp(BytePersistentType(), value)
public fun shortLiteral(value: Short): LiteralOp<Short> = LiteralOp(ShortPersistentType(), value)
public fun intLiteral(value: Int): LiteralOp<Int> = LiteralOp(IntegerPersistentType(), value)
public fun longLiteral(value: Long): LiteralOp<Long> = LiteralOp(LongPersistentType(), value)
public fun floatLiteral(value: Float): LiteralOp<Float> = LiteralOp(FloatPersistentType(), value)
public fun doubleLiteral(value: Double): LiteralOp<Double> =
  LiteralOp(DoublePersistentType(), value)

public fun stringLiteral(value: String): LiteralOp<String> =
  LiteralOp(StringPersistentType(), value)

@ExperimentalUnsignedTypes
public fun ubyteLiteral(value: UByte): LiteralOp<UByte> = LiteralOp(UBytePersistentType(), value)

@ExperimentalUnsignedTypes
public fun ushortLiteral(value: UShort): LiteralOp<UShort> =
  LiteralOp(UShortPersistentType(), value)

@ExperimentalUnsignedTypes
public fun uintLiteral(value: UInt): LiteralOp<UInt> = LiteralOp(UIntegerPersistentType(), value)

@ExperimentalUnsignedTypes
public fun ulongLiteral(value: ULong): LiteralOp<ULong> = LiteralOp(ULongPersistentType(), value)

public class ModOp<T : Number?, S : Number?>(
  private val lhs: Expression<T>,
  private val rhs: Expression<S>,
  override val persistentType: PersistentType<T>
) : BaseSqlTypeExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append('(')
    append(lhs)
    append(" % ")
    append(rhs)
    append(')')
  }
}

@Suppress("unused")
public class NoOpConversion<T, S>(
  private val expr: Expression<T>,
  override val persistentType: PersistentType<S>
) : BaseSqlTypeExpression<S>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply { append(expr) }
}

public class InListOrNotInListOp<T>(
  private val expr: SqlTypeExpression<T>,
  private val list: Iterable<T>,
  private val isInList: Boolean = true
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
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

public class PlusOp<T, S : T>(
  lhs: Expression<T>,
  rhs: Expression<S>,
  persistentType: PersistentType<T>
) : CustomOperator<T>("+", persistentType, lhs, rhs)

public class MinusOp<T, S : T>(
  lhs: Expression<T>,
  rhs: Expression<S>,
  persistentType: PersistentType<T>
) : CustomOperator<T>("-", persistentType, lhs, rhs)

public class TimesOp<T, S : T>(
  lhs: Expression<T>,
  rhs: Expression<S>,
  persistentType: PersistentType<T>
) : CustomOperator<T>("*", persistentType, lhs, rhs)

public class DivideOp<T, S : T>(
  rhs: Expression<T>,
  lhs: Expression<S>,
  persistentType: PersistentType<T>
) : CustomOperator<T>("/", persistentType, rhs, lhs)

public class Between(
  private val expr: Expression<*>,
  private val from: LiteralOp<*>,
  private val to: LiteralOp<*>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" BETWEEN ")
    append(from)
    append(" AND ")
    append(to)
  }
}
