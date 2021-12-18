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
import com.ealva.welite.db.type.StringPersistentType
import java.math.BigDecimal

public infix fun <T> SqlTypeExpression<T>.eq(t: T): Op<Boolean> =
  if (t == null) isNull() else EqOp(this, param(t))

public infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): EqOp =
  EqOp(this, other)

public infix fun <T> SqlTypeExpression<T>.neq(other: T): Op<Boolean> =
  if (other == null) isNotNull() else NeqOp(this, param(other))

public infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): NeqOp =
  NeqOp(this, other)

public infix fun <T : Comparable<T>> SqlTypeExpression<in T>.less(t: T): LessOp =
  LessOp(this, param(t))

public infix fun <T : Comparable<T>, S : T?> Expression<in S>.less(
  other: Expression<in S>
): LessOp = LessOp(this, other)

public infix fun <T : Comparable<T>> SqlTypeExpression<in T>.lessEq(t: T): LessEqOp =
  LessEqOp(this, param(t))

public infix fun <T : Comparable<T>, S : T?> Expression<in S>.lessEq(
  other: Expression<in S>
): LessEqOp = LessEqOp(this, other)

public infix fun <T : Comparable<T>> SqlTypeExpression<in T>.greater(t: T): GreaterOp =
  GreaterOp(this, param(t))

public infix fun <T : Comparable<T>, S : T?> Expression<in S>.greater(
  other: Expression<in S>
): GreaterOp = GreaterOp(this, other)

public infix fun <T : Comparable<T>> SqlTypeExpression<in T>.greaterEq(t: T): GreaterEqOp =
  GreaterEqOp(this, param(t))

public infix fun <T : Comparable<T>, S : T?> Expression<in S>.greaterEq(
  other: Expression<in S>
): GreaterEqOp = GreaterEqOp(this, other)

public fun <T> SqlTypeExpression<T>.between(from: T, to: T): Between =
  Between(this, literal(from), literal(to))

public fun <T> SqlTypeExpression<T>.notBetween(from: T, to: T): Between =
  Between(this, literal(from), literal(to), not = true)

public fun <T> Expression<T>.isNull(): IsNullOp = IsNullOp(this)

public fun <T> Expression<T>.isNotNull(): IsNotNullOp = IsNotNullOp(this)

public infix operator fun <T> SqlTypeExpression<T>.plus(t: T): PlusOp<T, T> =
  PlusOp(this, param(t), persistentType)

public infix operator fun <T, S : T> SqlTypeExpression<T>.plus(other: Expression<S>): PlusOp<T, S> =
  PlusOp(this, other, persistentType)

public infix operator fun <T> SqlTypeExpression<T>.minus(t: T): MinusOp<T, T> =
  MinusOp(this, param(t), persistentType)

public infix operator fun <T, S : T> SqlTypeExpression<T>.minus(
  other: Expression<S>
): MinusOp<T, S> = MinusOp(this, other, persistentType)

public infix operator fun <T> SqlTypeExpression<T>.times(t: T): TimesOp<T, T> =
  TimesOp(this, param(t), persistentType)

public infix operator fun <T, S : T> SqlTypeExpression<T>.times(
  other: Expression<S>
): TimesOp<T, S> = TimesOp(this, other, persistentType)

public infix operator fun <T> SqlTypeExpression<T>.div(t: T): DivideOp<T, T> =
  DivideOp(this, param(t), persistentType)

public infix operator fun <T, S : T> SqlTypeExpression<T>.div(
  other: Expression<S>
): DivideOp<T, S> = DivideOp(this, other, persistentType)

public infix operator fun <T : Number?, S : T> SqlTypeExpression<T>.rem(t: S): ModOp<T, S> =
  ModOp(this, param(t), persistentType)

public infix operator fun <T : Number?, S : Number> SqlTypeExpression<T>.rem(
  other: Expression<S>
): ModOp<T, S> = ModOp(this, other, persistentType)

public infix fun <T : Number?, S : T> SqlTypeExpression<T>.mod(t: S): ModOp<T, S> = this % t

public infix fun <T : Number?, S : Number> SqlTypeExpression<T>.mod(
  other: Expression<S>
): ModOp<T, S> = this % other

public fun concat(vararg expr: Expression<String>): Concat = Concat("", expr.toList())

public fun concat(separator: String = "", expr: List<Expression<String>>): Concat =
  Concat(separator, expr)

public infix fun <T : String?> Expression<T>.like(pattern: String): LikeOp =
  LikeOp(this, stringParam(pattern))

public infix fun <T : String?> Expression<T>.like(expr: Expression<T>): LikeOp =
  LikeOp(this, expr)

