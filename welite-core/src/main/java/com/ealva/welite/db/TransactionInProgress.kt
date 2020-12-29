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

package com.ealva.welite.db

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.annotation.RequiresApi
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.statements.ColumnValues
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.InsertStatement
import com.ealva.welite.db.statements.UpdateStatement
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.ColumnMetadata
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.CursorWrapper
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.OnConflict.Unspecified
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QuerySeed
import com.ealva.welite.db.table.SQLiteSchema
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDescription
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.asMasterType
import com.ealva.welite.db.table.columnMetadata
import com.ealva.welite.db.table.longForQuery
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.selects
import com.ealva.welite.db.table.stringForQuery
import com.ealva.welite.db.type.buildStr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private val LOG by lazyLogger(TransactionInProgress::class, WeLiteLog.marker)
private const val NULL_SQL_FOUND = "Null sql table:%s type=%s position:%d"

/**
 * These functions are scoped to a transaction interface in an attempt to only read and write to
 * the DB under an explicit transaction. Functions in this interface require the enclosing
 * transaction to be marked successful or rolled back, but leave the commit/rollback to a different
 * level.
 */
@WeLiteMarker
public interface TransactionInProgress : Queryable {
  /**
   * Execute this InsertStatement: clears the bindings,
   * provides for new bound parameters via [bindArgs], and executes the insert, returning the
   * row ID of the row inserted if successful else -1
   */
  public fun <C : ColumnSet> InsertStatement<C>.insert(
    bindArgs: C.(ArgBindings) -> Unit = {}
  ): Long

  /**
   * Does a single insert into the table. Builds an [InsertStatement] and invokes
   * [InsertStatement.insert]. The InsertStatement is single use and not exposed to the client
   * for reuse.
   */
  public fun <T : Table> T.insert(
    onConflict: OnConflict = Unspecified,
    bindArgs: T.(ArgBindings) -> Unit = { },
    assignColumns: T.(ColumnValues) -> Unit
  ): Long = InsertStatement(this, onConflict, assignColumns).insert(bindArgs)

  /**
   * Binds arguments to this DeleteStatement and perform a DELETE, returning the number of
   * rows removed.
   */
  public fun <C : ColumnSet> DeleteStatement<C>.delete(
    bindArgs: C.(ArgBindings) -> Unit = { }
  ): Long

  /**
   * Does a single delete on the table. Builds a [DeleteStatement] and calls
   * [DeleteStatement.delete]. The DeleteStatement is single use and not exposed to the client.
   */
  public fun <T : Table> T.delete(
    bindArgs: T.(ArgBindings) -> Unit = { },
    where: T.() -> Op<Boolean>
  ): Long = DeleteStatement(this, where()).delete(bindArgs)

  public fun <T : Table> T.deleteAll(bindArgs: T.(ArgBindings) -> Unit = { }): Long =
    DeleteStatement(this, null).delete(bindArgs)

  /**
   * Clears bindings, binds arguments to this UpdateStatement, and execute the statement
   * returning the number of rows updated.
   */
  public fun <C : ColumnSet> UpdateStatement<C>.update(
    bindArgs: C.(ArgBindings) -> Unit = { }
  ): Long

  /**
   * Create a Creatable database entity (Table, Index, View, ...)
   */
  public fun Creatable.create(temporary: Boolean = false)

  /**
   * Drop a Creatable database entity (Table, Index, View, ...)
   */
  public fun Creatable.drop()

  /**
   * The [VACUUM](https://www.sqlite.org/lang_vacuum.html) command rebuilds the database file,
   * repacking it into a minimal amount of disk space.
   */
  public fun vacuum()

  public companion object {
    /**
     * Create a TransactionInProgress using [dbConfig]
     */
    internal operator fun invoke(dbConfig: DbConfig): TransactionInProgress =
      TransactionInProgressImpl(dbConfig)
  }
}

private const val INDEX_FK_ID = 0
private const val INDEX_FK_SEQ = 1
private const val INDEX_FK_TABLE = 2
private const val INDEX_FK_FROM = 3
private const val INDEX_FK_TO = 4
private const val INDEX_FK_ON_UPDATE = 5
private const val INDEX_FK_ON_DELETE = 6
// const private val index_fk_match = 7

private const val INDEX_FK_VIOLATION_TABLE = 0
private const val INDEX_FK_VIOLATION_ROW_ID = 1
private const val INDEX_FK_VIOLATION_REFERS_TO = 2
private const val INDEX_FK_VIOLATION_CONSTRAINT_INDEX = 3

