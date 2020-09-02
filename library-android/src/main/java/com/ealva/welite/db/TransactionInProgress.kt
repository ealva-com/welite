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

package com.ealva.welite.db

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.annotation.RequiresApi
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.schema.MasterType
import com.ealva.welite.db.schema.SQLiteMaster
import com.ealva.welite.db.schema.asMasterType
import com.ealva.welite.db.statements.ColumnValues
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.InsertStatement
import com.ealva.welite.db.statements.UpdateStatement
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.ColumnMetadata
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.Cursor
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.FieldType
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.NO_ARGS
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.OnConflict.Unspecified
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.QuerySeed
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDescription
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectWhere
import com.ealva.welite.db.table.where
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.Row
import com.ealva.welite.db.type.buildStr
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.Arrays

private val LOG by lazyLogger(TransactionInProgress::class)
private const val NULL_SQL_FOUND = "Null sql table:%s type=%s position:%d"

/**
 * These functions are scoped to a Transaction interface in an attempt to only read and write to
 * the DB under an explicit transaction. Functions in this interface require the enclosing
 * transaction to be marked successful or rolled back, but leave the commit/rollback to a different
 * level.
 */
@WeLiteMarker
interface TransactionInProgress : Queryable {
  /**
   * Insert a row binding any parameters with [bindArgs]
   *
   * An InsertStatement contains a compiled SQLiteStatement and this method clears the bindings,
   * provides for new bound parameters via [bindArgs], and executes the insert
   * @return the row ID of the last row inserted if successful else -1
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

  fun DeleteStatement.delete(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long

  /**
   * Does a single delete on the table. Builds a [DeleteStatement] and invokes
   * [DeleteStatement.execute]. The DeleteStatement is single use and not exposed to the client
   * for reuse.
   */
  fun <T : Table> T.delete(
    bindArgs: (ArgBindings) -> Unit = NO_ARGS,
    where: () -> Op<Boolean>
  ): Long = DeleteStatement(this, where()).delete(bindArgs)

  fun UpdateStatement.update(bindArgs: (ArgBindings) -> Unit = NO_ARGS): Long

  fun Creatable.create()

  fun Creatable.drop()

  /**
   * The [VACUUM](https://www.sqlite.org/lang_vacuum.html) command rebuilds the database file,
   * repacking it into a minimal amount of disk space.
   */
  fun vacuum()

  companion object {
    internal operator fun invoke(dbConfig: DbConfig): TransactionInProgress =
      TransactionInProgressImpl(dbConfig)
  }
}

fun TransactionInProgress.createAll(list: List<Creatable>) = list.forEach { it.create() }

fun TransactionInProgress.dropAll(list: List<Creatable>) = list.forEach { it.drop() }

private const val UNBOUND = "Unbound"

private class QueryArgs(private val argTypes: List<PersistentType<*>>) : ArgBindings {
  private val arguments = Array(argTypes.size) { UNBOUND }

  override operator fun <T> set(index: Int, value: T?) {
    require(index in argTypes.indices) { "Arg types $index out of bounds ${argTypes.indices}" }
    require(index in arguments.indices) { "Args $index out of bounds ${arguments.indices}" }
    arguments[index] = argTypes[index].valueToString(value)
  }

  override val argCount: Int
    get() = argTypes.size

