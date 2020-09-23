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

package com.ealva.welite.db.view

import android.os.Build
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.Alias
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.DuplicateColumnException
import com.ealva.welite.db.table.ForeignKeyConstraint
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.QuerySeed
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.db.type.buildStr
import java.util.Comparator

interface ViewColumn<T> : Column<T> {
  companion object {
    operator fun <T> invoke(
      view: View,
      name: String,
      column: Column<T>
    ): ViewColumn<T> {
      val actualName =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
          makeName(name, view.identity, column.tableIdentity, column.identity())
        } else {
          column.identity().unquoted
        }
      return ViewColumnImpl(view, actualName, column)
    }

    private fun makeName(
      name: String,
      viewIdentity: Identity,
      tableIdentity: Identity,
      columnIdentity: Identity
    ): String {
      return if (name.isNotBlank()) {
        name
      } else buildStr {
        append(viewIdentity.unquoted)
        append("_")
        append(tableIdentity.unquoted)
        append("_")
        append(columnIdentity.unquoted)
      }
    }
  }
}

private const val CREATE_VIEW = "CREATE VIEW IF NOT EXISTS "
private const val CREATE_TEMP_VIEW = "CREATE TEMP VIEW IF NOT EXISTS "

private class ViewColumnImpl<T>(
  private val view: View,
  override val name: String,
  private val originalColumn: Column<T>
) : ViewColumn<T> {
  override val persistentType: PersistentType<T>
    get() = originalColumn.persistentType
  override val sqlType: String
    get() = persistentType.sqlType

  override fun identity(): Identity {
    return name.asIdentity()
  }

  override fun bind(bindable: Bindable, index: Int, value: T?) {
    originalColumn.bind(bindable, index, value)
  }

  override fun asDefaultValue(): String {
    return originalColumn.asDefaultValue()
  }

  override fun appendTo(sqlBuilder: SqlBuilder) = sqlBuilder.append(identity())

  override val table: Table
    get() = originalColumn.table

  override val tableIdentity: Identity
    get() = view.identity

  override val isAutoInc: Boolean
    get() = originalColumn.isAutoInc

  override var dbDefaultValue: Expression<T>?
    get() = originalColumn.dbDefaultValue
    set(value) {
      originalColumn.dbDefaultValue = value
    }

  override val refersTo: Column<*>?
    get() = originalColumn

  override var indexInPK: Int?
    get() = null
    set(@Suppress("UNUSED_PARAMETER") value) {}

  override var foreignKey: ForeignKeyConstraint?
    get() = null
    set(@Suppress("UNUSED_PARAMETER") value) {}

  override fun makeAlias(tableAlias: String?): Column<T> =
    Column(Alias(table, tableAlias ?: "${view.viewName}_$name"), name, persistentType)

  override val nullable: Boolean
    get() = originalColumn.nullable

  override val definesPrimaryKey: Boolean
    get() = false

  override fun isOneColumnPK(): Boolean = false

  override fun descriptionDdl(): String = ""

  override fun markPrimaryKey() {}

  override fun compareTo(other: Column<*>): Int {
    if (other !is ViewColumnImpl) return -1
    return columnComparator.compare(this, other)
  }

  private val columnComparator: Comparator<ViewColumnImpl<*>> =
    compareBy({ column -> column.name }, { column -> column.view })
}

private val LOG by lazyLogger(View::class)

/**
 * Represents a View in the database.
 *
 * The number
 *
 * The CREATE VIEW command assigns a name to a pre-packaged SELECT statement. Once the view is
 * created, it can be used in the FROM clause of another SELECT in place of a table name.
 * [SQLite CREATE VIEW](https://sqlite.org/lang_createview.html)
 *
 * A note about the underlying generated SQL: The column-name list syntax was added in SQLite
 * version 3.9.0, which is Android API 24, Build.VERSION_CODES.N. Less than this version of
 * Android will use the canonical column names (table.column) and will ignore the client's
 * requested column name. Versions >= Build.VERSION_CODES.N use the column-name list syntax of the
 * CREATE VIEW statement and will use the column name parameter if it's not blank.
 */
abstract class View(
  name: String = "",
  private val querySeed: QuerySeed
) : ColumnSet, Creatable, Comparable<View> {

  constructor(name: String = "", query: Query) : this(name, query.seed)

  constructor(name: String = "", builder: QueryBuilder) : this(name, builder.build())

  val viewName: String = (if (name.isNotEmpty()) name else this.nameFromClass()).apply {
    require(!startsWith(Table.RESERVED_PREFIX)) {
      "Invalid View name '$this', must not start with ${Table.RESERVED_PREFIX}"
    }
  }

  override val masterType: MasterType = MasterType.View

  override val identity = viewName.asIdentity()

  /**
   * Create a ViewColumn from a Column that will appear in a query. If
   * Build.VERSION.SDK_INT >= Build.VERSION_CODES.N [name] will be used as the column name unless
   * it is blank, then a name will be created ViewName_OriginalTableName_OriginalColumnName.
   * If the build version is less than Build.VERSION_CODES.N the resulting name will be
   * the original full name of [column] as the CREATE VIEW statement doesn't support
   * the column-name list that follows the view-name until SQLite 3.9.
   */
  fun <T> column(name: String = "", column: Column<T>): ViewColumn<T> {
    return _columns.addColumn(ViewColumn(this, name, column))
  }

  private fun <T> MutableList<ViewColumn<*>>.addColumn(column: ViewColumn<T>) = column.apply {
    if (any { it.name == column.name }) throw DuplicateColumnException(column.name, viewName)
    add(column)
  }

  private val _columns = mutableListOf<ViewColumn<*>>()
  override val columns: List<ViewColumn<*>>
    get() = _columns

  override fun appendTo(sqlBuilder: SqlBuilder) = sqlBuilder.apply { append(identity) }

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
  override infix fun naturalJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.NATURAL)

  override fun create(executor: SqlExecutor, temporary: Boolean) {
    LOG.i { it("Creating View %s", viewName) }
    executor.exec(createStatement(temporary))
  }

  override fun drop(executor: SqlExecutor) {
    LOG.i { it("Dropping view %s", viewName) }
    executor.exec(dropStatement())
  }

  private fun dropStatement() = buildStr {
    append("DROP VIEW IF EXISTS ")
    append(identity)
  }

  private fun createStatement(temporary: Boolean) = buildStr {
    if (temporary) append(CREATE_TEMP_VIEW) else append(CREATE_VIEW)
    append(identity)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      if (columns.isNotEmpty()) {
        columns.joinTo(this, prefix = " (", postfix = ") AS ") { it.identity().value }
      }
    } else {
      append(" AS ")
    }
    append(querySeed.sql)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as View

    if (viewName != other.viewName) return false

    return true
  }

  override fun hashCode(): Int {
    return viewName.hashCode()
  }

  override fun compareTo(other: View): Int {
    return viewName.compareTo(other.viewName)
  }
}

private fun View.nameFromClass() = javaClass.simpleName.removeSuffix("View")
