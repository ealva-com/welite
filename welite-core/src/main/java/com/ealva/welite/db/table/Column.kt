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

import com.ealva.welite.db.expr.BaseSqlTypeExpression
import com.ealva.welite.db.expr.BindExpression
import com.ealva.welite.db.expr.Count
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.buildStr
import java.util.Comparator

/**
 * Represents a column in a table with the type [T]. [T] is the Kotlin type and not necessarily
 * what is stored in the database. To be stored in the DB some types may be converted to
 * a String, stored in a Blob, stored as a wider numerical type, etc.
 */
public interface Column<T> : SqlTypeExpression<T>, Comparable<Column<*>> {
  public val name: String

  public val table: Table

  public val tableIdentity: Identity

  public val isAutoInc: Boolean

  public var dbDefaultValue: Expression<T>?

  public val refersTo: Column<*>?

//  fun <S : T> refersTo(): Column<S>? {
//    @Suppress("UNCHECKED_CAST")
//    return refersTo as? Column<S>
//  }

  public var indexInPK: Int?

  public var foreignKey: ForeignKeyConstraint?

  public fun identity(): Identity

  /**
   * Create an alias for this column with [tableAlias] as the table name. If [tableAlias] is
   * null the table name will be this column's table name with an underscore and this
   * column's [name] appended, eg. pseudocode:
   * ```tableAlias="${column.table.name}_${column.name}"```
   */
  public fun makeAlias(tableAlias: String? = null): Column<T>

  /**
   * Clones this column substituting [table] for the original table
   */
  public fun cloneFor(table: Table): Column<T>

  /**
   * True if null may be assigned to this column because either it's set nullable or
   * represents the row id (INTEGER PRIMARY KEY) without DESC
   */
  public val nullable: Boolean

  /**
   * True if the column constraints for this column include primary key
   */
  public val definesPrimaryKey: Boolean

  public fun isOneColumnPK(): Boolean

  /**
   * Return the sql for this column as will be found in the CREATE TABLE statement
   */
  public fun descriptionDdl(): String

  /**
   * Mark this column as part of the primary key
   */
  public fun markPrimaryKey()

  /**
   * Create a Bindable argument for this column to represent that the actual value will be bound
   * into the statement/query when executed.
   */
  public fun bindArg(): BindExpression<T> = BindExpression(persistentType)

  public companion object {
    /**
     * Create a column implementation using syntax similar to a constructor
     */
    public operator fun <T> invoke(
      name: String,
      table: Table,
      persistentType: PersistentType<T>,
      initialConstraints: List<ColumnConstraint> = emptyList(),
      addTo: (Column<*>) -> Unit = {},
      block: ColumnConstraints<T>.() -> Unit = {}
    ): Column<T> = ColumnImpl(name, table, persistentType, initialConstraints).apply {
      addTo(this)
      block()
    }
  }
}

