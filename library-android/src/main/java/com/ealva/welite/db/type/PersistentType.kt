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

@file:Suppress("UNCHECKED_CAST")

package com.ealva.welite.db.type

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.reflect.KClass

internal object DefaultValueMarker {
  override fun toString(): String = "DEFAULT"
}

fun Any.cannotConvert(convertedType: String) {
  throw IllegalArgumentException(
    "Cannot convert type:${this::class.qualifiedName} value:$this to $convertedType"
  )
}

/**
 * A PersistentType represents a type that decouples the Kotlin type from the underlying
 * database type. The generic parameter [T] is the Kotlin type, while the underlying type is
 * described by [sqlType], eg. INTEGER, TEXT, REAL... Instances are able to convert between the
 * types, bind values in a [Bindable] and read values from a [Row].
 *
 * Also [nullable],the concept of "nullability", does the underlying database column
 * allow null values, resides here.
 *
 * The type parameter [T] is not specified as bound by Any to allow for null. Since by default
 * [T] is nullable, implementations support null but subclasses can tighten the contract to
 * remove nullability. Instantiating a "Nullable????" class with a non-null type provides the
 * non-null version. Typically the compiler can infer the type given the left hand side.
 *
 * The standard SQLite SQL types are supported along with some simple 1 column translation,
 * such as UUID and Enumerations (by ordinal or name).
 */
interface PersistentType<T> {
  /**
   * The type as it appears in SQL. "INTEGER", "REAL", "TEXT", "BLOB"
   */
  val sqlType: String

  /** True if the underlying SQL type is INTEGER */
  val isIntegerType: Boolean

  /** True if null is valid for this type instance. A Column has a PersistentType */
  var nullable: Boolean

  /**
   * Binds a value into [bindable] at [index]. If [value] is null it
   * will be bound as null, ie. [Bindable.bindNull]
   */
  fun bind(bindable: Bindable, index: Int, value: Any?)

  /**
   * Read a value of this type [T] from [row] at the given [columnIndex]
   */
  fun columnValue(row: Row, columnIndex: Int): T?

  fun valueToString(value: Any?): String

  fun asNullable(): PersistentType<T?>
}

private val LOG by lazyLogger(PersistentType::class)
private const val NULL_NOT_ALLOWED_MSG: String = "Value at index=%d null but column is not nullable"

abstract class BasePersistentType<T>(
  override val sqlType: String,
  override var nullable: Boolean = true
) : PersistentType<T> {
  override val isIntegerType: Boolean
    get() = false

  final override fun bind(bindable: Bindable, index: Int, value: Any?) {
    value?.let { doBind(bindable, index, it) } ?: bindable.bindNullIfNullable(index)
  }

  abstract fun doBind(bindable: Bindable, index: Int, value: Any)

  private fun Bindable.bindNullIfNullable(index: Int) {
    check(nullable) { "Cannot assign null to non-nullable column" }
    bindNull(index)
  }

  override fun valueToString(value: Any?): String = when (value) {
    null -> {
      check(nullable) { "NULL in non-nullable column" }
      "NULL"
    }
    DefaultValueMarker -> "DEFAULT"
    is Iterable<*> -> value.joinToString(",", transform = ::valueToString)
    else -> nonNullValueToString(value)
  }

  protected open fun notNullValueToDB(value: Any): Any = value

  protected open fun nonNullValueToString(value: Any): String = notNullValueToDB(value).toString()

  override fun toString(): String = sqlType
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BasePersistentType<*>

    if (nullable != other.nullable) return false
    return true
  }

  override fun hashCode(): Int = 31 * javaClass.hashCode() + nullable.hashCode()

  override fun columnValue(row: Row, columnIndex: Int): T? {
    return if (row.isNull(columnIndex)) {
      // TODO should throw if not nullable?
      if (!nullable) LOG.e { it(NULL_NOT_ALLOWED_MSG, columnIndex) }
      null
    } else {
      row.readColumnValue(columnIndex)
    }
  }

  abstract fun Row.readColumnValue(index: Int): T

  override fun asNullable(): PersistentType<T?> {
    return clone().apply { nullable = true }
  }

  abstract fun clone(): PersistentType<T?>
}

abstract class BaseIntegerPersistentType<T> : BasePersistentType<T>("INTEGER") {
  override val isIntegerType: Boolean
    get() = true
}

abstract class BaseRealPersistentType<T> : BasePersistentType<T>("REAL")

open class NullableBytePersistentType<T : Byte?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Byte -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toByte().toLong())
      is String -> bindable.bind(index, value.toByte().toLong())
      else -> value.cannotConvert("Byte")
    }
  }

  override fun Row.readColumnValue(index: Int) = getShort(index).toByte() as T

  override fun clone(): PersistentType<T?> {
    return NullableBytePersistentType()
  }
}

