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

package com.ealva.welite.db.type

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.log.WeLiteLog
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.util.UUID
import kotlin.reflect.KClass

internal object DefaultValueMarker {
  override fun toString(): String = "DEFAULT"
}

private inline fun <T> Any.cantConvert(convertedType: () -> String): T {
  throw IllegalArgumentException(
    "Cannot convert type:${this::class.qualifiedName} value:$this to ${convertedType()}"
  )
}

private fun Any.cantConvert(convertedType: String) {
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
public interface PersistentType<T> {
  /**
   * The type as it appears in SQL. "INTEGER", "REAL", "TEXT", "BLOB"
   */
  public val sqlType: String

  /** True if the underlying SQL type is INTEGER */
  public val isIntegerType: Boolean

  /** True if null is valid for this type instance. A Column has a PersistentType */
  public var nullable: Boolean

  /**
   * Binds a value into [bindable] at [index]. If [value] is null it
   * will be bound as null, ie. [Bindable.bindNull]
   */
  public fun bind(bindable: Bindable, index: Int, value: Any?)

  /**
   * Read a value of this type [T] from [row] at the given [columnIndex]
   */
  public fun columnValue(row: Row, columnIndex: Int): T?

  public fun valueToString(value: Any?, quoteAsLiteral: Boolean): String

  public fun asNullable(): PersistentType<T?>
}

private val LOG by lazyLogger(PersistentType::class, WeLiteLog.marker)
private const val NULL_NOT_ALLOWED_MSG: String = "Value at index=%d null but column is not nullable"

public abstract class BasePersistentType<T, A>(
  override val sqlType: String,
  override var nullable: Boolean = true
) : PersistentType<T> {
  override val isIntegerType: Boolean
    get() = false

  final override fun bind(bindable: Bindable, index: Int, value: Any?): Unit =
    value?.let { doBind(bindable, index, it) } ?: bindable.bindNullIfNullable(index)

  public abstract fun doBind(bindable: Bindable, index: Int, value: Any)

  private fun Bindable.bindNullIfNullable(index: Int) {
    require(nullable) { "Cannot assign null to non-nullable column" }
    bindNull(index)
  }

  private fun valueToStringQuotedAsLiteral(value: Any?): String = valueToString(value, true)

  private fun valueToStringUnquoted(value: Any?): String = valueToString(value, false)

  override fun valueToString(value: Any?, quoteAsLiteral: Boolean): String = when (value) {
    null -> {
      require(nullable) { "NULL in non-nullable column" }
      "NULL"
    }
    DefaultValueMarker -> DefaultValueMarker.toString()
    is Iterable<*> -> value.joinToString(
      ",",
      transform = if (quoteAsLiteral) ::valueToStringQuotedAsLiteral else ::valueToStringUnquoted
    )
    else -> nonNullValueToString(value, quoteAsLiteral)
  }

  protected abstract fun notNullValueToDB(value: Any): A

  protected open fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    notNullValueToDB(value).toString()

  protected fun String.maybeQuote(quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'$this'" else this

  override fun toString(): String = sqlType

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as BasePersistentType<*, *>

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

  public abstract fun Row.readColumnValue(index: Int): T

  override fun asNullable(): PersistentType<T?> = clone().apply { nullable = true }

  public abstract fun clone(): PersistentType<T?>
}

public abstract class BaseIntegerPersistentType<T> : BasePersistentType<T, Long>("INTEGER") {
  override val isIntegerType: Boolean
    get() = true
}

private fun Any.byteValueToLong(): Long = when (this) {
  is Byte -> toLong()
  is Number -> toByte().toLong()
  is String -> toByteOrNull()?.toLong() ?: cantConvert { "Byte" }
  else -> cantConvert { "Byte" }
}

public class BytePersistentType<T : Byte?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.byteValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getShort(index).toByte() as T

  override fun notNullValueToDB(value: Any): Long = value.byteValueToLong()

  override fun clone(): PersistentType<T?> = BytePersistentType()
}

private fun Any.ubyteValueToLong(): Long = when (this) {
  is UByte -> toLong()
  is Number -> toLong().toUByte().toLong()
  is String -> toUByteOrNull()?.toLong() ?: cantConvert { "UByte" }
  else -> cantConvert { "UByte" }
}

@ExperimentalUnsignedTypes
public class UBytePersistentType<T : UByte?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.ubyteValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index).toUByte() as T

  override fun notNullValueToDB(value: Any): Long = value.ubyteValueToLong()

  override fun clone(): PersistentType<T?> = UBytePersistentType()
}

