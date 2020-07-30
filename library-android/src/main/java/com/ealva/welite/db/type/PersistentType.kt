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

/**
 * A PersistentType represents a type that decouples the Kotlin type from the underlying
 * database type. The generic parameter [T] is the Kotlin type, while the underlying type is
 * described by [sqlType], eg. INTEGER, TEXT, REAL... Instances are able to convert between the
 * types, bind values in a [Bindable] and read values from a [Row].
 *
 * Also [nullable],the concept of "nullability", resides here: does the underlying database column
 * allow null values.
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

  fun notNullValueToDB(value: Any): Any = value

  fun valueToString(value: Any?): String = when (value) {
    null -> {
      check(nullable) { "NULL in non-nullable column" }
      "NULL"
    }
    DefaultValueMarker -> "DEFAULT"
    is Iterable<*> -> value.joinToString(",", transform = ::valueToString)
    else -> nonNullValueToString(value)
  }

  fun nonNullValueToString(value: Any): String = notNullValueToDB(value).toString()
}

private val LOG by lazyLogger(PersistentType::class)
private const val NULL_NOT_ALLOWED_MSG: String = "Value at index=%d null but column is not nullable"

abstract class BasePersistentType<T>(
  override val sqlType: String,
  override var nullable: Boolean = true
) : PersistentType<T?> {
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

}

abstract class BaseIntegerPersistentType<T> : BasePersistentType<T>("INTEGER") {
  override val isIntegerType: Boolean
    get() = true
}

abstract class BaseRealPersistentType<T> : BasePersistentType<T>("REAL")

private fun cannotConvert(value: Any, convertedType: String) {
  throw IllegalArgumentException(
    "Cannot convert type:${value::class.qualifiedName} value:$value to $convertedType"
  )
}

class BytePersistentType : BaseIntegerPersistentType<Byte?>() {

  override fun Row.readColumnValue(index: Int) = getShort(index).toByte()

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Byte -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toByte().toLong())
      is String -> bindable.bind(index, value.toByte().toLong())
      else -> cannotConvert(value, "Byte")
    }
  }
}

@ExperimentalUnsignedTypes
class UBytePersistentType : BaseIntegerPersistentType<UByte?>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toUByte()

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UByte) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UByte -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUByte().toLong())
      is String -> value.toUByteOrNull()?.toLong()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "UByte")
      else -> cannotConvert(value, "UByte")
    }
  }
}

class ShortPersistentType : BaseIntegerPersistentType<Short?>() {
  override fun Row.readColumnValue(index: Int) = getShort(index)

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Short -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toShort().toLong())
      is String -> bindable.bind(index, value.toShort().toLong())
      else -> cannotConvert(value, "Short")
    }
  }
}

@ExperimentalUnsignedTypes
class UShortPersistentType : BaseIntegerPersistentType<UShort?>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toUShort()

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UShort) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UShort -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUShort().toLong())
      is String -> value.toUShortOrNull()?.toLong()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "UShort")
      else -> cannotConvert(value, "UShort")
    }
  }
}

class IntegerPersistentType : BaseIntegerPersistentType<Int?>() {
  override fun Row.readColumnValue(index: Int) = getInt(index)

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Int -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toInt().toLong())
      is String -> value.toIntOrNull()?.toLong()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "Int")
      else -> cannotConvert(value, "Int")
    }
  }
}

@ExperimentalUnsignedTypes
class UIntegerPersistentType : BaseIntegerPersistentType<UInt?>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toUInt()

  override fun notNullValueToDB(value: Any): Any {
    return if (value is UInt) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UInt -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toUInt().toLong())
      is String -> value.toUIntOrNull()?.toLong()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "UInt")
      else -> cannotConvert(value, "UInt")
    }
  }
}

class LongPersistentType : BaseIntegerPersistentType<Long?>() {
  override fun Row.readColumnValue(index: Int) = getLong(index)

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Long -> bindable.bind(index, value)
      is Number -> bindable.bind(index, value.toLong())
      is String -> bindable.bind(index, value.toLong())
      else -> cannotConvert(value, "Long")
    }
  }
}

@ExperimentalUnsignedTypes
class ULongPersistentType : BaseIntegerPersistentType<ULong?>() {
  override fun Row.readColumnValue(index: Int) = getLong(index).toULong()

  override fun notNullValueToDB(value: Any): Any {
    return if (value is ULong) value.toLong() else value
  }

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is ULong -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, value.toLong().toULong().toLong())
      is String -> bindable.bind(index, value.toULong().toLong())
      else -> cannotConvert(value, "ULong")
    }
  }
}

class FloatPersistentType : BaseRealPersistentType<Float?>() {
  override fun Row.readColumnValue(index: Int) = getFloat(index)

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Float -> bindable.bind(index, value.toDouble())
      is Number -> bindable.bind(index, value.toDouble())
      is String -> value.toFloatOrNull()?.toDouble()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "Float")
      else -> cannotConvert(value, "Float")
    }
  }
}

class DoublePersistentType : BaseRealPersistentType<Double?>() {
  override fun Row.readColumnValue(index: Int) = getDouble(index)

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Double -> bindable.bind(index, value)
      is Number -> bindable.bind(index, value.toDouble())
      is String -> value.toDoubleOrNull()?.let { bindable.bind(index, it) }
        ?: cannotConvert(value, "Double")
      else -> cannotConvert(value, "Double")
    }
  }
}

open class StringPersistentType : BasePersistentType<String?>("TEXT") {
  override fun Row.readColumnValue(index: Int) = getString(index)

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
}

open class BlobPersistentType : BasePersistentType<Blob?>("BLOB") {
  override fun Row.readColumnValue(index: Int): Blob {
    return Blob(getBlob(index))
  }

  override fun notNullValueToDB(value: Any): ByteArray {
    return (value as Blob).bytes
  }

  override fun nonNullValueToString(value: Any): String = "?"

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Blob -> bindable.bind(index, value.bytes)
      is ByteArray -> bindable.bind(index, value)
      is InputStream -> bindable.bind(index, value.readBytes())
      else -> cannotConvert(value, "String")
    }
  }
}

class UUIDPersistentType private constructor(
  private val blobColumn: BlobPersistentType
) : BasePersistentType<UUID?>(blobColumn.sqlType) {
  private fun ByteBuffer.getUuid(): UUID {
    return UUID(long, long)
  }

  private fun ByteBuffer.putUuid(uuid: UUID): ByteBuffer = apply {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
  }

  private fun UUID.toBlob(): Blob = Blob(
    ByteBuffer.allocate(16)
      .putUuid(this)
      .array()
  )

  private val uuidRegexp = Regex(
    "[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}",
    RegexOption.IGNORE_CASE
  )

  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is UUID -> blobColumn.bind(
        bindable,
        index,
        value.toBlob()
      )
      is String -> blobColumn.bind(bindable, index, UUID.fromString(value).toBlob())
      is ByteArray -> blobColumn.bind(
        bindable,
        index,
        ByteBuffer.wrap(value).let { UUID(it.long, it.long) }.toBlob()
      )
      else -> cannotConvert(value, "UUID")
    }
  }

  override fun Row.readColumnValue(index: Int): UUID {
    return ByteBuffer.wrap(getBlob(index)).getUuid()
  }

  override fun notNullValueToDB(value: Any): Any {
    val uuid = valueToUUID(value)
    return ByteBuffer.allocate(16)
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
    is ByteArray -> ByteBuffer.wrap(value)
      .let { UUID(it.long, it.long) }
    else -> error("Unexpected value of type UUID: ${value.javaClass.canonicalName}")
  }

  companion object {
    operator fun invoke(): UUIDPersistentType = UUIDPersistentType(BlobPersistentType())
  }
}

class BooleanPersistentType : BaseIntegerPersistentType<Boolean?>() {
  override fun Row.readColumnValue(index: Int): Boolean = getInt(index) != 0
  override fun nonNullValueToString(value: Any): String = (value as Boolean).toStatementString()
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    when (value) {
      is Boolean -> bindable.bind(index, value.toLong())
      is Number -> bindable.bind(index, if (value != 0) 1L else 0L)
      else -> bindable.bind(index, value.toString().toBoolean().toLong())
    }
  }
}

class EnumerationPersistentType<T : Enum<T>>(
  private val klass: KClass<T>
) : BasePersistentType<T?>("INTEGER") {

  private val enums = checkNotNull(klass.java.enumConstants) { "${klass.qualifiedName} not Enum" }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    return enums[getInt(index)]
  }

  override fun notNullValueToDB(value: Any): Int = when (value) {
    is Int -> value
    is Enum<*> -> value.ordinal
    else -> error("$value of type ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
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
        } else cannotConvert(value, klass.qualifiedName ?: klass.toString())
      }
      else -> cannotConvert(value, klass.qualifiedName ?: klass.toString())
    }
  }
}

class EnumerationNamePersistentType<T : Enum<T>>(
  val klass: KClass<T>
) : BasePersistentType<T?>("TEXT") {
  private val enums = checkNotNull(klass.java.enumConstants) { "${klass.qualifiedName} not Enum" }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    return enums.first { it.name == getString(index) }
  }

  override fun notNullValueToDB(value: Any): String = when (value) {
    is String -> value
    is Enum<*> -> value.name
    else -> error("$value of ${value::class.qualifiedName} is not valid for enum ${klass.qualifiedName}")
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
      is String -> enums.firstOrNull { it.name == value }?.let { bindable.bind(index, it.name) }
        ?: cannotConvert(value, klass.qualifiedName ?: klass.toString())
      else -> cannotConvert(value, klass.qualifiedName ?: klass.toString())

    }
  }
}
