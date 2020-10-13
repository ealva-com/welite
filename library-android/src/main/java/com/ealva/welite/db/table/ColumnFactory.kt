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
  public fun byte(name: String, block: SetConstraints<Byte> = {}): Column<Byte>
  public fun optByte(name: String, block: SetConstraints<Byte?> = {}): Column<Byte?>
  public fun short(name: String, block: SetConstraints<Short> = {}): Column<Short>
  public fun optShort(name: String, block: SetConstraints<Short?> = {}): Column<Short?>
  public fun integer(name: String, block: SetConstraints<Int> = {}): Column<Int>
  public fun optInteger(name: String, block: SetConstraints<Int?> = {}): Column<Int?>
  public fun long(name: String, block: SetConstraints<Long> = {}): Column<Long>
  public fun optLong(name: String, block: SetConstraints<Long?> = {}): Column<Long?>
  public fun float(name: String, block: SetConstraints<Float> = {}): Column<Float>
  public fun optFloat(name: String, block: SetConstraints<Float?> = {}): Column<Float?>
  public fun double(name: String, block: SetConstraints<Double> = {}): Column<Double>
  public fun optDouble(name: String, block: SetConstraints<Double?> = {}): Column<Double?>
  public fun text(name: String, block: SetConstraints<String> = {}): Column<String>
  public fun optText(name: String, block: SetConstraints<String?> = {}): Column<String?>
  public fun blob(name: String, block: SetConstraints<Blob> = {}): Column<Blob>
  public fun optBlob(name: String, block: SetConstraints<Blob?> = {}): Column<Blob?>
  @ExperimentalUnsignedTypes
  public fun ubyte(name: String, block: SetConstraints<UByte> = {}): Column<UByte>
  @ExperimentalUnsignedTypes
  public fun optUbyte(name: String, block: SetConstraints<UByte?> = {}): Column<UByte?>
  @ExperimentalUnsignedTypes
  public fun uShort(name: String, block: SetConstraints<UShort> = {}): Column<UShort>
  @ExperimentalUnsignedTypes
  public fun optUshort(name: String, block: SetConstraints<UShort?> = {}): Column<UShort?>
  @ExperimentalUnsignedTypes
  public fun ulong(name: String, block: SetConstraints<ULong> = {}): Column<ULong>
  @ExperimentalUnsignedTypes
  public fun optUlong(name: String, block: SetConstraints<ULong?> = {}): Column<ULong?>
  public fun bool(name: String, block: SetConstraints<Boolean> = {}): Column<Boolean>
  public fun optBool(name: String, block: SetConstraints<Boolean?> = {}): Column<Boolean?>
  public fun <T : Enum<T>> enumeration(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T> = {}
  ): Column<T>
  public fun <T : Enum<T>> enumerationByName(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T> = {}
  ): Column<T>
}