private fun Any.shortValueToLong(): Long = when (this) {
  is Short -> toLong()
  is Number -> toShort().toLong()
  is String -> toShortOrNull()?.toLong() ?: cantConvert { "Short" }
  else -> cantConvert { "Short" }
}

public class ShortPersistentType<T : Short?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.shortValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getShort(index) as T

  override fun notNullValueToDB(value: Any): Long = value.shortValueToLong()

  override fun clone(): PersistentType<T?> = ShortPersistentType()
}

private fun Any.ushortValueToLong(): Long = when (this) {
  is UShort -> toLong()
  is Number -> toLong().toUShort().toLong()
  is String -> toUShortOrNull()?.toLong() ?: cantConvert { "UShort" }
  else -> cantConvert { "UShort" }
}

@ExperimentalUnsignedTypes
public class UShortPersistentType<T : UShort?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.ushortValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index).toUShort() as T

  override fun notNullValueToDB(value: Any): Long = value.ushortValueToLong()

  override fun clone(): PersistentType<T?> = UShortPersistentType()
}

private fun Any.intValueToLong(): Long = when (this) {
  is Int -> toLong()
  is Number -> toInt().toLong()
  is String -> toIntOrNull()?.toLong() ?: cantConvert { "Int" }
  else -> cantConvert { "Int" }
}

public class IntegerPersistentType<T : Int?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.intValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getInt(index) as T

  override fun notNullValueToDB(value: Any): Long = value.intValueToLong()

  override fun clone(): PersistentType<T?> = IntegerPersistentType()
}

private fun Any.uintValueToLong(): Long = when (this) {
  is UInt -> toLong()
  is Number -> toLong().toUInt().toLong()
  is String -> toUIntOrNull()?.toLong() ?: cantConvert { "UInt" }
  else -> cantConvert { "UInt" }
}

@ExperimentalUnsignedTypes
public class UIntegerPersistentType<T : UInt?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.uintValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index).toUInt() as T

  override fun notNullValueToDB(value: Any): Long = value.uintValueToLong()

  override fun clone(): PersistentType<T?> = UIntegerPersistentType()
}

private fun Any.longValueToLong(): Long = when (this) {
  is Long -> this
  is Number -> toLong()
  is String -> toLong()
  else -> cantConvert { "Long" }
}

public class LongPersistentType<T : Long?> : BaseIntegerPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.longValueToLong())

  override fun notNullValueToDB(value: Any): Long = value.longValueToLong()

  override fun clone(): PersistentType<T?> = LongPersistentType()
}

private fun Any.ulongValueToLong(): Long = when (this) {
  is ULong -> toLong()
  is Number -> toLong().toULong().toLong()
  is String -> toULongOrNull()?.toLong() ?: cantConvert { "ULong" }
  else -> cantConvert { "ULong" }
}

@ExperimentalUnsignedTypes
public class ULongPersistentType<T : ULong?> : BaseIntegerPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.ulongValueToLong())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getLong(index).toULong() as T

  override fun notNullValueToDB(value: Any): Long = value.ulongValueToLong()

  override fun clone(): PersistentType<T?> = ULongPersistentType()
}

public abstract class BaseRealPersistentType<T> : BasePersistentType<T, Double>("REAL")

private fun Any.floatValueToDouble(): Double = when (this) {
  is Float -> toDouble()
  is Number -> toDouble()
  is String -> toFloatOrNull()?.toDouble() ?: cantConvert { "Float" }
  else -> cantConvert { "Float" }
}

public class FloatPersistentType<T : Float?> : BaseRealPersistentType<T>() {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.floatValueToDouble())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getFloat(index) as T

  override fun notNullValueToDB(value: Any): Double = value.floatValueToDouble()

  override fun clone(): PersistentType<T?> = FloatPersistentType()
}

private fun Any.doubleValueToDouble(): Double = when (this) {
  is Double -> this
  is Number -> toDouble()
  is String -> toDoubleOrNull() ?: cantConvert { "Double" }
  else -> cantConvert { "Double" }
}