  val args: Array<String>
    get() = arguments.copyOf()
}

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
    this@TransactionInProgressImpl.doForEach(build().seed, bind, action)
  }

  private fun doForEach(
    seed: QuerySeed,
    bindArgs: (ArgBindings) -> Unit,
    action: (Cursor) -> Unit
  ) = with(seed) {
    QueryArgs(types).let { queryArgs ->
      bindArgs(queryArgs)
      DbCursorWrapper(db.select(sql, queryArgs.args), fields.mapExprToIndex()).use { cursor ->
        while (cursor.moveToNext()) action(cursor)
      }
    }
  }

  override fun <T> Query.flow(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Flow<T> = doFlow(seed, bind, factory)

  override fun <T> QueryBuilder.flow(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Flow<T> = this@TransactionInProgressImpl.doFlow(build().seed, bind, factory)

  private fun <T> doFlow(
    seed: QuerySeed,
    bindArgs: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Flow<T> = flow {
    with(seed) {
      QueryArgs(types).let { queryArgs ->
        bindArgs(queryArgs)
        DbCursorWrapper(db.select(sql, queryArgs.args), fields.mapExprToIndex()).use { cursor ->
          while (cursor.moveToNext()) emit(factory(cursor))
        }
      }
    }
  }.flowOn(dbConfig.dispatcher)

  override fun <T> Query.sequence(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = doSequence(seed, bind, factory)

  override fun <T> QueryBuilder.sequence(
    bind: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = this@TransactionInProgressImpl.doSequence(build().seed, bind, factory)

  private fun <T> doSequence(
    seed: QuerySeed,
    bindArgs: (ArgBindings) -> Unit,
    factory: (Cursor) -> T
  ): Sequence<T> = sequence {
    with(seed) {
      QueryArgs(types).let { queryArgs ->
        bindArgs(queryArgs)
        DbCursorWrapper(db.select(sql, queryArgs.args), fields.mapExprToIndex()).use { cursor ->
          while (cursor.moveToNext()) yield(factory(cursor))
        }
      }
    }
  }

  override fun Query.longForQuery(bind: (ArgBindings) -> Unit): Long =
    doLongForQuery(seed, bind)

  override fun QueryBuilder.longForQuery(bind: (ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.doLongForQuery(build().seed, bind)

  private fun doLongForQuery(seed: QuerySeed, bindArgs: (ArgBindings) -> Unit): Long = with(seed) {
    QueryArgs(seed.types).let { queryArgs ->
      bindArgs(queryArgs)
      db.longForQuery(sql, queryArgs.args)
    }
  }

  override fun Query.stringForQuery(bind: (ArgBindings) -> Unit): String =
    doStringForQuery(seed, bind)

  override fun QueryBuilder.stringForQuery(bind: (ArgBindings) -> Unit): String =
    this@TransactionInProgressImpl.doStringForQuery(build().seed, bind)

  private fun doStringForQuery(
    seed: QuerySeed,
    bindArgs: (ArgBindings) -> Unit
  ): String = with(seed) {
    QueryArgs(types).let { queryArgs ->
      bindArgs(queryArgs)
      db.stringForQuery(sql, queryArgs.args)
    }
  }

  override fun Query.count(bind: (ArgBindings) -> Unit): Long = doCount(seed, bind)

  override fun QueryBuilder.count(bind: (ArgBindings) -> Unit): Long =
    this@TransactionInProgressImpl.doCount(build().seed, bind)

  private fun doCount(seed: QuerySeed, bindArgs: (ArgBindings) -> Unit): Long {
    return doLongForQuery(maybeModifyCountSeed(seed), bindArgs)
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

  override fun InsertStatement.insert(bindArgs: (ArgBindings) -> Unit): Long {
    return execute(this@TransactionInProgressImpl.db, bindArgs)
  }

  override fun DeleteStatement.delete(bindArgs: (ArgBindings) -> Unit): Long {
    return execute(this@TransactionInProgressImpl.db, bindArgs)
  }

  override fun UpdateStatement.update(bindArgs: (ArgBindings) -> Unit): Long {
    return execute(db, bindArgs)
  }

  override fun Creatable.create() {
    create(this@TransactionInProgressImpl)
  }

  override fun Creatable.drop() {
    drop(this@TransactionInProgressImpl)
  }

  /**
   * Builds a query of type ```SELECT COUNT(*) FROM $this [where]```, and the WHERE portion is
   * optional. Invoke [Query.count], binding any necessary variables, which executes the query and
   * returns the long value in the first column of the first row of the result.
   *
   * If the are no binding variables in the resulting SQL, calling [SelectFrom.select] followed by
   * [QueryBuilder.count] on the result is equivalent. eg. ```table.selectAll().count()``` returns a
   * count of all rows in the table.
   */
  fun SelectFrom.count(where: Op<Boolean>?): Query =
    QueryBuilder(this, where, true).build()

  override val Table.exists
    get() = try {
      val tableType = MasterType.Table.toString()
      SQLiteMaster.selectWhere {
        (SQLiteMaster.type eq tableType) and (SQLiteMaster.tbl_name eq identity.unquoted)
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
          cursor.getOptional(sqlCol)?.let { sql ->
            when (val masterType = cursor[typeCol].asMasterType()) {
              is MasterType.Table -> tableSql += sql
              is MasterType.Index -> indices += sql
              is MasterType.Trigger -> triggers += sql
              is MasterType.View -> views += sql
              is MasterType.Unknown -> {
                LOG.e { it("With table:'%s' unknown type:'%s'", tableName, masterType) }
              }
            }
          } ?: LOG.w { it(NULL_SQL_FOUND, tableName, cursor[typeCol], cursor.position) }
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
            if (!cursor.isNull(0)) add(cursor.getString(0))
          } while (cursor.moveToNext())
        }
      }
    }
    return emptyList()
  }

  fun StringBuilder.append(vararg strings: String) = apply {
    strings.forEach { append(it) }
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
    db.select(
      buildStr {
        append("PRAGMA foreign_key_check")
        append("(")
        append(identity.value)
        append(")")
      }
    ).use { cursor ->
      return if (cursor.count > 0) {
        ArrayList<ForeignKeyViolation>(cursor.count).apply {
          while (cursor.moveToNext()) {
            add(
              ForeignKeyViolation(
                tableName = cursor.getString(INDEX_FK_VIOLATION_TABLE),
                rowId = if (cursor.isNull(INDEX_FK_VIOLATION_ROW_ID)) -1 else cursor.getLong(
                  INDEX_FK_VIOLATION_ROW_ID
                ),
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
    db.execSQL("VACUUM;")
  }

  @Suppress("unused")
  fun execSql(sql: String, bindArgs: List<Any?> = emptyList()) {
    if (bindArgs.isEmpty()) db.execSQL(sql) else db.execSQL(sql, bindArgs.toTypedArray())
  }

  override fun exec(sql: String, vararg bindArgs: Any) {
    db.execSQL(sql, bindArgs)
  }

  override fun exec(sql: List<String>) {
    sql.forEach { db.execSQL(it) }
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

private typealias ExpressionToIndexMap = Object2IntMap<SqlTypeExpression<*>>

private fun List<SqlTypeExpression<*>>.mapExprToIndex(): ExpressionToIndexMap {
  return Object2IntOpenHashMap<SqlTypeExpression<*>>(size).apply {
    defaultReturnValue(-1)
    this@mapExprToIndex.forEachIndexed { index, expression -> put(expression, index) }
  }
}

private typealias ACursor = android.database.Cursor

private class DbCursorWrapper(
  private val cursor: ACursor,
  private val exprMap: ExpressionToIndexMap
) : Cursor, Row, AutoCloseable {
  override val count: Int
    get() = cursor.count

  override val position: Int
    get() = cursor.position

  fun moveToNext(): Boolean = cursor.moveToNext()

  private fun <T> SqlTypeExpression<T>.index() = exprMap.getInt(this)

  override fun <T> getOptional(expression: SqlTypeExpression<T>): T? =
    expression.persistentType.columnValue(this, expression.index())

  override fun <T> get(expression: SqlTypeExpression<T>): T {
    return getOptional(expression) ?: throw IllegalStateException(unexpectedNullMessage(expression))
  }

  override fun getBlob(columnIndex: Int): ByteArray = cursor.getBlob(columnIndex)
  override fun getString(columnIndex: Int): String = cursor.getString(columnIndex)
  override fun getShort(columnIndex: Int) = cursor.getShort(columnIndex)
  override fun getInt(columnIndex: Int) = cursor.getInt(columnIndex)
  override fun getLong(columnIndex: Int) = cursor.getLong(columnIndex)
  override fun getFloat(columnIndex: Int) = cursor.getFloat(columnIndex)
  override fun getDouble(columnIndex: Int) = cursor.getDouble(columnIndex)
  override fun isNull(columnIndex: Int) = cursor.isNull(columnIndex)
  override fun columnName(columnIndex: Int): String = cursor.getColumnName(columnIndex)
  override fun close() = cursor.close()

  private fun <T> unexpectedNullMessage(expression: SqlTypeExpression<T>) =
    "Unexpected NULL reading column=${expression.name()} of expected type ${expression.sqlType}"

  private fun <T> SqlTypeExpression<T>.name() = cursor.getColumnName(index())
}

internal fun SQLiteDatabase.select(sql: String, args: Array<String>? = null): ACursor {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return rawQuery(sql, args)
}

internal fun SQLiteDatabase.longForQuery(sql: String, args: Array<String>? = null): Long {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.longForQuery(this, sql, args)
}

internal fun SQLiteDatabase.stringForQuery(sql: String, args: Array<String>? = null): String {
  if (Query.logQueryPlans) logQueryPlan(sql, args)
  return DatabaseUtils.stringForQuery(this, sql, args)
}

private fun SQLiteDatabase.logQueryPlan(sql: String, selectionArgs: Array<String>?) {
  LOG.i { it("Plan for:\nSQL:%s\nargs:%s", sql, Arrays.toString(selectionArgs)) }
  explainQueryPlan(sql, selectionArgs).forEachIndexed { index, toLog ->
    LOG.i { it("%d: %s", index, toLog) }
  }
}

private fun SQLiteDatabase.explainQueryPlan(
  sql: String,
  selectionArgs: Array<String>?
): List<String> {
  return mutableListOf<String>().apply {
    rawQuery("""EXPLAIN QUERY PLAN $sql""", selectionArgs).use { c ->
      while (c.moveToNext()) {
        add(
          buildStr {
            for (i in 0 until c.columnCount) {
              append(c.getColumnName(i))
              append(':')
              append(c.getString(i))
              append(", ")
            }
          }
        )
      }
    }
  }
}

private const val ID_COLUMN = 0
private const val NAME_COLUMN = 1
private const val TYPE_COLUMN = 2
private const val NULLABLE_COLUMN = 3
private const val DEF_VAL_COLUMN = 4
private const val PK_COLUMN = 5
private const val NOT_NULLABLE = 1
private val ACursor.columnMetadata: ColumnMetadata
  get() = ColumnMetadata(
    getInt(ID_COLUMN),
    getString(NAME_COLUMN),
    FieldType.fromType(getString(TYPE_COLUMN)),
    getInt(NULLABLE_COLUMN) != NOT_NULLABLE, // 1 = not nullable
    if (isNull(DEF_VAL_COLUMN)) "NULL" else getString(DEF_VAL_COLUMN),
    getInt(PK_COLUMN)
  )
