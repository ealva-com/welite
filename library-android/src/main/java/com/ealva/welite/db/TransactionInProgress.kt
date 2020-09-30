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
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.CursorWrapper
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.NO_ARGS
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.OnConflict.Unspecified
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.QuerySeed
import com.ealva.welite.db.table.SQLiteMaster
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDescription
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.asMasterType
import com.ealva.welite.db.table.columnMetadata
import com.ealva.welite.db.table.longForQuery
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.stringForQuery
import com.ealva.welite.db.table.where
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
interface TransactionInProgress : Queryable {
  /**
   * Execute this InsertStatement: clears the bindings,
   * provides for new bound parameters via [bindArgs], and executes the insert, returning the
   * row ID of the row inserted if successful else -1
   */
  fun InsertStatement.insert(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Does a single insert into the table. Builds an [InsertStatement] and invokes
   * [InsertStatement.insert]. The InsertStatement is single use and not exposed to the client
   * for reuse.
   */
  fun <T : Table> T.insert(
    onConflict: OnConflict = Unspecified,
    bindArgs: (ArgBindings) -> Unit = NO_ARGS,
    assignColumns: T.(ColumnValues) -> Unit
  ): Long = InsertStatement(this, onConflict, assignColumns).insert(bindArgs)

  /**
   * Binds arguments to this DeleteStatement and perform a DELETE, returning the number of
   * rows removed.
   */
  fun DeleteStatement.delete(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Does a single delete on the table. Builds a [DeleteStatement] and calls
   * [DeleteStatement.delete]. The DeleteStatement is single use and not exposed to the client.
   */
  fun <T : Table> T.delete(
    bindArgs: (ArgBindings) -> Unit = NO_ARGS,
    where: () -> Op<Boolean>
  ): Long = DeleteStatement(this, where()).delete(bindArgs)

  fun <T : Table> T.deleteAll(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long =
    DeleteStatement(this, null).delete(bindArgs)

  /**
   * Clears bindings, binds arguments to this UpdateStatement, and execute the statement
   * returning the number of rows updated.
   */
  fun UpdateStatement.update(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Create a Creatable database entity (Table, Index, View, ...)
   */
  fun Creatable.create(temporary: Boolean = false)

  /**
   * Drop a Creatable database entity (Table, Index, View, ...)
   */
  fun Creatable.drop()

  /**
   * The [VACUUM](https://www.sqlite.org/lang_vacuum.html) command rebuilds the database file,
   * repacking it into a minimal amount of disk space.
   */
  fun vacuum()

  companion object {
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
    require(db.inTransaction()) { "Transaction must be in progress" }
  }

  override fun Query.forEach(bind: (ArgBindings) -> Unit, action: (Cursor) -> Unit) {
    doForEach(seed, bind, action)
  }

  override fun QueryBuilder.forEach(bind: (ArgBindings) -> Unit, action: (Cursor) -> Unit) {
    this@TransactionInProgressImpl.doForEach(build(), bind, action)
  }

  override fun <T> Query.flow(bind: (ArgBindings) -> Unit, factory: (Cursor) -> T): Flow<T> =
    doFlow(seed, bind, factory)

  override fun <T> QueryBuilder.flow(bind: (ArgBindings) -> Unit, factory: (Cursor) -> T): Flow<T> =
    this@TransactionInProgressImpl.doFlow(build(), bind, factory)

  override fun <T> Query.sequence(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = doSequence(seed, bind, factory)

  override fun <T> QueryBuilder.sequence(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = this@TransactionInProgressImpl.doSequence(build(), bind, factory)

  private fun doForEach(
    seed: QuerySeed,
    bind: (ArgBindings) -> Unit,
    action: (Cursor) -> Unit
  ) = CursorWrapper.select(seed, db, bind).use { cursor ->
    while (cursor.moveToNext()) action(cursor)
  }

  private fun <T> doFlow(
    seed: QuerySeed,
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Flow<T> = flow {
    CursorWrapper.select(seed, db, bind).use { cursor ->
      while (cursor.moveToNext()) emit(factory(cursor))
    }
  }.flowOn(dbConfig.dispatcher)

  private fun <T> doSequence(
    seed: QuerySeed,
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = sequence {
    CursorWrapper.select(seed, db, bind).use { cursor ->
      while (cursor.moveToNext()) yield(factory(cursor))
    }
  }

  override fun Query.longForQuery(bind: (ArgBindings) -> Unit): Long = db.longForQuery(seed, bind)

  override fun QueryBuilder.longForQuery(bind: (ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.db.longForQuery(build(), bind)

  override fun Query.stringForQuery(bind: (ArgBindings) -> Unit): String =
    db.stringForQuery(seed, bind)

  override fun QueryBuilder.stringForQuery(bind: (ArgBindings) -> Unit): String =
    this@TransactionInProgressImpl.db.stringForQuery(build(), bind)

  override fun Query.count(bind: (ArgBindings) -> Unit): Long = doCount(seed, bind)

  override fun QueryBuilder.count(bind: (ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.doCount(build(), bind)

  private fun doCount(seed: QuerySeed, bindArgs: (ArgBindings) -> Unit): Long {
    return db.longForQuery(maybeModifyCountSeed(seed), bindArgs)
  }

  private fun maybeModifyCountSeed(seed: QuerySeed): QuerySeed =
    if (isCountSelect(seed)) seed else seed.copy(sql = wrapSqlWithSelectCount(seed))

  private fun isCountSelect(seed: QuerySeed) =
    seed.sql.trim().startsWith("SELECT COUNT(*)", ignoreCase = true)

  private fun wrapSqlWithSelectCount(seed: QuerySeed): String = buildStr {
    append("SELECT COUNT(*) FROM ( ")
    append(seed.sql)
    append(" )")
  }

  override fun InsertStatement.insert(bindArgs: (ArgBindings) -> Unit): Long =
    execute(this@TransactionInProgressImpl.db, bindArgs)

  override fun DeleteStatement.delete(bindArgs: (ArgBindings) -> Unit): Long =
    execute(this@TransactionInProgressImpl.db, bindArgs)

  override fun UpdateStatement.update(bindArgs: (ArgBindings) -> Unit): Long = execute(db, bindArgs)

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
  fun SelectFrom.count(where: Op<Boolean>?): Query = Query(QueryBuilder(this, where, true))

  override val Creatable.exists
    get() = try {
      val tableType = masterType.toString()
      SQLiteMaster.selectWhere {
        SQLiteMaster.type eq tableType and (SQLiteMaster.name eq identity.unquoted)
      }.count() == 1L
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

      val sqlCol = SQLiteMaster.sql
      val typeCol = SQLiteMaster.type
      SQLiteMaster.select(sqlCol, typeCol)
        .where { SQLiteMaster.tbl_name eq identity.unquoted }
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
