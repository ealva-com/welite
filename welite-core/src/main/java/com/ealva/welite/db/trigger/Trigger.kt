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

package com.ealva.welite.db.trigger

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.statements.ColumnValues
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.InsertStatement
import com.ealva.welite.db.statements.UpdateStatement
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.NoIdentityColumnSet
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.where
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.StatementSeed
import com.ealva.welite.db.type.append
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.db.type.buildStr

private const val CREATE_TRIGGER = "CREATE TRIGGER IF NOT EXISTS "
private const val CREATE_TEMP_TRIGGER = "CREATE TEMP TRIGGER IF NOT EXISTS "

public interface Trigger<T : Table> : Creatable {
  public enum class BeforeAfter(public val value: String) {
    BEFORE(" BEFORE"),
    AFTER(" AFTER")
  }
}

public interface OldNewColumnFactory {
  /**
   * Refer to a column as "NEW.columnName" in a trigger
   */
  public fun <T> new(column: Column<T>): Column<T>

  /**
   * Refer to a column as "OLD.columnName" in a trigger
   */
  public fun <T> old(column: Column<T>): Column<T>
}

/** internal visibility for test */
internal enum class Event(
  val value: String,
  val acceptsNewColumnRef: Boolean,
  val acceptsOldColumnRef: Boolean
) {
  INSERT(" INSERT", true, false),
  UPDATE(" UPDATE", true, true),
  DELETE(" DELETE", false, true);
}

private class TriggerImpl<T : Table>(
  name: String,
  private val temp: Boolean,
  private val beforeAfter: Trigger.BeforeAfter,
  private val event: Event,
  private val updateCols: List<Column<*>>,
  private val table: T,
  private val columnFactory: OldNewColumnFactory,
  private val statements: List<StatementSeed>
) : Trigger<T>, OldNewColumnFactory by columnFactory {

  init {
    require(statements.isNotEmpty()) { "A trigger is required to have at least 1 statement" }
  }

  private var whenCondition: Expression<Boolean>? = null

  override val masterType: MasterType = MasterType.Trigger
  override val identity = name.asIdentity()

  override fun create(executor: SqlExecutor, temporary: Boolean) {
    executor.exec(makeCreateStatement())
  }

  private fun makeCreateStatement(): String = buildStr {
    append(if (temp) CREATE_TEMP_TRIGGER else CREATE_TRIGGER)
    append(identity.value)
    append(beforeAfter.value)
    append(event.value)
    if (event == Event.UPDATE && updateCols.isNotEmpty()) {
      append(" OF ")
      updateCols.forEachIndexed { index, column ->
        append(column.identity())
        if (index < updateCols.size - 1) {
          append(", ")
        }
      }
    }
    append(" ON ")
    append(table.identity)

    whenCondition?.let { cond ->
      append(" WHEN ")
      append(cond)
    }

    append(" BEGIN ")
    statements.forEach { statement ->
      check(statement.types.isEmpty()) {
        "Trigger does not support statements with bindable arguments. SQL=${statement.sql}"
      }
      append(statement)
      append("; ")
    }
    append("END;")
  }

  override fun drop(executor: SqlExecutor) {
    executor.exec(makeDropStatement())
  }

  private fun makeDropStatement(): String = buildStr {
    append("DROP TRIGGER IF EXISTS ")
    append(identity.value)
  }

  fun triggerWhen(cond: OldNewColumnFactory.(T) -> Expression<Boolean>) {
    whenCondition = cond(table)
  }
}

public enum class NewOrOld(public val value: String) {
  NEW("NEW."),
  OLD("OLD.");

  override fun toString(): String = value
}

private class NewOrOldColumn<T>(
  newOrOld: NewOrOld,
  private val original: Column<T>
) : Column<T> by original {
  override val name: String = "$newOrOld${original.name}"
  override fun identity(): Identity = name.asIdentity()

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(name)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NewOrOldColumn<*>

    if (name != other.name) return false
    if (original != other.original) return false

    return true
  }

  override fun hashCode(): Int {
    var result = original.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}

public interface TriggerUpdate<T : Table> {
  public fun addWhere(where: T.() -> Op<Boolean>)
}

@WeLiteMarker
public interface TriggerStatements : OldNewColumnFactory {
  public val statements: List<StatementSeed>

  public fun <T : Table> T.insert(
    onConflict: OnConflict = OnConflict.Unspecified,
    assignColumns: T.(ColumnValues) -> Unit
  )

  public fun <T : Table> T.delete(where: () -> Op<Boolean>)

  public fun <T : Table> T.update(
    onConflict: OnConflict = OnConflict.Unspecified,
    assignColumns: T.(ColumnValues) -> Unit
  ): TriggerUpdate<T>

  public fun <T : Table> TriggerUpdate<T>.where(where: T.() -> Op<Boolean>)

  public fun select(vararg columns: Expression<*>)
}

/**
 * As TriggerStatements are further developed it may be necessary to implement a SelectFrom
 * delegate and/or a QueryBuilder delegate that adds statements to the TriggerStatements list
 */
