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
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.expr.ExpressionBuilder
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.schema.ColumnMetadata
import com.ealva.welite.db.schema.MasterType
import com.ealva.welite.db.schema.TableDescription
import com.ealva.welite.db.schema.asMasterType
import com.ealva.welite.db.schema.sqlite_master
import com.ealva.welite.db.statements.ColumnValues
import com.ealva.welite.db.statements.DeleteStatement
import com.ealva.welite.db.statements.InsertStatement
import com.ealva.welite.db.statements.UpdateBuilder
import com.ealva.welite.db.statements.UpdateStatement
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.NO_BIND
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.OnConflict.Unspecified
import com.ealva.welite.db.table.ParamBindings
import com.ealva.welite.db.table.Query
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.SelectFrom
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.select

/**
 * This interface is the entry point of a query DSL and other types of queries. These
 * functions are scoped to a Queryable interface in an attempt to only initiate queries while
 * a transaction is in progress. See [Database.query] for starting a query only under an explicit
 * transaction (with no commit/rollback concerns).
 */
@WeLiteMarker
interface Queryable {
  /** Select a subset of [columns] of this [ColumnSet], returning a [SelectFrom] */
  fun ColumnSet.select(vararg columns: SqlTypeExpression<*>): SelectFrom =
    SelectFrom(columns.distinct(), this)

  /**
   * Select a subset of [columns] of this [ColumnSet] (default is all [columns]), and return a
   * SelectFrom to start building the query.
   */
  fun ColumnSet.select(columns: List<SqlTypeExpression<*>> = this.columns): SelectFrom =
    SelectFrom(columns.distinct(), this)

  /**
   * Select all columns of this [ColumnSet] matching the [where] part of the query
   * Convenience function for: ```selectAll().where(where)```
   */
  fun ColumnSet.selectAllWhere(where: Op<Boolean>?): QueryBuilder = selectAll().where(where)

  /**
   * Select all columns of this [ColumnSet] and call [build] to make the where expression
   */
  fun ColumnSet.selectAllWhere(build: ExpressionBuilder.() -> Op<Boolean>) =
    selectAllWhere(ExpressionBuilder.build())

  /**
   * Select all columns and return a [SelectFrom] to start building the query
   */
  fun ColumnSet.selectAll(): SelectFrom = select()

  /** Makes a [QueryBuilder] from this [SelectFrom] and associated [where] clause */
  fun SelectFrom.where(where: Op<Boolean>?): QueryBuilder

  /**
   * Calls [build] to create the where clause and then makes a [QueryBuilder] from this [SelectFrom]
   * and the where clause.
   */
  fun SelectFrom.where(build: ExpressionBuilder.() -> Op<Boolean>) =
    where(ExpressionBuilder.build())

  /**
   * True if the table, as known via [Table.identity], exists in the database, else false
   */
  val Table.exists: Boolean

  /**
   * The description of the table in the database
   * @throws IllegalArgumentException if this [Table] does not exist in the database
   * @see [Table.exists]
   */
  val Table.description: TableDescription

  /**
   * Full create sql as stored by SQLite
   * @throws IllegalArgumentException if this [Table] does not exist in the database
   * @see [Table.exists]
   */
  val Table.sql: TableSql

  /**
   * Does an integrity check of the entire database. Looks for out-of-order records, missing pages,
   * malformed records, missing index entries, and UNIQUE, CHECK, and NOT NULL constraint errors.
   * Returns a list of strings describe any problems found. Will return at
   * most [n] errors before the analysis quits, with n defaulting to 100. If integrity check
   * finds no errors, a single string with the value 'ok' is returned.
   *
   * Does not find FOREIGN KEY errors. Use [foreignKeyCheck] command for to find errors in FOREIGN
   * KEY constraints.
   */
  fun tegridyCheck(n: Int = 100): List<String>

  val Table.foreignKeyList: List<ForeignKeyInfo>
    @RequiresApi(Build.VERSION_CODES.O) get

  @RequiresApi(Build.VERSION_CODES.O)
  fun Table.foreignKeyCheck(): List<ForeignKeyViolation>

  val sqliteVersion: String
}

data class ForeignKeyInfo(
  val id: Long,
  val seq: Long,
  val table: String,
  val from: String,
  val to: String,
  val onUpdate: ForeignKeyAction,
  val onDelete: ForeignKeyAction
  // val match: String not currently supported in SQLite
)

/**
 * Returned from [Transaction.foreignKeyCheck]
 *
 * The first column is .
 * The second column is
 * The third column is .
 * The fourth column is
 * The fourth column in the output of the foreign_key_check pragma is . When a "table-name" is specified, the only
 * foreign key constraints checked are those created by REFERENCES clauses in the CREATE TABLE
 * statement for table-name.
 */
data class ForeignKeyViolation(
  /** The name of the table that contains the REFERENCES clause */
  val tableName: String,
  /**
   * The row id of the row that contains the invalid REFERENCES clause, or -1 if the child table is
   * a WITHOUT ROWID table.
   */
  val rowId: Long,
  /**
   * The name of the table that is referred to
   */
  val refersTo: String,
  /**
   * The index of the specific foreign key constraint that failed. This is the same integer as the
   * first column in the output of [Transaction.foreignKeyList]
   */
  val failingConstraintIndex: Long
)