public infix fun <T : String?> Expression<T>.notLike(pattern: String): NotLikeOp =
  NotLikeOp(this, stringParam(pattern))

public infix fun <T : String?> Expression<T>.notLike(expr: Expression<T>): NotLikeOp =
  NotLikeOp(this, expr)

public fun case(value: Expression<*>? = null): Case = Case(value)

public infix fun <T> SqlTypeExpression<T>.inList(list: Iterable<T>): InListOrNotInListOp<T> =
  InListOrNotInListOp(this, list, isInList = true)

public infix fun <T> SqlTypeExpression<T>.notInList(list: Iterable<T>): InListOrNotInListOp<T> =
  InListOrNotInListOp(this, list, isInList = false)

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("UNCHECKED_CAST")
public fun <T> SqlTypeExpression<in T>.param(value: T): Expression<T> = when (value) {
  is String -> stringParam(value)
  is Int -> intParam(value)
  is Long -> longParam(value)
  is Float -> floatParam(value)
  is Double -> doubleParam(value)
  is Short -> shortParam(value)
  is Boolean -> booleanParam(value)
  is Byte -> byteParam(value)
  is UInt -> uintParam(value)
  is ULong -> ulongParam(value)
  is UShort -> ushortParam(value)
  is UByte -> ubyteParam(value)
  is BigDecimal -> longParam(value.unscaledValue().toLong())
  is BindExpression<*> -> value
  else -> QueryParameter(value, persistentType)
} as Expression<T>

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("UNCHECKED_CAST")
public fun <T> SqlTypeExpression<in T>.literal(value: T): LiteralOp<T> = when (value) {
  is String -> com.ealva.welite.db.expr.literal(value)
  is Int -> com.ealva.welite.db.expr.literal(value)
  is Long -> com.ealva.welite.db.expr.literal(value)
  is Float -> com.ealva.welite.db.expr.literal(value)
  is Double -> com.ealva.welite.db.expr.literal(value)
  is Short -> com.ealva.welite.db.expr.literal(value)
  is Boolean -> com.ealva.welite.db.expr.literal(value)
  is Byte -> com.ealva.welite.db.expr.literal(value)
  is UInt -> com.ealva.welite.db.expr.literal(value)
  is ULong -> com.ealva.welite.db.expr.literal(value)
  is UShort -> com.ealva.welite.db.expr.literal(value)
  is UByte -> com.ealva.welite.db.expr.literal(value)
  is BigDecimal -> literal(value.unscaledValue().toLong())
  is ByteArray -> literal(value.toString(Charsets.UTF_8))
  else -> LiteralOp(persistentType, value)
} as LiteralOp<T>

public enum class Order(public val sqlName: String) {
  ASC("ASC"),
  DESC("DESC");

  override fun toString(): String = sqlName
}

public fun Expression<String>.substring(start: Int, length: Int): Function<String> =
  Substring(this, literal(start), literal(length))

public fun Expression<String>.trim(): Function<String> = Trim(this)

public fun Expression<Long>.random(): Function<Long> = Random()

public fun Expression<String>.lowerCase(): Function<String> = LowerCase(this)

public fun Expression<String>.upperCase(): Function<String> = UpperCase(this)

public fun <T : Comparable<T>> SqlTypeExpression<T>.avg(): Function<T> =
  Avg(this, this.persistentType)

public fun <T : Any?> SqlTypeExpression<T>.sum(): Function<T> = Sum(this, this.persistentType)

public fun <T : Comparable<T>> SqlTypeExpression<T>.min(): Function<T> =
  Min(this, this.persistentType)

public fun <T : Comparable<T>> SqlTypeExpression<T>.max(): Function<T> =
  Max(this, this.persistentType)

public fun <T : String?> Expression<T>.groupConcat(separator: String? = null): GroupConcat<T> =
  GroupConcat(this, separator)

public fun SqlTypeExpression<*>.count(): Function<Long> = Count(this)

public fun <R> Expression<*>.cast(persistentType: PersistentType<R>): SqlTypeExpression<R> =
  Cast(this, persistentType)

public fun <T : Any> SqlTypeExpression<T>.function(name: String): CustomFunction<T> =
  CustomFunction(name, persistentType, listOf(this))

public fun customStringFunction(
  name: String,
  vararg params: Expression<*>
): CustomFunction<String> =
  CustomFunction(name, StringPersistentType(), params.toList())

public fun customLongFunction(name: String, vararg params: Expression<*>): CustomFunction<Long> =
  CustomFunction(name, LongPersistentType(), params.toList())
