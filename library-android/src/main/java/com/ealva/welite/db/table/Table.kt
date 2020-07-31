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

package com.ealva.welite.db.table

import android.database.sqlite.SQLiteException
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.invoke
import com.ealva.welite.db.type.Blob
import com.ealva.welite.db.type.BlobPersistentType
import com.ealva.welite.db.type.BooleanPersistentType
import com.ealva.welite.db.type.BytePersistentType
import com.ealva.welite.db.type.DoublePersistentType
import com.ealva.welite.db.type.EnumerationNamePersistentType
import com.ealva.welite.db.type.EnumerationPersistentType
import com.ealva.welite.db.type.FloatPersistentType
import com.ealva.welite.db.type.IntegerPersistentType
import com.ealva.welite.db.type.LongPersistentType
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.ShortPersistentType
import com.ealva.welite.db.type.StringPersistentType
import com.ealva.welite.db.type.UBytePersistentType
import com.ealva.welite.db.type.ULongPersistentType
import com.ealva.welite.db.type.UShortPersistentType
import com.ealva.welite.db.type.UUIDPersistentType
import java.util.UUID
import kotlin.reflect.KClass

private val LOG by lazyLogger(Table::class)

typealias SetConstraints<T> = ColumnConstraints<T>.() -> Unit

/**
 * Table base class typically instantiated as a singleton in the style:
 * ```
 * object Users : Table() {
 *   val id = integer("id") { primaryKey() }
 *   val name = text("name") { notNull() }
 * }
 * ```
 *
 * @param name optional table name, defaulting to the class name with any "Table" suffix removed.
 */
abstract class Table(name: String = "", systemTable: Boolean = false) : ColumnSet {
  open val tableName: String =
    (if (name.isNotEmpty()) name else this.javaClass.simpleName.removeSuffix("Table")).also { name ->
      require(systemTable || !name.startsWith(RESERVED_PREFIX)) {
        "Invalid Table name '$name', must not start with $RESERVED_PREFIX"
      }
    }

  open val identity: Identity by lazy { tableName.asIdentity() }

  private val _columns = mutableListOf<Column<*>>()
  override val columns: List<Column<*>>
    get() = _columns

  private val indices = mutableListOf<Index>()
  private val checkConstraints = mutableListOf<Pair<String, Op<Boolean>>>()

  private val indicesDDL: List<String>
    get() = indices.flatMap { it.createStatement() }

  override fun appendTo(sqlBuilder: SqlBuilder) = sqlBuilder { append(identity) }

  @ExperimentalUnsignedTypes
  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>?,
    otherColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join = Join(this, joinTo, joinType, thisColumn, otherColumn, additionalConstraint)

  override infix fun innerJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.INNER)
  override infix fun leftJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.LEFT)
  override infix fun crossJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.CROSS)
  override fun naturalJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.NATURAL)

  inner class PrimaryKey(
    firstColumn: Column<*>,
    vararg remainingColumns: Column<*>,
    val name: String = "pk_$tableName"
  ) {
    fun identity() = name.asIdentity()

    val columns: List<Column<*>> = mutableListOf(firstColumn, *remainingColumns).apply {
      checkMultipleDeclaration()
      forEach { column -> column.markPrimaryKey() }
      sortWith(compareBy { !it.isAutoInc })
    }

    private fun checkMultipleDeclaration() {
      this@Table.columnDefiningPrimaryKey?.let { column ->
        error("Column ${column.name} already defines the primary key")
      }
    }
  }

  internal val columnDefiningPrimaryKey: Column<*>?
    get() = columns.find { it.definesPrimaryKey }

  open val primaryKey: PrimaryKey? = null