private class ColumnImpl<T>(
  override val name: String,
  override val table: Table,
  override val persistentType: PersistentType<T>,
  initialConstraints: List<ColumnConstraint>
) : BaseSqlTypeExpression<T>(), Column<T>, ColumnConstraints<T> {

  private val constraintList = ConstraintCollection(persistentType).apply {
    initialConstraints.forEach { add(it) }
  }

  override val tableIdentity: Identity
    get() = table.identity

  override val isAutoInc: Boolean
    get() = constraintList.hasAutoInc()

  override var dbDefaultValue: Expression<T>? = null

  override val refersTo: Column<*>?
    get() = foreignKey?.child

  override var indexInPK: Int? = null

  override var foreignKey: ForeignKeyConstraint? = null

  override fun identity(): Identity = Identity.make(name)

  override fun makeAlias(tableAlias: String?): Column<T> =
    Column(name, Alias(table, tableAlias ?: "${table.tableName}_$name"), persistentType)

  override fun cloneFor(table: Table): Column<T> = Column(name, table, persistentType)

  override val nullable: Boolean
    get() = persistentType.nullable || constraintList.isRowId(persistentType.isIntegerType)

  override val definesPrimaryKey: Boolean
    get() = constraintList.hasPrimaryKey()

  override fun isOneColumnPK(): Boolean = table.primaryKey?.columns?.singleOrNull() == this

  /** Returns the SQL representation of this column. */
  override fun descriptionDdl(): String = buildStr {
    append(identity())
    append(' ')
    append(persistentType.sqlType)
    constraintList.forEach { constraint ->
      append(' ')
      append(constraint.toString())
    }
    dbDefaultValue?.let { defaultValue ->
      append(" DEFAULT ")
      append(defaultValue.asDefaultValue())
    }
  }

  override fun markPrimaryKey() {
    val columnDefiningPrimaryKey = table.columnDefiningPrimaryKey
    check(columnDefiningPrimaryKey == null) {
      "Primary key already defined for column ${columnDefiningPrimaryKey?.name}"
    }
    check(indexInPK == null) { "$this already part of primary key" }
    indexInPK = table.columns.count { it.indexInPK != null } + 1
  }

  override fun asDefaultValue(): String = buildStr {
    append("(")
    append(tableIdentity.unquoted)
    append('.')
    append(name)
    append(")")
  }

  override fun primaryKey() = apply { addConstraint(PrimaryKeyConstraint) }

  override fun asc() = apply { addConstraint(AscConstraint) }

  override fun desc() = apply { addConstraint(DescConstraint) }

  override fun onConflict(onConflict: OnConflict) = apply {
    addConstraint(ConflictConstraint(onConflict))
  }

  override fun autoIncrement() = apply {
    if (!constraintList.hasPrimaryKey()) {
      primaryKey()
    }
    addConstraint(AutoIncrementConstraint)
  }

  override fun unique() = apply { addConstraint(UniqueConstraint) }

  @ExperimentalUnsignedTypes
  override fun check(name: String, op: (Column<T>) -> Op<Boolean>) = apply {
    table.check(name) { op(this@ColumnImpl) }
  }

  override fun default(defaultValue: T) = apply { dbDefaultValue = literal(defaultValue) }

  override fun defaultExpression(defaultValue: Expression<T>) = apply {
    dbDefaultValue = defaultValue
  }

  override fun collateBinary() = collate(CollateBinary)
  override fun collateNoCase() = collate(CollateNoCase)
  override fun collateRTrim() = collate(CollateRTrim)
  private fun collate(collate: Collate) = apply { addConstraint(CollateConstraint(collate)) }
  override fun collate(name: String) = apply { addConstraint(CollateConstraint(CollateUser(name))) }

  override fun index(customIndexName: String?) = apply {
    customIndexName?.let { table.index(customIndexName, this) } ?: table.index(this)
  }

  override fun uniqueIndex(customIndexName: String?) = apply {
    customIndexName?.let { table.uniqueIndex(customIndexName, this) } ?: table.uniqueIndex(this)
  }

  override fun <S : T> references(
    ref: Column<S>,
    onDelete: ForeignKeyAction,
    onUpdate: ForeignKeyAction,
    fkName: String?
  ) = apply {
    foreignKey = ForeignKeyConstraint(
      parentTable = tableIdentity,
      parent = this,
      childTable = ref.tableIdentity,
      child = ref,
      onUpdate = onUpdate,
      onDelete = onDelete,
      name = fkName
    )
  }

  private fun addConstraint(constraint: ColumnConstraint) = constraintList.add(constraint)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(table.identity.value)
    append('.')
    append(identity().value)
  }

  override fun compareTo(other: Column<*>): Int = columnComparator.compare(this, other)

  override fun toString(): String = buildStr {
    append(tableIdentity.unquoted)
    append('.')
    append(name)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as ColumnImpl<*>

    if (name != other.name) return false
    if (table != other.table) return false
    if (persistentType != other.persistentType) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + table.hashCode()
    result = 31 * result + persistentType.hashCode()
    return result
  }
}

/**
 * SimpleDelegatingColumn: Simple as in it does not present it's fully qualified name when
 * appending to SqlBuilder or in toString
 */
public data class SimpleDelegatingColumn<T>(val original: Column<T>) : Column<T> by original {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(identity().value)
  }

  /**
   * Don't allow delegation to original because is will slice this object and return [original]
   */
  override fun aliasOrSelf(): SqlTypeExpression<T> = this

  /**
   * Don't allow delegation to original because is will slice this object and return [original]
   */
  override fun originalOrSelf(): Expression<T> = this

  override fun toString(): String {
    return name
  }
}

private val columnComparator: Comparator<Column<*>> =
  compareBy({ column -> column.tableIdentity.value }, { column -> column.name })

@Suppress("unused")
public fun Column<*>.countDistinct(): Count = Count(this, true)
