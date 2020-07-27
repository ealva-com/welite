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

object ExpressionBuilder {

  infix fun <T> SqlTypeExpression<T>.eq(t: T): Op<Boolean> =
    if (t == null) isNull() else EqOp(this, param(t))

  infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.eq(other: Expression<in S2>): EqOp =
    EqOp(this, other)

  infix fun <T> SqlTypeExpression<T>.neq(other: T): Op<Boolean> =
    if (other == null) isNotNull() else NeqOp(this, param(other))

  infix fun <T, S1 : T?, S2 : T?> Expression<in S1>.neq(other: Expression<in S2>): NeqOp =
    NeqOp(this, other)

  infix fun <T : Comparable<T>> SqlTypeExpression<in T>.less(t: T): LessOp =
    LessOp(this, param(t))

  infix fun <T : Comparable<T>, S : T?> Expression<in S>.less(other: Expression<in S>): LessOp =
    LessOp(this, other)

  infix fun <T : Comparable<T>> SqlTypeExpression<in T>.lessEq(t: T): LessEqOp =
    LessEqOp(this, param(t))

  infix fun <T : Comparable<T>, S : T?> Expression<in S>.lessEq(other: Expression<in S>): LessEqOp =
    LessEqOp(this, other)

  infix fun <T : Comparable<T>> SqlTypeExpression<in T>.greater(t: T) = GreaterOp(this, param(t))

  infix fun <T : Comparable<T>, S : T?> Expression<in S>.greater(other: Expression<in S>) =
    GreaterOp(this, other)

  infix fun <T : Comparable<T>> SqlTypeExpression<in T>.greaterEq(t: T) = GreaterEqOp(this, param(t))

  infix fun <T : Comparable<T>, S : T?> Expression<in S>.greaterEq(other: Expression<in S>) =
    GreaterEqOp(this, other)

  fun <T> SqlTypeExpression<T>.between(from: T, to: T): Between =
    Between(this, literal(from), literal(to))

  @Suppress("MemberVisibilityCanBePrivate")
  fun <T> Expression<T>.isNull(): IsNullOp = IsNullOp(this)

  @Suppress("MemberVisibilityCanBePrivate")
  fun <T> Expression<T>.isNotNull(): IsNotNullOp = IsNotNullOp(this)

  infix operator fun <T> SqlTypeExpression<T>.plus(t: T): PlusOp<T, T> =
    PlusOp(this, param(t), persistentType)

  infix operator fun <T, S : T> SqlTypeExpression<T>.plus(other: Expression<S>): PlusOp<T, S> =
    PlusOp(this, other, persistentType)

  infix operator fun <T> SqlTypeExpression<T>.minus(t: T): MinusOp<T, T> =
    MinusOp(this, param(t), persistentType)

  infix operator fun <T, S : T> SqlTypeExpression<T>.minus(other: Expression<S>): MinusOp<T, S> =
    MinusOp(this, other, persistentType)

  infix operator fun <T> SqlTypeExpression<T>.times(t: T): TimesOp<T, T> =
    TimesOp(this, param(t), persistentType)

  infix operator fun <T, S : T> SqlTypeExpression<T>.times(other: Expression<S>): TimesOp<T, S> =
    TimesOp(this, other, persistentType)

  infix operator fun <T> SqlTypeExpression<T>.div(t: T): DivideOp<T, T> =
    DivideOp(this, param(t), persistentType)

  infix operator fun <T, S : T> SqlTypeExpression<T>.div(other: Expression<S>): DivideOp<T, S> =
    DivideOp(this, other, persistentType)

  infix operator fun <T : Number?, S : T> SqlTypeExpression<T>.rem(t: S): ModOp<T, S> =
    ModOp(this, param(t), persistentType)

  infix operator fun <T : Number?, S : Number> SqlTypeExpression<T>.rem(other: Expression<S>) =
    ModOp(this, other, persistentType)