@ExperimentalUnsignedTypes
open class NullableUBytePersistentType<T : UByte?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UByte -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUByte().toLong())
      is String ->
        value.toUByteOrNull()?.toLong()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("UByte")
      else -> value.cannotConvert("UByte")
    }
  }

  override fun Row.readColumnValue(index: Int) = getLong(index).toUByte() as T

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UByte) value.toLong() else value
  }

  override fun clone(): PersistentType<T?> {
    return NullableUBytePersistentType()
  }
}

@ExperimentalUnsignedTypes class UBytePersistentType : NullableUBytePersistentType<UByte>()

open class NullableShortPersistentType<T : Short?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = getShort(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Short -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toShort().toLong())
      is String -> bindable.bind(index, value.toShort().toLong())
      else -> value.cannotConvert("Short")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableShortPersistentType()
  }
}

@ExperimentalUnsignedTypes
open class NullableUShortPersistentType<T : UShort?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toUShort() as T

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UShort) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UShort -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUShort().toLong())
      is String ->
        value.toUShortOrNull()?.toLong()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("UShort")
      else -> value.cannotConvert("UShort")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableUShortPersistentType()
  }
}

open class NullableIntegerPersistentType<T : Int?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = getInt(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Int -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toInt().toLong())
      is String ->
        value.toIntOrNull()?.toLong()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("Int")
      else -> value.cannotConvert("Int")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableIntegerPersistentType()
  }
}

@ExperimentalUnsignedTypes
open class NullableUIntegerPersistentType<T : UInt?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toUInt() as T

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UInt) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UInt -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUInt().toLong())
      is String ->
        value.toUIntOrNull()?.toLong()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("UInt")
      else -> value.cannotConvert("UInt")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableUIntegerPersistentType()
  }
}

open class NullableLongPersistentType<T : Long?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = getLong(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Long -> bindable.bind(index, value)
      is Number -> bindable.bind(index, value.toLong())
      is String -> bindable.bind(index, value.toLong())
      else -> value.cannotConvert("Long")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableLongPersistentType()
  }
}

@ExperimentalUnsignedTypes
open class NullableULongPersistentType<T : ULong?> : BaseIntegerPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = getLong(index).toULong() as T

  override fun notNullValueToDB(value: Any): Any {
    return if (value is ULong) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is ULong -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toULong().toLong())
      is String -> bindable.bind(index, value.toULong().toLong())
      else -> value.cannotConvert("ULong")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableULongPersistentType()
  }
}

open class NullableFloatPersistentType<T : Float?> : BaseRealPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = getFloat(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Float -> bindable.bind(index, value.toDouble())
      is Number -> bindable.bind(index, value.toDouble())
      is String ->
        value.toFloatOrNull()?.toDouble()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("Float")
      else -> value.cannotConvert("Float")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableFloatPersistentType()
  }
}

open class NullableDoublePersistentType<T : Double?> : BaseRealPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = getDouble(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Double -> bindable.bind(index, value)
      is Number -> bindable.bind(index, value.toDouble())
      is String ->
        value.toDoubleOrNull()?.let { bindable.bind(index, it) }
          ?: value.cannotConvert("Double")
      else -> value.cannotConvert("Double")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableDoublePersistentType()
  }
}

open class NullableStringPersistentType<T : String?> : BasePersistentType<T>("TEXT") {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int) = getString(index) as T

  override fun nonNullValueToString(value: Any): String = buildString {
    append('\'')
    append(escape(value.toString()))
    append('\'')
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is String -> bindable.bind(index, value)
      is ByteArray -> bindable.bind(index, String(value))
      else -> bindable.bind(index, value.toString())
    }
  }

  private fun escape(value: String): String =
    value.map { charToEscapedMap[it] ?: it }.joinToString("")

  /**
   * Escape single quote, new line, and carriage return. Don't bother with double quote as
   * we will wrap the result will be wrapped in single quotes
   */
  private val charToEscapedMap = mapOf(
    '\'' to "\'\'",
    '\r' to "\\r",
    '\n' to "\\n"
  )

  override fun clone(): PersistentType<T?> {
    return NullableStringPersistentType()
  }
}

open class NullableBlobPersistentType<T : Blob?> : BasePersistentType<T>("BLOB") {
  override fun Row.readColumnValue(index: Int): T = Blob(getBlob(index)) as T

  override fun notNullValueToDB(value: Any): ByteArray {
    return (value as Blob).bytes
  }

  override fun nonNullValueToString(value: Any): String = "?"

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Blob -> bindable.bind(index, value.bytes)
      is ByteArray -> bindable.bind(index, value)
      is InputStream -> bindable.bind(index, value.readBytes())
      else -> value.cannotConvert("String")
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableBlobPersistentType()
  }
}

private const val SIZE_UUID = 16

