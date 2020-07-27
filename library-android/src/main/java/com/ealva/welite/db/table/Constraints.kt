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

import com.ealva.welite.db.expr.Op

/**
 * See [SQLite Foreign Key Support](https://sqlite.org/foreignkeys.html)
 */
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
      name ?: "fk_${parentTable.unquoted}_${parentColumn.unquoted}_${childColumn.unquoted}"
    )

  internal val foreignKeyPart: String
    get() = buildString {
      append("CONSTRAINT ${fkName.value} ")
      append("FOREIGN KEY (${parentColumn.value}) REFERENCES ${childTable.value}(${childColumn.value})")
      if (onDelete != ForeignKeyAction.NO_ACTION) {
        append(" ON DELETE $onDelete")
      }
      if (onUpdate != ForeignKeyAction.NO_ACTION) {
        append(" ON UPDATE $onUpdate")
      }
    }

}

class CheckConstraint(
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
    return "CheckConstraint(tableIdentity=$tableIdentity, checkName=$checkName, checkOp='$checkOp', checkPart='$checkPart')"
  }

  companion object {
    internal operator fun invoke(table: Table, name: Identity, op: Op<Boolean>): CheckConstraint {
      require(name.value.isNotBlank()) { "Check constraint name cannot be blank" }
      val tableIdentity = table.identity()
      val checkOpSQL = op.toString().replace("${tableIdentity.value}.", "")
      return CheckConstraint(
        tableIdentity,
        name,
        checkOpSQL
      )
    }
  }
}

class Index(
  private val tableIdentity: Identity,
  private val columns: List<Column<*>>,
  private val unique: Boolean,
  private val customName: String? = null
) {
  init {
    require(columns.isNotEmpty()) { "At least one column is required to create an index" }
  }

  val identity: Identity
    get() = indexName.asIdentity()

  private val indexName: String
    get() = customName ?: buildString {
      append(tableIdentity.unquoted)
      append('_')
      append(columns.joinToString("_") { it.name })
      if (unique) {
        append("_unique")
      }
    }

  fun createStatement(): List<String> {
    val indexIdentity = indexName.asIdentity()

    val columnsList = columns.joinToString(prefix = "(", postfix = ")") { it.identity().value }
    val prefix = if (unique) "CREATE UNIQUE INDEX IF NOT EXISTS" else "CREATE INDEX IF NOT EXISTS"

    val sql = "$prefix ${indexIdentity.value} ON ${tableIdentity.value}$columnsList"
    return listOf(sql)
  }

  fun onlyDiffersInName(other: Index): Boolean =
    indexName != other.indexName && columns == other.columns && unique == other.unique

  override fun toString(): String =
    createStatement().first()

  @Suppress("DuplicatedCode")
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Index

    if (tableIdentity != other.tableIdentity) return false
    if (columns != other.columns) return false
    if (unique != other.unique) return false
    if (customName != other.customName) return false

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