  infix fun <T : Number?, S : T> SqlTypeExpression<T>.mod(t: S): ModOp<T, S> = this % t

  infix fun <T : Number?, S : Number> SqlTypeExpression<T>.mod(other: Expression<S>) = this % other

  fun concat(vararg expr: Expression<String>): Concat = Concat("", *expr)

  fun concat(separator: String = "", expr: List<Expression<String>>): Concat =
    Concat(separator, *expr.toTypedArray())

  infix fun <T : String?> Expression<T>.like(pattern: String): LikeOp =
    LikeOp(this, stringParam(pattern))

  infix fun <T : String?> Expression<T>.notLike(pattern: String): NotLikeOp =
    NotLikeOp(this, stringParam(pattern))

  fun case(value: Expression<*>? = null): Case = Case(value)

  infix fun <T> SqlTypeExpression<T>.inList(list: Iterable<T>): InListOrNotInListOp<T> =
    InListOrNotInListOp(this, list, isInList = true)

  infix fun <T> SqlTypeExpression<T>.notInList(list: Iterable<T>): InListOrNotInListOp<T> =
    InListOrNotInListOp(this, list, isInList = false)

  @Suppress("UNCHECKED_CAST")
  fun <T> SqlTypeExpression<in T>.param(value: T): Expression<T> = when (value) {
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
    is BindableParameter<*> -> value
    else -> QueryParameter(value, persistentType)
  } as Expression<T>

  @Suppress("UNCHECKED_CAST")
  fun <T> SqlTypeExpression<in T>.literal(value: T): LiteralOp<T> = when (value) {
    is String -> stringLiteral(value)
    is Int -> intLiteral(value)
    is Long -> longLiteral(value)
    is Float -> floatLiteral(value)
    is Double -> doubleLiteral(value)
    is Short -> shortLiteral(value)
    is Boolean -> booleanLiteral(value)
    is Byte -> byteLiteral(value)
    is UInt -> uintLiteral(value)
    is ULong -> ulongLiteral(value)
    is UShort -> ushortLiteral(value)
    is UByte -> ubyteLiteral(value)
    is ByteArray -> stringLiteral(value.toString(Charsets.UTF_8))

    else -> LiteralOp(persistentType, value)
  } as LiteralOp<T>
}

enum class SortOrder(val sqlName: String) {
  ASC("ASC"),
  DESC("DESC");

  override fun toString() = sqlName
}

fun Expression<String>.substring(start: Int, length: Int): Substring =
  Substring(this, intLiteral(start), intLiteral(length))

fun Expression<String>.trim(): Trim = Trim(this)

fun Expression<String>.lowerCase(): LowerCase = LowerCase(this)

fun Expression<String>.upperCase(): UpperCase = UpperCase(this)

fun <T : Comparable<T>> SqlTypeExpression<T>.avg(): Avg<T> = Avg(this, this.persistentType)

fun <T : Any?> SqlTypeExpression<T>.sum(): Sum<T> = Sum(this, this.persistentType)

fun <T : Comparable<T>> SqlTypeExpression<T>.min(): Min<T> = Min(this, this.persistentType)

fun <T : Comparable<T>> SqlTypeExpression<T>.max(): Max<T> = Max(this, this.persistentType)

fun Expression<String>.groupConcat(separator: String? = null) = GroupConcat(this, separator)

fun SqlTypeExpression<*>.count(): Count = Count(this)

fun <R> Expression<*>.cast(persistentType: PersistentType<R>): SqlTypeExpression<R> =
  Cast(this, persistentType)

fun <T : Any> SqlTypeExpression<T>.function(name: String): CustomFunction<T> =
  CustomFunction(name, persistentType, this)

fun customStringFunction(name: String, vararg params: Expression<*>): CustomFunction<String> =
  CustomFunction(name, StringPersistentType(), *params)

fun customLongFunction(name: String, vararg params: Expression<*>): CustomFunction<Long> =
  CustomFunction(name, LongPersistentType(), *params)

