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

import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.Alias
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.DuplicateColumnException
import com.ealva.welite.db.table.ForeignKeyConstraint
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.Bindable
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.db.type.buildStr
import java.util.Comparator

interface ViewColumn<T> : Column<T>

private class ViewColumnImpl<T>(
  val view: View,
  override val name: String,
  private val column: Column<T>
) : ViewColumn<T> {
  override val persistentType: PersistentType<T>
    get() = column.persistentType
  override val sqlType: String
    get() = persistentType.sqlType

  override fun bind(bindable: Bindable, index: Int, value: T?) {
    column.bind(bindable, index, value)
  }

  override fun asDefaultValue(): String {
    return column.asDefaultValue()
  }

  override fun appendTo(sqlBuilder: SqlBuilder) = sqlBuilder.append(name.asIdentity())

  override val table: Table
    get() = column.table

  override val tableIdentity: Identity
    get() = view.identity

  override val isAutoInc: Boolean
    get() = column.isAutoInc

  override var dbDefaultValue: Expression<T>?
    get() = column.dbDefaultValue
    set(value) {
      column.dbDefaultValue = value
    }

  override val refersTo: Column<*>?
    get() = column

  override var indexInPK: Int?
    get() = column.indexInPK
    set(@Suppress("UNUSED_PARAMETER") value) {}

  override var foreignKey: ForeignKeyConstraint?
    get() = column.foreignKey
    set(@Suppress("UNUSED_PARAMETER") value) {}

  override fun identity(): Identity {
    return name.asIdentity()
  }

  override fun makeAlias(tableAlias: String?): Column<T> =
    Column(Alias(table, tableAlias ?: "${view.viewName}_$name"), name, persistentType)

  override val nullable: Boolean
    get() = column.nullable

  override val definesPrimaryKey: Boolean
    get() = column.definesPrimaryKey

  override fun isOneColumnPK(): Boolean = column.isOneColumnPK()

  override fun descriptionDdl(): String = column.descriptionDdl()

  override fun markPrimaryKey() {}

  override fun compareTo(other: Column<*>): Int {
    if (other !is ViewColumnImpl) return -1
    return columnComparator.compare(this, other)
  }

  private val columnComparator: Comparator<ViewColumnImpl<*>> =
    compareBy({ column -> column.name }, { column -> column.view })
}

private val LOG by lazyLogger(View::class)

abstract class View(
  name: String = "",
  makeBuilder: () -> QueryBuilder
) : ColumnSet, Comparable<View> {
  private val querySeed = makeBuilder().makeQuerySeed()

  val viewName: String = (if (name.isNotEmpty()) name else this.nameFromClass()).apply {
    require(!startsWith(Table.RESERVED_PREFIX)) {
      "Invalid View name '$this', must not start with ${Table.RESERVED_PREFIX}"
    }
  }

  val identity = viewName.asIdentity()

  fun <T> column(name: String, column: Column<T>): ViewColumn<T> {
    return _columns.addColumn(ViewColumnImpl(this, name, column))
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

  /**
   * Public for test
   */
  fun create(executor: SqlExecutor) {
    LOG.i { it("Create View $viewName") }
    executor.exec(createSql)
  }

  protected val createSql by lazy {
    buildStr {
      append("CREATE VIEW IF NOT EXISTS ")
      append(identity)
      if (columns.isNotEmpty()) {
        columns.joinTo(this, prefix = " (", postfix = ") AS ") { it.name }
      }
      append(querySeed.sql)
    }
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