private class TransactionInProgressImpl(
  private val dbConfig: DbConfig
) : TransactionInProgress, SqlExecutor {
  private val db: SQLiteDatabase = dbConfig.db

  init {
    check(db.inTransaction()) { "Transaction must be in progress" }
  }

  override fun <C : ColumnSet> Query<C>.forEach(
    bind: C.(ArgBindings) -> Unit,
    action: C.(Cursor) -> Unit
  ) = doForEach(seed, bind, action)

  override fun <C : ColumnSet> QueryBuilder<C>.forEach(
    bind: C.(ArgBindings) -> Unit,
    action: C.(Cursor) -> Unit
  ) { this@TransactionInProgressImpl.doForEach(build(), bind, action) }

  override fun <C : ColumnSet, T> Query<C>.flow(
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Flow<T> = doFlow(seed, bind, factory)

  override fun <C : ColumnSet, T> QueryBuilder<C>.flow(
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Flow<T> = this@TransactionInProgressImpl.doFlow(build(), bind, factory)

  override fun <C : ColumnSet, T> Query<C>.sequence(
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Sequence<T> = doSequence(seed, bind, factory)

  override fun <C : ColumnSet, T> QueryBuilder<C>.sequence(
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Sequence<T> = this@TransactionInProgressImpl.doSequence(build(), bind, factory)

  private fun <C : ColumnSet> doForEach(
    seed: QuerySeed<C>,
    bind: C.(ArgBindings) -> Unit,
    action: C.(Cursor) -> Unit
  ) = CursorWrapper.select(seed, db, bind).use { cursor ->
    while (cursor.moveToNext()) seed.sourceSet.action(cursor)
  }

  private fun <C : ColumnSet, T> doFlow(
    seed: QuerySeed<C>,
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Flow<T> = flow {
    CursorWrapper.select(seed, db, bind).use { cursor ->
      while (cursor.moveToNext()) emit(seed.sourceSet.factory(cursor))
    }
  }.flowOn(dbConfig.dispatcher)

  private fun <C : ColumnSet, T> doSequence(
    seed: QuerySeed<C>,
    bind: C.(ArgBindings) -> Unit,
    factory: C.(Cursor) -> T
  ): Sequence<T> = sequence {
    CursorWrapper.select(seed, db, bind).use { cursor ->
      while (cursor.moveToNext()) yield(seed.sourceSet.factory(cursor))
    }
  }

  override fun <C : ColumnSet> Query<C>.longForQuery(
    bind: C.(ArgBindings) -> Unit
  ): Long = db.longForQuery(seed, bind)

  override fun <C : ColumnSet> QueryBuilder<C>.longForQuery(bind: C.(ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.db.longForQuery(build(), bind)

  override fun <C : ColumnSet> Query<C>.stringForQuery(
    bind: C.(ArgBindings) -> Unit
  ): String = db.stringForQuery(seed, bind)

  override fun <C : ColumnSet> QueryBuilder<C>.stringForQuery(
    bind: C.(ArgBindings) -> Unit
  ): String = this@TransactionInProgressImpl.db.stringForQuery(build(), bind)

  override fun <C : ColumnSet> Query<C>.count(
    bind: C.(ArgBindings) -> Unit
  ): Long = doCount(seed, bind)

  override fun <C : ColumnSet> QueryBuilder<C>.count(bind: C.(ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.doCount(build(), bind)

  private fun <C : ColumnSet> doCount(seed: QuerySeed<C>, bindArgs: C.(ArgBindings) -> Unit): Long {
    return db.longForQuery(maybeModifyCountSeed(seed), bindArgs)
  }

  private fun <C : ColumnSet> maybeModifyCountSeed(seed: QuerySeed<C>): QuerySeed<C> =
    if (isCountSelect(seed)) seed else seed.copy(sql = wrapSqlWithSelectCount(seed))

  private fun <C : ColumnSet> isCountSelect(seed: QuerySeed<C>) =
    seed.sql.trim().startsWith("SELECT COUNT(*)", ignoreCase = true)

  private fun <C : ColumnSet> wrapSqlWithSelectCount(seed: QuerySeed<C>): String = buildStr {
    append("SELECT COUNT(*) FROM ( ")
    append(seed.sql)
    append(" )")
  }

  override fun <C : ColumnSet> InsertStatement<C>.insert(bindArgs: C.(ArgBindings) -> Unit): Long =
    execute(this@TransactionInProgressImpl.db, bindArgs)

  override fun <C : ColumnSet> DeleteStatement<C>.delete(bindArgs: C.(ArgBindings) -> Unit): Long =
    execute(this@TransactionInProgressImpl.db, bindArgs)

  override fun <C : ColumnSet> UpdateStatement<C>.update(bindArgs: C.(ArgBindings) -> Unit): Long =
    execute(db, bindArgs)

  override fun Creatable.create(temporary: Boolean) =
    create(this@TransactionInProgressImpl, temporary)

  override fun Creatable.drop() = drop(this@TransactionInProgressImpl)

  /**
   * Builds a query of type ```SELECT COUNT(*) FROM $this [where]```, and the WHERE portion is
   * optional. Invoke [Query.count], binding any necessary variables, which executes the query and
   * returns the long value in the first column of the first row of the result.
   *
   * If the are no binding variables in the resulting SQL, calling [SelectFrom.select] followed by
   * [QueryBuilder.count] on the result is equivalent. eg. ```table.selectAll().count()``` returns a
   * count of all rows in the table.
   */
  fun <C : ColumnSet> SelectFrom<C>.count(
    where: Op<Boolean>?
  ): Query<C> = Query(QueryBuilder(this, where, true))

  override val Creatable.exists
    get() = try {
      val creatableType = masterType.toString()
      val creatableName = identity.unquoted
      SQLiteSchema.selectWhere { type eq creatableType and (name eq creatableName) }.count() == 1L
    } catch (e: Exception) {
      LOG.e(e) { it("Error checking table existence") }
      false
    }

  override val Table.description: TableDescription
    get() {
      require(exists) { "Table ${identity.value} does not exist" }
      val columnsMetadata = mutableListOf<ColumnMetadata>()
      val tableIdentity = this.identity.unquoted
      db.select("""PRAGMA table_info("$tableIdentity")""").use { cursor ->
        while (cursor.moveToNext()) columnsMetadata.add(cursor.columnMetadata)
      }
      return TableDescription(tableIdentity, columnsMetadata)
    }

  override val Table.sql: TableSql
    get() {
      require(exists) { "Table $tableName does not exist" }
      val tableSql = mutableListOf<String>()
      val indices = mutableListOf<String>()
      val triggers = mutableListOf<String>()
      val views = mutableListOf<String>()

      val tableIdentity = identity.unquoted
      val sqlCol = SQLiteSchema.sql
      val typeCol = SQLiteSchema.type
      SQLiteSchema.selects { listOf(sqlCol, typeCol) }
        .where { tbl_name eq tableIdentity }
        .forEach { cursor ->
          val sql = cursor[sqlCol, ""]
          if (sql.isNotEmpty()) {
            when (val masterType = cursor[typeCol].asMasterType()) {
              is MasterType.Table -> tableSql += sql
              is MasterType.Index -> indices += sql
              is MasterType.Trigger -> triggers += sql
              is MasterType.View -> views += sql
              is MasterType.Unknown -> {
                LOG.e { it("With table:'%s' unknown type:'%s'", tableName, masterType) }
              }
            }
          } else LOG.w { it(NULL_SQL_FOUND, tableName, cursor[typeCol], cursor.position) }
        }
      return TableSql(tableName, tableSql, indices, triggers, views)
    }

  override val sqliteVersion: String
    get() = DatabaseUtils.stringForQuery(db, "SELECT sqlite_version() AS sqlite_version", null)

  override fun tegridyCheck(maxErrors: Int): List<String> {
    db.select("PRAGMA INTEGRITY_CHECK($maxErrors)").use { cursor ->
      if (cursor.moveToFirst()) {
        return ArrayList<String>(cursor.count).apply {
          do {
            if (!cursor.isNull(0)) {
              add(cursor.getString(0))
            }
          } while (cursor.moveToNext())
        }
      }
    }
    return emptyList()
  }

  override val Table.foreignKeyList: List<ForeignKeyInfo>
    get() {
      db.select("PRAGMA foreign_key_list(${identity.value})").use { cursor ->
        return if (cursor.count > 0) {
          ArrayList<ForeignKeyInfo>(cursor.count).apply {
            while (cursor.moveToNext()) {
              add(
                ForeignKeyInfo(
                  id = cursor.getLong(INDEX_FK_ID),
                  seq = cursor.getLong(INDEX_FK_SEQ),
                  table = cursor.getString(INDEX_FK_TABLE),
                  from = cursor.getString(INDEX_FK_FROM),
                  to = cursor.getString(INDEX_FK_TO),
                  onUpdate = ForeignKeyAction.fromSQLite(cursor.getString(INDEX_FK_ON_UPDATE)),
                  onDelete = ForeignKeyAction.fromSQLite(cursor.getString(INDEX_FK_ON_DELETE))
                  // match = cursor.getString(7) not supported so we won't read it
                )
              )
            }
          }
        } else {
          emptyList()
        }
      }
    }

  @RequiresApi(Build.VERSION_CODES.O)
  override fun Table.foreignKeyCheck(): List<ForeignKeyViolation> {
    val sql = buildStr {
      append("PRAGMA foreign_key_check")
      append("(")
      append(identity.value)
      append(")")
    }
    db.select(sql).use { cursor ->
      return if (cursor.count > 0) {
        ArrayList<ForeignKeyViolation>(cursor.count).apply {
          while (cursor.moveToNext()) {
            add(
              ForeignKeyViolation(
                tableName = cursor.getString(INDEX_FK_VIOLATION_TABLE),
                rowId = cursor.getOptLong(INDEX_FK_VIOLATION_ROW_ID),
                refersTo = cursor.getString(INDEX_FK_VIOLATION_REFERS_TO),
                failingConstraintIndex = cursor.getLong(INDEX_FK_VIOLATION_CONSTRAINT_INDEX)
              )
            )
          }
        }
      } else {
        emptyList()
      }
    }
  }

  override fun vacuum() {
    db.execSQL("VACUUM")
  }

  override fun exec(sql: String, vararg bindArgs: Any) {
    db.execSQL(sql, bindArgs)
  }

  override fun exec(sql: List<String>) {
    sql.forEach { db.execSQL(it) }
  }
}

private typealias ACursor = android.database.Cursor

internal fun ACursor.getOptLong(columnIndex: Int, ifNullValue: Long = -1): Long =
  if (isNull(columnIndex)) ifNullValue else getLong(columnIndex)