/**
 * This interface is the entry point of a SQLite CRUD DSL including [Queryable] functions. These
 * functions are scoped to a Transaction interface in an attempt to only read and write to the DB
 * under an explicit transaction. Functions in this interface require the enclosing transaction to
 * be marked successful or rolled back, but leave the commit/rollback to a different level.
 */
@WeLiteMarker
interface TransactionInProgress : Queryable {
  /**
   * Makes an InsertStatement into which arguments can be bound for insertion.
   *
   *```
   * db.transaction {
   *   with(MediaFileTable.insertValues()) {
   *     insert {
   *       it[mediaUri] = "/dir/Music/file.mpg"
   *       it[fileName] = "filename"
   *       it[mediaTitle] = "title"
   *     }
   *     insert {
   *       it[mediaUri] = "/dir/Music/anotherFile.mpg"
   *       it[fileName] = "2nd file"
   *       it[mediaTitle] = "another title"
   *     }
   *   }
   * }
   * ```
   * [SQLite INSERT](https://sqlite.org/lang_insert.html)
   * @see InsertStatement
   */
  fun Table.insertValues(
    onConflict: OnConflict = Unspecified,
    bind: (ColumnValues) -> Unit
  ): InsertStatement

  /**
   * Does a single insert into the table.
   */
  fun Table.insert(
    onConflict: OnConflict = Unspecified,
    paramBindings: (ParamBindings) -> Unit = NO_BIND,
    bind: (ColumnValues) -> Unit
  ): Long = insertValues(onConflict, bind).insert(paramBindings)

  fun Table.update(
    onConflict: OnConflict = Unspecified,
    bind: (ColumnValues) -> Unit
  ): UpdateBuilder

  fun Table.updateAll(onConflict: OnConflict, bind: (ColumnValues) -> Unit): UpdateStatement

  fun Table.deleteWhere(where: ExpressionBuilder.() -> Op<Boolean>): DeleteStatement

  fun Table.deleteAll(): DeleteStatement

  /**
   * The [VACUUM](https://www.sqlite.org/lang_vacuum.html) command rebuilds the database file,
   * repacking it into a minimal amount of disk space.
   */
  fun vacuum()

  companion object {
    operator fun invoke(db: SQLiteDatabase): TransactionInProgress = TransactionInProgressImpl(db)
  }
}

private class TransactionInProgressImpl(private val db: SQLiteDatabase) : TransactionInProgress {

  init {
    require(db.inTransaction()) { "Transaction must be in progress" }
  }

  override fun SelectFrom.where(where: Op<Boolean>?): QueryBuilder = QueryBuilder(db, this, where)

  override fun Table.insertValues(
    onConflict: OnConflict,
    bind: (ColumnValues) -> Unit
  ): InsertStatement = InsertStatement(db, this, onConflict, bind)

  override fun Table.update(onConflict: OnConflict, bind: (ColumnValues) -> Unit): UpdateBuilder {
    return UpdateBuilder(db, this, onConflict, bind)
  }

  override fun Table.updateAll(
    onConflict: OnConflict,
    bind: (ColumnValues) -> Unit
  ): UpdateStatement {
    return UpdateStatement(db, this, onConflict, bind)
  }

  override fun Table.deleteWhere(where: ExpressionBuilder.() -> Op<Boolean>): DeleteStatement {
    return DeleteStatement(db, this, ExpressionBuilder.where())
  }

