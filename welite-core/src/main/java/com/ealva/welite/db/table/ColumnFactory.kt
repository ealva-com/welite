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

package com.ealva.welite.db.table

import com.ealva.welite.db.type.Blob
import kotlin.reflect.KClass

/**
 * Factory functions for all the "fundamental" types. The enumeration functions don't support
 * nullability. It's expected a special enum member can be used to indicate the absence of the
 * other members.
 *
 * The purpose of this interface is primarily (only?) support composite columns and keep these
 * methods from the public interface of Table.
 */
public interface ColumnFactory {
  public fun byte(name: String, block: ColumnConstraints<Byte>.() -> Unit = {}): Column<Byte>
  public fun optByte(name: String, block: ColumnConstraints<Byte?>.() -> Unit = {}): Column<Byte?>
  public fun short(name: String, block: ColumnConstraints<Short>.() -> Unit = {}): Column<Short>
  public fun optShort(
    name: String,
    block: ColumnConstraints<Short?>.() -> Unit = {}
  ): Column<Short?>

  public fun integer(name: String, block: ColumnConstraints<Int>.() -> Unit = {}): Column<Int>
  public fun optInteger(name: String, block: ColumnConstraints<Int?>.() -> Unit = {}): Column<Int?>
  public fun long(name: String, block: ColumnConstraints<Long>.() -> Unit = {}): Column<Long>
  public fun optLong(name: String, block: ColumnConstraints<Long?>.() -> Unit = {}): Column<Long?>
  public fun float(name: String, block: ColumnConstraints<Float>.() -> Unit = {}): Column<Float>
  public fun optFloat(
    name: String,
    block: ColumnConstraints<Float?>.() -> Unit = {}
  ): Column<Float?>

  public fun double(name: String, block: ColumnConstraints<Double>.() -> Unit = {}): Column<Double>
  public fun optDouble(
    name: String,
    block: ColumnConstraints<Double?>.() -> Unit = {}
  ): Column<Double?>

  public fun text(name: String, block: ColumnConstraints<String>.() -> Unit = {}): Column<String>
  public fun optText(
    name: String,
    block: ColumnConstraints<String?>.() -> Unit = {}
  ): Column<String?>

  public fun blob(name: String, block: ColumnConstraints<Blob>.() -> Unit = {}): Column<Blob>
  public fun optBlob(name: String, block: ColumnConstraints<Blob?>.() -> Unit = {}): Column<Blob?>

  @ExperimentalUnsignedTypes
  public fun ubyte(name: String, block: ColumnConstraints<UByte>.() -> Unit = {}): Column<UByte>

  @ExperimentalUnsignedTypes
  public fun optUbyte(
    name: String,
    block: ColumnConstraints<UByte?>.() -> Unit = {}
  ): Column<UByte?>

  @ExperimentalUnsignedTypes
  public fun uShort(name: String, block: ColumnConstraints<UShort>.() -> Unit = {}): Column<UShort>

  @ExperimentalUnsignedTypes
  public fun optUshort(
    name: String,
    block: ColumnConstraints<UShort?>.() -> Unit = {}
  ): Column<UShort?>

  @ExperimentalUnsignedTypes
  public fun ulong(name: String, block: ColumnConstraints<ULong>.() -> Unit = {}): Column<ULong>

  @ExperimentalUnsignedTypes
  public fun optUlong(
    name: String,
    block: ColumnConstraints<ULong?>.() -> Unit = {}
  ): Column<ULong?>

  public fun bool(name: String, block: ColumnConstraints<Boolean>.() -> Unit = {}): Column<Boolean>
  public fun optBool(
    name: String,
    block: ColumnConstraints<Boolean?>.() -> Unit = {}
  ): Column<Boolean?>

  public fun <T : Enum<T>> enumeration(
    name: String,
    klass: KClass<T>,
    block: ColumnConstraints<T>.() -> Unit = {}
  ): Column<T>

  public fun <T : Enum<T>> enumerationByName(
    name: String,
    klass: KClass<T>,
    block: ColumnConstraints<T>.() -> Unit = {}
  ): Column<T>
}