open class NullableUUIDPersistentType<T : UUID?> internal constructor(
  private val blobType: NullableBlobPersistentType<Blob>
) : BasePersistentType<T>(blobType.sqlType) {
  private fun ByteBuffer.getUuid(): UUID {
    return UUID(long, long)
  }

  private fun ByteBuffer.putUuid(uuid: UUID): ByteBuffer = apply {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
  }

  private fun UUID.toBlob(): Blob = Blob(
    ByteBuffer.allocate(SIZE_UUID)
      .putUuid(this)
      .array()
  )

  private val uuidRegexp = Regex(
    "[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}",
    RegexOption.IGNORE_CASE
  )

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UUID -> blobType.bind(
        bindable,
        index,
        value.toBlob()
      )
      is String -> blobType.bind(bindable, index, UUID.fromString(value).toBlob())
      is ByteArray -> blobType.bind(
        bindable,
        index,
        ByteBuffer.wrap(value).let { UUID(it.long, it.long) }.toBlob()
      )
      else -> value.cannotConvert("UUID")
    }
  }

  override fun Row.readColumnValue(index: Int) = ByteBuffer.wrap(getBlob(index)).getUuid() as T

  override fun notNullValueToDB(value: Any): Any {
    val uuid = valueToUUID(value)
    return ByteBuffer.allocate(SIZE_UUID)
      .putLong(uuid.mostSignificantBits)
      .putLong(uuid.leastSignificantBits)
      .array()
  }

  override fun nonNullValueToString(value: Any): String = "'${valueToUUID(value)}'"

  private fun valueToUUID(value: Any): UUID = when (value) {
    is UUID -> value
    is String -> {
      require(value.matches(uuidRegexp)) { "String not in form of UUID" }
      UUID.fromString(value)
    }
    is ByteArray ->
      ByteBuffer.wrap(value)
        .let { UUID(it.long, it.long) }
    else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
  }

  companion object {
    operator fun <T : UUID?> invoke(): NullableUUIDPersistentType<T> =
      NullableUUIDPersistentType(NullableBlobPersistentType())
  }

  override fun clone(): PersistentType<T?> {
    return NullableUUIDPersistentType()
  }
}

open class NullableBooleanPersistentType<T : Boolean?> : BaseIntegerPersistentType<T>() {
  override fun Row.readColumnValue(index: Int) = (getInt(index) != 0) as T
  override fun nonNullValueToString(value: Any): String = (value as Boolean).toStatementString()
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Boolean -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, if (value != 0) 1L else 0L)
      else -> bindable.bind(index, value.toString().toBoolean().toLong())
    }
  }

  override fun clone(): PersistentType<T?> {
    return NullableBooleanPersistentType()
  }
}

/**
 * No nullable version of this, use a special enum member to denote "absent" or "unknown"
 */
class EnumerationPersistentType<T : Enum<T>>(
  private val klass: KClass<T>
) : BasePersistentType<T>("INTEGER") {

  private val enums = checkNotNull(klass.java.enumConstants) { "${klass.qualifiedName} not Enum" }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    return enums[getInt(index)]
  }

  override fun notNullValueToDB(value: Any): Int = when (value) {
    is Int -> value
    is Enum<*> -> value.ordinal
    else -> error(
      "$value of type ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}"
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as EnumerationPersistentType<*>

    if (klass != other.klass) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + klass.hashCode()
    return result
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Enum<*> -> {
        require(klass.isInstance(value))
        bindable.bind(index, value.ordinal.toLong())
      }
      is Number -> {
        val ordinal = value.toInt()
        if (ordinal in enums.indices) {
          bindable.bind(index, ordinal.toLong())
        } else value.cannotConvert(klass.qualifiedName ?: klass.toString())
      }
      else -> value.cannotConvert(klass.qualifiedName ?: klass.toString())
    }
  }

  override fun clone(): PersistentType<T?> {
    throw NotImplementedError("This type ${javaClass.canonicalName} is not nullable")
  }
}

/**
 * No nullable version of this, use a special enum member to denote "absent" or "unknown"
 */
class EnumerationNamePersistentType<T : Enum<T>>(
  val klass: KClass<T>
) : BasePersistentType<T>("TEXT") {
  private val enums = checkNotNull(klass.java.enumConstants) { "${klass.qualifiedName} not Enum" }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    return enums.first { it.name == getString(index) }
  }

  override fun notNullValueToDB(value: Any): String = when (value) {
    is String -> value
    is Enum<*> -> value.name
    else -> error(
      "$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}"
    )
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as EnumerationNamePersistentType<*>

    if (klass != other.klass) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + klass.hashCode()
    return result
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Enum<*> -> {
        require(klass.isInstance(value))
        bindable.bind(index, value.name)
      }
      is String ->
        enums.firstOrNull { it.name == value }?.let { bindable.bind(index, it.name) }
          ?: value.cannotConvert(klass.qualifiedName ?: klass.toString())
      else -> value.cannotConvert(klass.qualifiedName ?: klass.toString())
    }
  }

  override fun clone(): PersistentType<T?> {
    throw NotImplementedError("This type ${javaClass.canonicalName} is not nullable")
  }
}
