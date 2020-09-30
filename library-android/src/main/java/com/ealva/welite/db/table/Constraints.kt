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

import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.db.type.buildStr

private val LOG by lazyLogger("Constraints.kt", WeLiteLog.marker)

/**
 * See [SQLite Foreign Key Support](https://sqlite.org/foreignkeys.html)
 */
@Suppress("unused")
enum class ForeignKeyAction(val value: String) {
  /**
   * If the configured action is "SET NULL", then when a parent key is deleted (for ON DELETE SET
   * NULL) or modified (for ON UPDATE SET NULL), the child key columns of all rows in the child
   * table that mapped to the parent key are set to contain SQL NULL values.
   */
  SET_NULL("SET NULL"),

  /**
   * The "SET DEFAULT" actions are similar to "SET NULL", except that each of the child key columns
   * is set to contain the columns default value instead of NULL.
   */
  SET_DEFAULT("SET DEFAULT"),

  /**
   * A "CASCADE" action propagates the delete or update operation on the parent key to each
   * dependent child key.
   */
  CASCADE("CASCADE"),

  /**
   * The "RESTRICT" action means that the application is prohibited from deleting (for ON DELETE
   * RESTRICT) or modifying (for ON UPDATE RESTRICT) a parent key when there exists one or more
   * child keys mapped to it.
   */
  RESTRICT("RESTRICT"),

  /**
   * When a parent key is modified or deleted from the database, no special action is taken.
   * Currently NO ACTION is not part of the generated SQL as it is the default
   */
  NO_ACTION("NO ACTION");

  override fun toString() = value

  companion object {
    private val valueInstanceMap =
      values().associateBy { foreignKeyAction -> foreignKeyAction.value }

    fun fromSQLite(
      sqliteName: String,
      defaultValue: ForeignKeyAction = NO_ACTION
    ): ForeignKeyAction = valueInstanceMap[sqliteName] ?: defaultValue
  }
}

/**
 * SQL foreign key constraints are used to enforce "exists" relationships between tables.
 *
 *```
 * foreign_key_clause
 * : REFERENCES foreign_table ( '(' column_name ( ',' column_name )* ')' )?
 *   ( ( ON ( DELETE | UPDATE ) ( SET NULL
 *                              | SET DEFAULT
 *                              | CASCADE
 *                              | RESTRICT
 *                              | NO ACTION )
 *     | MATCH name
 *     )
 *   )*
 *   ( NOT? DEFERRABLE ( INITIALLY DEFERRED | INITIALLY IMMEDIATE )? )?
 *```
 * See [SQLite Foreign Key Support](https://sqlite.org/foreignkeys.html)
 */
data class ForeignKeyConstraint(
  val parentTable: Identity,
  val parent: Column<*>,
  val childTable: Identity,
  val child: Column<*>,
  val onUpdate: ForeignKeyAction,
  val onDelete: ForeignKeyAction,
  private val name: String?
) {
  private val childColumn: Identity
    get() = child.identity()

  private val parentColumn: Identity
    get() = parent.identity()

  private val fkName: Identity
    get() = Identity.make(
      name ?: buildStr {
        append("fk_")
        append(parentTable.unquoted)
        append("_")
        append(parentColumn.unquoted)
        append("_")
        append(childColumn.unquoted)
      }
    )

  internal val foreignKeyPart: String
    get() = buildStr {
      append("CONSTRAINT ")
      append(fkName.value)
      append(" FOREIGN KEY (")
      append(parentColumn.value)
      append(") REFERENCES ")
      append(childTable.value)
      append('(')
      append(childColumn.value)
      append(')')
      if (onDelete != ForeignKeyAction.NO_ACTION) {
        append(" ON DELETE ")
        append(onDelete.toString())
      }
      if (onUpdate != ForeignKeyAction.NO_ACTION) {
        append(" ON UPDATE ")
        append(onUpdate.toString())
      }
    }
}

class CheckConstraint private constructor(
  private val tableIdentity: Identity,
  private val checkName: Identity,
  private val checkOp: String
) {

  internal val checkPart = "CONSTRAINT ${checkName.value} CHECK ($checkOp)"

  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as CheckConstraint

    if (tableIdentity != other.tableIdentity) return false
    if (checkName != other.checkName) return false
    if (checkOp != other.checkOp) return false
    if (checkPart != other.checkPart) return false

    return true
  }

  override fun hashCode(): Int {
    var result = tableIdentity.hashCode()
    result = 31 * result + checkName.hashCode()
    result = 31 * result + checkOp.hashCode()
    result = 31 * result + checkPart.hashCode()
    return result
  }

  override fun toString(): String {
    return "CheckConstraint(tableIdentity=" + tableIdentity + ", checkName=" + checkName +
      ", checkOp='" + checkOp + "', checkPart='" + checkPart + "')"
  }

  companion object {
    /**
     * Make a CheckConstraint
     */
    internal operator fun invoke(table: Table, name: Identity, op: Op<Boolean>): CheckConstraint {
      require(name.value.isNotBlank()) { "Check constraint name cannot be blank" }
      val tableIdentity = table.identity
      val checkOpSQL = op.toString().replace("${tableIdentity.value}.", "")
      return CheckConstraint(tableIdentity, name, checkOpSQL)
    }
  }
}

private const val CREATE_UNIQUE_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS "
private const val CREATE_INDEX = "CREATE INDEX IF NOT EXISTS "

class Index(
  private val table: Table,
  private val columns: List<Column<*>>,
  private val unique: Boolean,
  private val customName: String? = null
) : Creatable {
  init {
    require(columns.isNotEmpty()) { "At least one column is required to create an index" }
  }

  val tableIdentity: Identity = table.identity

  private val indexName: String
    get() = customName ?: buildStr {
      append(tableIdentity.unquoted)
      append('_')
      append(columns.joinToString("_") { it.name })
      if (unique) {
        append("_unique")
      }
    }

  override val masterType: MasterType = MasterType.Index
  override val identity: Identity
    get() = indexName.asIdentity()

  override fun toString(): String = makeCreateSql()

  override fun create(executor: SqlExecutor, temporary: Boolean) {
    if (temporary) LOG.e { it("CREATE INDEX does not support TEMPORARY. Ignoring...") }
    executor.exec(makeCreateSql())
  }

  override fun drop(executor: SqlExecutor) {
    table.removeIndex(this)
    executor.exec(makeDropSql())
  }

  private fun makeCreateSql(): String = buildStr {
    if (unique) append(CREATE_UNIQUE_INDEX) else append(CREATE_INDEX)
    append(identity.value)
    append(" ON ")
    append(tableIdentity.value)
    columns.appendTo(this, prefix = "(", postfix = ")") { append(it.identity().value) }
  }

  private fun makeDropSql(): String = buildStr {
    append("DROP INDEX IF EXISTS ")
    append(identity.value)
  }

  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Index

    if (tableIdentity != other.tableIdentity) return false
    if (columns != other.columns) return false
    if (unique != other.unique) return false
    if (indexName != other.indexName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = tableIdentity.hashCode()
    result = 31 * result + columns.hashCode()
    result = 31 * result + unique.hashCode()
    result = 31 * result + (customName?.hashCode() ?: 0)
    return result
  }
}