public class DoublePersistentType<T : Double?> : BaseRealPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getDouble(index) as T

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.doubleValueToDouble())

  override fun notNullValueToDB(value: Any): Double = value.doubleValueToDouble()

  override fun clone(): PersistentType<T?> = DoublePersistentType()
}

public class StringPersistentType<T : String?> : BasePersistentType<T, String>("TEXT") {
  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.toString())

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = getString(index) as T

  override fun notNullValueToDB(value: Any): String = value.toString()

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String = buildStr {
    if (quoteAsLiteral) {
      append('\'')
      append(escape(value.toString()))
      append('\'')
    } else {
      append(value.toString())
    }
  }

  private fun escape(value: String): String =
    value.map { charToEscapedMap[it] ?: it }.joinToString("")

  /**
   * Escape single quote, new line, and carriage return. Don't bother with double quote as
   * the result will be wrapped in single quotes
   */
  private val charToEscapedMap = mapOf('\'' to "\'\'", '\r' to "\\r", '\n' to "\\n")

  override fun clone(): PersistentType<T?> = StringPersistentType()
}

public open class BlobPersistentType<T : Blob?> : BasePersistentType<T, ByteArray>("BLOB") {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = Blob(getBlob(index)) as T

  override fun notNullValueToDB(value: Any): ByteArray = (value as Blob).bytes

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String = "?"

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit = when (value) {
    is Blob -> bindable.bind(index, value.bytes)
    is ByteArray -> bindable.bind(index, value)
    is InputStream -> bindable.bind(index, value.readBytes())
    else -> value.cantConvert { "Blob" }
  }

  override fun clone(): PersistentType<T?> = BlobPersistentType()
}

private const val SIZE_UUID = 16