//  private fun getPrimaryKeyColumns(): List<Column<*>>? = columns
//    .filter { it.indexInPK != null }
//    .sortedWith(compareBy({ !it.isAutoInc }, { it.indexInPK }))
//    .takeIf { it.isNotEmpty() }

  protected fun byte(name: String, block: SetConstraints<Byte> = {}): Column<Byte> =
    registerColumn(name, BytePersistentType(), block)

  protected fun nullableByte(name: String, block: SetConstraints<Byte?> = {}): Column<Byte?> =
    registerOptColumn(name, BytePersistentType(), block)

  protected fun short(name: String, block: SetConstraints<Short> = {}): Column<Short> =
    registerColumn(name, ShortPersistentType(), block)

  protected fun nullableShort(name: String, block: SetConstraints<Short?> = {}): Column<Short?> =
    registerOptColumn(name, ShortPersistentType(), block)

  protected fun integer(name: String, block: SetConstraints<Int> = {}): Column<Int> =
    registerColumn(name, IntegerPersistentType(), block)

  protected fun nullableInteger(name: String, block: SetConstraints<Int?> = {}): Column<Int?> =
    registerColumn(name, IntegerPersistentType(), block)

  protected fun long(name: String, block: SetConstraints<Long> = {}): Column<Long> =
    registerColumn(name, LongPersistentType(), block)

  protected fun nullableLong(name: String, block: SetConstraints<Long?> = {}): Column<Long?> =
    registerColumn(name, LongPersistentType(), block)

  protected fun float(name: String, block: SetConstraints<Float> = {}): Column<Float> =
    registerColumn(name, FloatPersistentType(), block)

  protected fun nullableFloat(name: String, block: SetConstraints<Float?> = {}): Column<Float?> =
    registerColumn(name, FloatPersistentType(), block)

  protected fun double(name: String, block: SetConstraints<Double> = {}): Column<Double> =
    registerColumn(name, DoublePersistentType(), block)

  protected fun nullableDouble(name: String, block: SetConstraints<Double?> = {}): Column<Double?> =
    registerColumn(name, DoublePersistentType(), block)

  protected fun text(name: String, block: SetConstraints<String> = {}): Column<String> =
    registerColumn(name, StringPersistentType(), block)

  protected fun nullableText(name: String, block: SetConstraints<String?> = {}): Column<String?> =
    registerOptColumn(name, StringPersistentType(), block)

  protected fun blob(name: String, block: SetConstraints<Blob> = {}): Column<Blob> =
    registerColumn(name, BlobPersistentType(), block)

  protected fun nullableBlob(name: String, block: SetConstraints<Blob?> = {}): Column<Blob?> =
    registerOptColumn(name, BlobPersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun ubyte(name: String, block: SetConstraints<UByte> = {}): Column<UByte> =
    registerColumn(name, UBytePersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun nullableUbyte(name: String, block: SetConstraints<UByte?> = {}): Column<UByte?> =
    registerOptColumn(name, UBytePersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun uShort(name: String, block: SetConstraints<UShort> = {}): Column<UShort> =
    registerColumn(name, UShortPersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun nullableUshort(name: String, block: SetConstraints<UShort?> = {}): Column<UShort?> =
    registerColumn(name, UShortPersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun ulong(name: String, block: SetConstraints<ULong> = {}): Column<ULong> =
    registerColumn(name, ULongPersistentType(), block)

  @ExperimentalUnsignedTypes
  protected fun nullableUlong(name: String, block: SetConstraints<ULong?> = {}): Column<ULong?> =
    registerColumn(name, ULongPersistentType(), block)

  protected fun uuid(name: String, block: SetConstraints<UUID> = {}): Column<UUID> =
    registerColumn(name, UUIDPersistentType(), block)

  protected fun nullableUuid(name: String, block: SetConstraints<UUID?> = {}): Column<UUID?> =
    registerOptColumn(name, UUIDPersistentType(), block)

  protected fun bool(name: String, block: SetConstraints<Boolean> = {}): Column<Boolean> =
    registerColumn(name, BooleanPersistentType(), block)

  protected fun nullableBool(name: String, block: SetConstraints<Boolean?> = {}): Column<Boolean?> =
    registerOptColumn(name, BooleanPersistentType(), block)

  protected fun <T : Enum<T>> enumeration(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T> = {}
  ): Column<T> = registerColumn(name, EnumerationPersistentType(klass), block)

  protected fun <T : Enum<T>> optEnumeration(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T?> = {}
  ): Column<T?> = registerOptColumn(name, EnumerationPersistentType(klass), block)

  protected fun <T : Enum<T>> enumerationByName(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T> = {}
  ): Column<T> = registerColumn(name, EnumerationNamePersistentType(klass), block)

  protected fun <T : Enum<T>> optEnumerationByName(
    name: String,
    klass: KClass<T>,
    block: SetConstraints<T?> = {}
  ): Column<T?> = registerOptColumn(name, EnumerationNamePersistentType(klass), block)

  /**
   * Create a FOREIGN KEY constraint on a table
   *
   * @param name Name of the column.
   * @param refColumn A column from another table which will be used as a "parent".
   * @param onDelete optional action for when a linked row from a parent table is deleted.
   * @param onUpdate optional action for when a value in the parent table row had changed.
   * @param fkName optional foreign key constraint name.
   *
   * @see ForeignKeyAction
   */
  protected fun <T : Comparable<T>> references(
    name: String,
    refColumn: Column<T>,
    onDelete: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    onUpdate: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    fkName: String? = null
  ): Column<T> =
    Column(
      table = this,
      name = name,
      persistentType = refColumn.persistentType,
      initialConstraints = listOf(NotNullConstraint),
      addTo = ::addColumn
    ) {
      references(refColumn, onDelete, onUpdate, fkName)
    }

  /**
   * Creates a FOREIGN KEY constraint on a table that is nullable
   *
   * @param name Name of the column.
   * @param refColumn A column from another table which will be used as a "parent".
   * @param onDelete optional action for when a linked row from a parent table is deleted.
   * @param onUpdate optional action for when a value in the parent table row had changed.
   * @param fkName Optional foreign key constraint name.
   *
   * @see ForeignKeyAction
   */
  protected fun <T : Comparable<T>> optReference(
    name: String,
    refColumn: Column<T>,
    onDelete: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    onUpdate: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    fkName: String? = null
  ): Column<T?> =
    Column<T?>(
      table = this,
      name = name,
      persistentType = refColumn.persistentType.apply { nullable = true },
      addTo = ::addColumn
    ) { references(refColumn, onDelete, onUpdate, fkName) }

  private fun <T> registerColumn(
    name: String,
    type: PersistentType<T?>,
    block: SetConstraints<T>
  ): Column<T> {
    return Column(
      table = this,
      name = name,
      persistentType = type.apply { nullable = false },
      initialConstraints = listOf(NotNullConstraint),
      addTo = ::addColumn,
      block = block
    )
  }

  private fun <T> registerOptColumn(
    name: String,
    type: PersistentType<T?>,
    block: SetConstraints<T?>
  ): Column<T?> {
    return Column(
      table = this,
      name = name,
      persistentType = type.apply { nullable = true },
      addTo = ::addColumn,
      block = block
    )
  }

  private fun addColumn(column: Column<*>) {
    _columns.addColumn(column)
  }

  private fun MutableList<Column<*>>.addColumn(column: Column<*>) {
    if (any { it.name == column.name }) {
      throw DuplicateColumnException(
        column.name,
        tableName
      )
    }
    add(column)
  }

  private fun isCustomPKNameDefined(): Boolean =
    primaryKey?.let { it.identity().unquoted != "pk_$tableName" } == true

  fun index(customIndexName: String, firstColumn: Column<*>, vararg columns: Column<*>) =
    makeIndex(customIndexName, false, firstColumn, *columns)

  fun index(firstColumn: Column<*>, vararg columns: Column<*>) =
    makeIndex(null, false, firstColumn, *columns)

  fun uniqueIndex(
    customIndexName: String,
    firstColumn: Column<*>,
    vararg columns: Column<*>
  ) = makeIndex(customIndexName, true, firstColumn, *columns)

  fun uniqueIndex(firstColumn: Column<*>, vararg columns: Column<*>) =
    makeIndex(null, true, firstColumn, *columns)

  private fun makeIndex(
    customIndexName: String?,
    isUnique: Boolean,
    firstColumn: Column<*>,
    vararg otherColumns: Column<*>
  ) {
    val tableIdentity = identity
    indices.add(
      Index(
        tableIdentity,
        mutableListOf(firstColumn, *otherColumns).also { list ->
          list.forEach { column ->
            require(columns.contains(column)) { "Column '$column' not in table '$tableName'" }
          }
        },
        isUnique,
        customIndexName
      )
    )
  }

  @ExperimentalUnsignedTypes
  fun check(name: String = "", op: () -> Op<Boolean>) {
    if (name.isEmpty() || checkConstraints.none { it.first.equals(name, true) }) {
      checkConstraints.add(name to op())
    } else {
      LOG.w { it("A CHECK constraint with name '$name' was ignored because there is already one with that name") }
    }
  }

  protected val ddl: List<String>
    get() = createStatement() + indicesDDL

  /**
   * By default, every row in SQLite has a special column, usually called the "rowid", that
   * uniquely identifies that row within the table. However if the phrase "WITHOUT ROWID" is added
   * to the end of a CREATE TABLE statement, then the special "rowid" column is omitted.
   * There are sometimes space and performance advantages to omitting the rowid.
   *
   * The WITHOUT ROWID optimization is likely to be helpful for tables that have non-integer or
   * composite (multi-column) PRIMARY KEYs and that do not store large strings or BLOBs.
   *
   * [WITHOUT ROWID documentation](https://www.sqlite.org/withoutrowid.html)
   */
  protected var withoutRowId: Boolean
    get() {
      return _withoutRowId
    }
    set(value) {
      validateWithoutRowId(value)
      _withoutRowId = value
    }

  private fun validateWithoutRowId(value: Boolean) {
    if (value) require(primaryKey == null && columnDefiningPrimaryKey == null) {
      "Must have primary key if using WITHOUT ROWID"
    }
    primaryKey?.let { primary ->
      if (primary.columns.size == 1) {
        val column = primary.columns.first()
        require(!column.isAutoInc) { "AUTOINCREMENT does not work on WITHOUT ROWID tables" }
        when (column.persistentType) {
          is IntegerPersistentType, is LongPersistentType -> {
            LOG.w {
              it("The WITHOUT ROWID optimization is unlikely to be helpful for tables that have a single INTEGER PRIMARY KEY.")
            }
          }
        }
      }
    }
  }

  private var _withoutRowId: Boolean = false

  private fun StringBuilder.maybeAppendWithoutRowId() = apply {
    if (_withoutRowId) {
      validateWithoutRowId(true)
      append(" WITHOUT ROWID")
    }
  }

  private fun primaryKeyConstraint(): String? {
    return primaryKey?.let { primaryKey ->
      val constraint = primaryKey.identity()
      return primaryKey.columns.joinToString(
        prefix = "CONSTRAINT ${constraint.value} PRIMARY KEY (",
        postfix = ")",
        transform = { column -> column.identity().value }
      )
    }
  }

  private fun createStatement(): List<String> {
    val createTable = buildString {
      append("CREATE TABLE IF NOT EXISTS ")
      appendIdentity(identity)
      if (columns.isNotEmpty()) {
        columns.joinTo(this, prefix = " (") { it.descriptionDdl() }

        if (isCustomPKNameDefined() || columns.none { it.definesPrimaryKey }) {
          primaryKeyConstraint()?.let { append(", $it") }
        }

        val foreignKeyConstraints = columns.mapNotNull { it.foreignKey }

        if (foreignKeyConstraints.isNotEmpty()) {
          foreignKeyConstraints.joinTo(this, prefix = ", ", separator = ", ") { it.foreignKeyPart }
        }

        if (checkConstraints.isNotEmpty()) {
          checkConstraints.mapIndexed { index, (name, op) ->
            CheckConstraint(
              this@Table,
              name.ifBlank { "check_${tableName}_$index" }.asIdentity(),
              op
            ).checkPart
          }.joinTo(this, prefix = ", ")
        }

        append(")")
        maybeAppendWithoutRowId()
      }
    }

    return listOf(createTable)
  }

  private fun dropStatement(): List<String> {
    val dropTable = buildString {
      append("DROP TABLE IF EXISTS ")
      appendIdentity(identity)
    }

    return listOf(dropTable)
  }

  internal fun create(executor: SqlExecutor) {
    preCreate()
    LOG.i { it("Create $tableName") }
    executor.exec(ddl)
  }

  protected open fun preCreate() {}

  internal fun postCreate(executor: SqlExecutor) {
    LOG.i { it("postCreate $tableName") }
    onPostCreate(executor)
  }

  /**
   * This is typically where things such as triggers and views are created. The is called after
   * all the tables have been created.
   */
  @Suppress("UNUSED_PARAMETER")
  protected open fun onPostCreate(executor: SqlExecutor) {
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Table) return false

    if (tableName != other.tableName) return false

    return true
  }

  override fun hashCode(): Int = tableName.hashCode()

  companion object {
    const val RESERVED_PREFIX = "sqlite_"
  }
}

/**
 * Thrown when attempting to create multiple columns with the same name in the same table
 */
class DuplicateColumnException(columnName: String, tableName: String) :
  SQLiteException("Duplicate column name \"$columnName\" in table \"$tableName\"")