  override fun Table.deleteAll(): DeleteStatement {
    return DeleteStatement(db, this, null)
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
  fun SelectFrom.count(where: Op<Boolean>?): Query = QueryBuilder(db, this, where, true).build()

  override val Table.exists
    get() = try {
      val tableName = identity().unquoted
      val tableType = MasterType.Table.toString()
      sqlite_master.selectAll().where {
        (sqlite_master.type eq tableType) and (sqlite_master.tbl_name eq tableName)
      }.count() == 1L
    } catch (e: Exception) {
      LOG.e(e) { it("Error checking table existence") }
      false
    }

  override val Table.description: TableDescription
    get() {
      require(exists) { "Table ${identity().value} does not exist" }
      val columnsMetadata = mutableListOf<ColumnMetadata>()
      val tableIdentity = this.identity().unquoted
      db.select("""PRAGMA table_info("$tableIdentity")""").use { cursor ->
        while (cursor.moveToNext()) {
          columnsMetadata.add(ColumnMetadata.fromCursor(cursor))
        }
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

      val sqlCol = sqlite_master.sql
      val typeCol = sqlite_master.type
      sqlite_master.select(sqlCol, typeCol)
        .where { sqlite_master.tbl_name eq identity().unquoted }
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

  override fun tegridyCheck(n: Int): List<String> {
    db.select(buildString { append("PRAGMA INTEGRITY_CHECK(", n, ")") }).use { cursor ->
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
      db.select(
        buildString { append("PRAGMA foreign_key_list('", identity().unquoted, "')") }
      ).use { cursor ->
        return if (cursor.count > 0) {
          ArrayList<ForeignKeyInfo>(cursor.count).apply {
            while (cursor.moveToNext()) {
              add(
                ForeignKeyInfo(
                  id = cursor.getLong(0),
                  seq = cursor.getLong(1),
                  table = cursor.getString(2),
                  from = cursor.getString(3),
                  to = cursor.getString(4),
                  onUpdate = ForeignKeyAction.fromSQLite(cursor.getString(5)),
                  onDelete = ForeignKeyAction.fromSQLite(cursor.getString(6))
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

  /**
   * Checks this table for failing foreign key constraints. Returns list containing a
   * [ForeignKeyViolation] for each violation, empty list if none. The only
   * foreign key constraints checked are those created by REFERENCES clauses in the CREATE TABLE
   * statement for table-name.
   */
  @RequiresApi(Build.VERSION_CODES.O)
  override fun Table.foreignKeyCheck(): List<ForeignKeyViolation> {
    db.select(buildString {
      append("PRAGMA foreign_key_check")
      append("('")
      append(identity().unquoted)
      append("')")
    }).use { cursor ->
      return if (cursor.count > 0) {
        ArrayList<ForeignKeyViolation>(cursor.count).apply {
          while (cursor.moveToNext()) {
            add(
              ForeignKeyViolation(
                tableName = cursor.getString(0),
                rowId = if (cursor.isNull(1)) -1 else cursor.getLong(1),
                refersTo = cursor.getString(2),
                failingConstraintIndex = cursor.getLong(3)
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
}

/**
 * Transaction implements [AutoCloseable] and is typically used within a [use] block.
 * ```kotlin
 * beginTransaction().use { txn ->
 *    doDbWork()
 *    txn.markSuccessful()
 * } // txn is automatically closed leaving the use scope
 * ```
 * The commit/rollback occurs at the end of the [use] block when the txn is closed.
 */
@WeLiteMarker
interface Transaction : TransactionInProgress, AutoCloseable {
  val isClosed: Boolean
  val successful: Boolean
  val rolledBack: Boolean

  /**
   * Marks this txn as successful and will commit on close. No more database work should be done
   * between this call and closing the txn
   * @throws IllegalStateException if the txn is already closed or rolled back
   */
  fun setSuccessful()

  /**
   * Mark this txn rolled back and txn ends during close. No more database work should be done
   * between this call and closing the txn
   * @throws IllegalStateException if the txn is already closed
   */
  fun rollback()

  /**
   * Close is idempotent. This means you could call it before the txn leaves [use] block, but
   * the expected design is control flow that "immediately" leaves the [use]
   * block after either [setSuccessful] or [rollback]. Close happens exiting the [use] scope
   */
  override fun close()

  companion object {
    operator fun invoke(
      db: SQLiteDatabase,
      exclusiveLock: Boolean,
      unitOfWork: String,
      throwIfNoChoice: Boolean
    ): Transaction {
      if (exclusiveLock) db.beginTransaction() else db.beginTransactionNonExclusive()
      return TransactionImpl(
        db,
        exclusiveLock,
        unitOfWork,
        throwIfNoChoice,
        TransactionInProgress(db)
      )
    }
  }
}

private val LOG by lazyLogger(Transaction::class)
private const val TXN_NOT_MARKED =
  "<<==Warning==>> Closing a txn without it being marked successful or rolled back. %s"
private const val NULL_SQL_FOUND = "Null sql table:%s type=%s position:%d"

private class TransactionImpl(
  private val db: SQLiteDatabase,
  private val exclusiveLock: Boolean,
  private val unitOfWork: String,
  private val throwIfNoChoice: Boolean,
  private val transactionInProgress: TransactionInProgress
) : TransactionInProgress by transactionInProgress, Transaction {
  override var successful = false
    private set
  override var rolledBack = false
    private set
  override var isClosed = false
    private set

  override fun setSuccessful() {
    check(!isClosed) { "Txn $unitOfWork already closed" }
    check(!rolledBack) { "Txn $unitOfWork rolled back, cannot be marked successful" }
    if (!successful) {
      db.setTransactionSuccessful()
      successful = true
    }
  }

  override fun rollback() {
    check(!isClosed) { "Txn $unitOfWork already closed" }
    successful = false
    rolledBack = true
  }

  override fun close() {
    if (!isClosed) {
      try {
        isClosed = true
        if (!successful && !rolledBack) {
          LOG.e(RuntimeException("Txn $unitOfWork unsuccessful")) { it(TXN_NOT_MARKED, unitOfWork) }
          if (throwIfNoChoice) throw NeitherSuccessNorRollbackException(unitOfWork)
          // Transaction has not been marked successful and will rollback automatically
        }
      } finally {
        db.endTransaction()
      }
    }
  }

  override fun toString(): String {
    return """Txn='$unitOfWork' closed=$isClosed exclusive=$exclusiveLock success=$successful rolledBack=$rolledBack"""
  }
}