public open class UUIDPersistentType<T : UUID?> internal constructor(
  private val blobType: BlobPersistentType<Blob>
) : BasePersistentType<T, ByteArray>(blobType.sqlType) {
  private fun ByteBuffer.getUuid(): UUID = UUID(long, long)

  private fun ByteBuffer.putUuid(uuid: UUID): ByteBuffer = apply {
    putLong(uuid.mostSignificantBits)
    putLong(uuid.leastSignificantBits)
  }

  private fun UUID.toBlob(): Blob = Blob(ByteBuffer.allocate(SIZE_UUID).putUuid(this).array())

  private val uuidRegexp = Regex(
    "[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}",
    RegexOption.IGNORE_CASE
  )

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit = when (value) {
    is UUID -> blobType.bind(bindable, index, value.toBlob())
    is String -> blobType.bind(bindable, index, UUID.fromString(value).toBlob())
    is ByteArray -> blobType.bind(
      bindable,
      index,
      ByteBuffer.wrap(value).let { UUID(it.long, it.long) }.toBlob()
    )
    else -> value.cantConvert("UUID")
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = ByteBuffer.wrap(getBlob(index)).getUuid() as T

  override fun notNullValueToDB(value: Any): ByteArray {
    val uuid = valueToUUID(value)
    return ByteBuffer.allocate(SIZE_UUID)
      .putLong(uuid.mostSignificantBits)
      .putLong(uuid.leastSignificantBits)
      .array()
  }

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'${valueToUUID(value)}'" else "${valueToUUID(value)}"

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

  public companion object {
    public operator fun <T : UUID?> invoke(): UUIDPersistentType<T> =
      UUIDPersistentType(BlobPersistentType())
  }

  override fun clone(): PersistentType<T?> = UUIDPersistentType()
}

private fun Any.booleanValueToLong(): Long = when (this) {
  is Boolean -> toLong()
  is Number -> if (toLong() == 0L) 0L else 1L
  else -> toString().toBoolean().toLong()
}

public open class BooleanPersistentType<T : Boolean?> : BaseIntegerPersistentType<T>() {
  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T = (getInt(index) != 0) as T

  override fun nonNullValueToString(
    value: Any,
    quoteAsLiteral: Boolean
  ): String = (value as Boolean).toStatementString()

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit =
    bindable.bind(index, value.booleanValueToLong())

  override fun clone(): PersistentType<T?> = BooleanPersistentType()

  override fun notNullValueToDB(value: Any): Long = value.booleanValueToLong()
}

/**
 * No nullable version of this, use a special enum member to denote "absent" or "unknown"
 */
public class EnumerationPersistentType<T : Enum<T>>(
  private val klass: KClass<T>
) : BaseIntegerPersistentType<T>() {

  private val enums = checkNotNull(klass.java.enumConstants) { "${klass.qualifiedName} not Enum" }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    return enums[getInt(index)]
  }

  override fun notNullValueToDB(value: Any): Long = when (value) {
    is Long -> value
    is Int -> value.toLong()
    is Enum<*> -> value.ordinal.toLong()
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

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit = when (value) {
    is Enum<*> -> {
      require(klass.isInstance(value))
      bindable.bind(index, value.ordinal.toLong())
    }
    is Number -> {
      val ordinal = value.toInt()
      if (ordinal in enums.indices) {
        bindable.bind(index, ordinal.toLong())
      } else value.cantConvert(klass.qualifiedName ?: klass.toString())
    }
    else -> value.cantConvert(klass.qualifiedName ?: klass.toString())
  }

  override fun clone(): PersistentType<T?> {
    throw NotImplementedError("This type ${javaClass.canonicalName} is not nullable")
  }
}

/**
 * No nullable version of this, use a special enum member to denote "absent" or "unknown"
 */
public class EnumerationNamePersistentType<T : Enum<T>>(
  private val klass: KClass<T>
) : BasePersistentType<T, String>("TEXT") {
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

  override fun doBind(bindable: Bindable, index: Int, value: Any): Unit = when (value) {
    is Enum<*> -> {
      require(klass.isInstance(value))
      bindable.bind(index, value.name)
    }
    is String -> {
      enums.firstOrNull { it.name == value }?.let { bindable.bind(index, it.name) }
        ?: value.cantConvert { klass.qualifiedName ?: klass.toString() }
    }
    else -> value.cantConvert { klass.qualifiedName ?: klass.toString() }
  }

  override fun clone(): PersistentType<T?> {
    throw NotImplementedError("This type ${javaClass.canonicalName} is not nullable")
  }
}

/**
 * Scale is fixed per column with unscaled value stored as a long. Since scale is the same for
 * all BigDecimal for this column, the database long nicely compares iff bit length < 64
 */
public class ScaledBigDecimalAsLongType<T : BigDecimal?> private constructor(
  private val scale: Int,
  private val roundingMode: RoundingMode,
  private val longType: LongPersistentType<Long>
) : BasePersistentType<T, Long>(longType.sqlType) {
  override fun doBind(bindable: Bindable, index: Int, value: Any) {
    bindBigDecimal(bindable, index, value.valueToBigDecimal())
  }

  private fun bindBigDecimal(bindable: Bindable, index: Int, value: BigDecimal) {
    val bindVal = if (value.scale() != scale) {
      LOG.w { it("Scale differs, value:${value.scale()} column:$scale. Adjusting scale.") }
      value.setScale(scale, roundingMode)
    } else {
      value
    }
    longType.bind(bindable, index, bindVal.unscaledValue().toLong())
  }

  @Suppress("UNCHECKED_CAST")
  override fun Row.readColumnValue(index: Int): T {
    val long = getLong(index)
    return BigDecimal(long.toBigInteger(), scale) as T
  }

  override fun notNullValueToDB(value: Any): Long {
    return value.valueToBigDecimal().toLong()
  }

  override fun nonNullValueToString(value: Any, quoteAsLiteral: Boolean): String =
    if (quoteAsLiteral) "'${value.valueToBigDecimal()}'" else "${value.valueToBigDecimal()}"

  private fun Any.valueToBigDecimal(): BigDecimal = when (this) {
    is BigDecimal -> this
    is Double -> toBigDecimal()
    is Float -> toBigDecimal()
    is Int -> toBigDecimal()
    is Long -> toBigDecimal()
    is String -> toBigDecimal()
    else -> toString().toBigDecimal()
  }.setScale(scale, roundingMode)

  public companion object {
    public operator fun <T : BigDecimal?> invoke(
      scale: Int,
      roundingMode: RoundingMode = RoundingMode.HALF_UP
    ): ScaledBigDecimalAsLongType<T> {
      return ScaledBigDecimalAsLongType(scale, roundingMode, LongPersistentType())
    }
  }

  override fun clone(): PersistentType<T?> = ScaledBigDecimalAsLongType(scale, roundingMode)
}