private class TriggerStatementsImpl(
  private val columnFactory: OldNewColumnFactory,
) : TriggerStatements, OldNewColumnFactory by columnFactory {
  override val statements: MutableList<StatementSeed> = mutableListOf()

  override fun <T : Table> T.insert(
    onConflict: OnConflict,
    assignColumns: T.(ColumnValues) -> Unit
  ) {
    statements += InsertStatement.statementSeed(this, onConflict, assignColumns)
  }

  override fun <T : Table> T.delete(where: () -> Op<Boolean>) {
    statements += DeleteStatement.statementSeed(this, where())
  }

  override fun <T : Table> T.update(
    onConflict: OnConflict,
    assignColumns: T.(ColumnValues) -> Unit
  ): TriggerUpdate<T> {
    return object : TriggerUpdate<T> {
      override fun addWhere(where: T.() -> Op<Boolean>) {
        statements += UpdateStatement.statementSeed(
          this@update,
          onConflict,
          this@update.where(),
          assignColumns
        )
      }
    }
  }

  override fun <T : Table> TriggerUpdate<T>.where(where: T.() -> Op<Boolean>) {
    addWhere(where)
  }

  /**
   * If select later needs to be expanded to use WHERE or other parts of a query, a type of
   * TriggerSelectFrom delegate and/or TriggerQueryBuilder delegate can be developed, each of which
   * would contain a TriggerStatements instance so the eventual query StatementSeed can be added to
   * the list of trigger statements
   */
  override fun select(vararg columns: Expression<*>) {
    statements += SelectFrom<NoIdentityColumnSet>(columns.distinct(), NoIdentityColumnSet())
      .where(null)
      .statementSeed()
  }
}

/**
 * Make a [Trigger] with [name] for table [T] to be executed [beforeAfter] an insert and
 * call [addStatements] to add all the statements to be executed when the trigger fires.
 */
public fun <T : Table> T.insertTrigger(
  name: String,
  beforeAfter: Trigger.BeforeAfter,
  temporary: Boolean = false,
  triggerCondition: (OldNewColumnFactory.(T) -> Expression<Boolean>)? = null,
  addStatements: TriggerStatements.() -> Unit
): Trigger<T> {
  val event = Event.INSERT
  val columnFactory = TriggerOldNewFactory(this, event)
  return TriggerImpl(
    name,
    temporary,
    beforeAfter,
    event,
    emptyList(),
    this,
    columnFactory,
    TriggerStatementsImpl(columnFactory).apply(addStatements).statements
  ).apply {
    triggerCondition?.let { cond ->
      triggerWhen(cond)
    }
  }
}

/**
 * Make a [Trigger] with [name] for table [T] to be executed [beforeAfter] an update and
 * call [addStatements] to add all the statements to be executed when the trigger fires.
 */
public fun <T : Table> T.updateTrigger(
  name: String,
  beforeAfter: Trigger.BeforeAfter,
  temporary: Boolean = false,
  updateColumns: List<Column<*>> = emptyList(),
  triggerCondition: (OldNewColumnFactory.(T) -> Expression<Boolean>)? = null,
  addStatements: TriggerStatements.() -> Unit
): Trigger<T> {
  val event = Event.UPDATE
  val columnFactory = TriggerOldNewFactory(this, event)
  return TriggerImpl(
    name,
    temporary,
    beforeAfter,
    event,
    updateColumns,
    this,
    columnFactory,
    TriggerStatementsImpl(columnFactory).apply(addStatements).statements
  ).apply {
    triggerCondition?.let { cond ->
      triggerWhen(cond)
    }
  }
}

/**
 * Make a [Trigger] with [name] for table [T] to be executed [beforeAfter] a delete and
 * call [addStatements] to add all the statements to be executed when the trigger fires.
 */
public fun <T : Table> T.deleteTrigger(
  name: String,
  beforeAfter: Trigger.BeforeAfter,
  temporary: Boolean = false,
  triggerCondition: (OldNewColumnFactory.(T) -> Expression<Boolean>)? = null,
  addStatements: TriggerStatements.() -> Unit
): Trigger<T> {
  val event = Event.DELETE
  val columnFactory = TriggerOldNewFactory(this, event)
  return TriggerImpl(
    name,
    temporary,
    beforeAfter,
    event,
    emptyList(),
    this,
    columnFactory,
    TriggerStatementsImpl(columnFactory).apply(addStatements).statements
  ).apply {
    triggerCondition?.let { cond ->
      triggerWhen(cond)
    }
  }
}

private class TriggerOldNewFactory(
  private val table: Table,
  private val event: Event
) : OldNewColumnFactory {
  override fun <T> new(column: Column<T>): Column<T> {
    check(event.acceptsNewColumnRef) {
      "NEW reference not valid for a $event trigger"
    }
    check(column.table == table) {
      "NEW.column must refer to a $table table column. Refers to ${column.table} table"
    }
    return NewOrOldColumn(NewOrOld.NEW, column)
  }

  override fun <T> old(column: Column<T>): Column<T> {
    check(event.acceptsOldColumnRef) {
      "OLD reference not valid for an $event trigger"
    }
    check(column.table == table) {
      "OLD.column must refer to a $table table column. Refers to ${column.table} table"
    }
    return NewOrOldColumn(NewOrOld.OLD, column)
  }
}
