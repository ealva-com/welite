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

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.WeResult.Success
import com.ealva.welite.db.WeResult.Unsuccessful
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.schema.MasterType
import com.ealva.welite.db.schema.TableDependencies
import com.ealva.welite.db.schema.SQLiteMaster
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.type.toStatementString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.Locale

/**
 * Has the [tableName] and all the create SQL statements associated with it and a type.  For both
 * tables and indices, the SQL field is the text of the original CREATE TABLE or CREATE INDEX
 * statement that created the table or index. For automatically created indices (used to implement
 * the PRIMARY KEY or UNIQUE constraints) the sql field is NULL.
 */
data class TableSql(
  /** The table name as in sqlite_master.tbl_name */
  val tableName: String,
  /** List of create statements to create the Table [tableName] */
  val table: List<String>,
  /** List of create index statements for Table [tableName] */
  val indices: List<String>,
  /** List of create trigger statements associated with Table [tableName] */
  val triggers: List<String>,
  /** List of create view statements associate with Table [tableName] */
  val views: List<String>
)

@WeLiteMarker
interface Database {
  /**
   * Tables this Database instance is aware of, in creation order
   */
  val tables: List<Table>

  /**
   * If false an IllegalStateException is thrown if work is dispatched the UI thread. Default is
   * false (don't do I/O on the UI thread). This is here primarily for testing coroutines. WeLite
   * tests use a rule that confines coroutines to a single thread to simplify testing.
   */
  var allowWorkOnUiThread: Boolean

  /**
   * Begin a [Transaction] named [unitOfWork] and call [work] with the transaction as the receiver.
   * WeLite attempts to require an explicit transaction be started for all DB access (otherwise
   * SQLite will start an implicit txn). By default [exclusive] is false and the transaction runs in
   * IMMEDIATE mode. All db work should be completed within the [work] block and the txn must be
   * marked as either successful, [Transaction.setSuccessful], or rolled back,
   * [Transaction.rollback].
   *
   * When the txn closes, if neither success nor rollback has been called an error will be logged.
   * If [throwIfNoChoice] is true, which is the default, a [NeitherSuccessNorRollbackException] will
   * be thrown. If [throwIfNoChoice] is false execution continues but the txn will not be committed.
   *
   * If [exclusive] is false, which is the default, a reserved lock is acquired. While
   * holding a reserved lock, this session is allowed to read or write but other sessions are only
   * allowed to read.
   *
   * If [exclusive] is true, an exclusive lock is acquired. While holding an exclusive
   * lock, this session is allowed to read or write but no other sessions are allowed to
   * access the database.
   *
   * The work function will be executed on the underlying CoroutineDispatcher which is typically
   * either Dispatchers.IO or a custom dispatcher just for DB work.
   *
   * [SQLite docs](https://www.sqlite.org/lang_transaction.html): No reads or writes occur
   * except within a transaction. Any command that accesses the database (basically, any SQL
   * command, except a few PRAGMA statements) will automatically start a transaction if one is not
   * already in effect. Automatically started transactions are committed when the last SQL statement
   * finishes.
   *
   * @see query
   * @throws IllegalStateException if the database has been closed
   * @throws WeLiteException if an exception is thrown from [work]. The [WeLiteException.cause]
   * is set to the underlying exception. If an underlying dispatcher executes on the UI thread
   * an IllegalStateException is thrown, which is caught, and wrapped in a WeLiteException
   */
  suspend fun <R> transaction(
    exclusive: Boolean = false,
    unitOfWork: String = "Unnamed Txn",
    throwIfNoChoice: Boolean = true,
    work: suspend Transaction.() -> R
  ): R

  /**
   * Called to do queries and no other CRUD operation. This is the same as starting a [transaction]
   * and only doing queries.
   *
   * WeLite attempts to require an explicit transaction be started for all DB access (otherwise
   * SQLite will start an implicit txn). This function is fundamentally the same as [transaction],
   * except the client is indicating no other CRUD operations will be performed.
   * [Transaction.setSuccessful] is always called on the underlying transaction as queries don't
   * require commit/rollback
   *
   * The work function will be executed on the underlying CoroutineDispatcher which is typically
   * either Dispatchers.IO or a custom dispatcher just for DB work.
   *
   * [SQLite docs](https://www.sqlite.org/lang_transaction.html): No reads or writes occur
   * except within a transaction. Any command that accesses the database (basically, any SQL
   * command, except a few PRAGMA statements) will automatically start a transaction if one is not
   * already in effect. Automatically started transactions are committed when the last SQL statement
   * finishes.
   *
   * @see transaction
   * @throws IllegalStateException if the database has been closed
   * @throws WeLiteException if an exception is thrown from [work]. The [WeLiteException.cause]
   * is set to the underlying exception. If an underlying dispatcher executes on the UI thread
   * an IllegalStateException is thrown, which is caught, and wrapped in a WeLiteException
   */
  suspend fun <R> query(
    exclusive: Boolean = false,
    unitOfWork: String = "Unnamed Txn",
    work: suspend Queryable.() -> R
  ): R

  /**
   * True if this thread is currently within a transaction, ie. a transaction is in progress.
   */
  val inTransaction: Boolean

  /**
   * Provides the interface to an ongoing transaction. Client is not concerned with commit/rollback
   * which is handled at another level
   *
   * The [work] function is executed on the current thread, unlike [transaction] and [query], which
   * will use coroutines to execute off the main thread.
   *
   * @throws IllegalStateException if the current thread is not in a transaction, the database has
   * been closed, or the caller is on the UI thread
   * @throws WeLiteException if an exception is thrown from [work]. The [WeLiteException.cause]
   * is set to the underlying exception.
   * @see inTransaction
   */
  fun <R> ongoingTransaction(work: TransactionInProgress.() -> R): R

  /**
   * Close this Database connection. Calling other public functions after close will result in an
   * IllegalStateException. This method isn't usually needed for Android apps. Typically
   * Android apps will leave the DB connected for the life of the application and allow for
   * cleanup during the GC->finalization of the underlying DB object.
   *
   * This function is idempotent.
   *
   * If the client needs to do something like overwrite the DB file:
   * * Call close on this Database instance and release all references to it
   * * Do file copying, etc.
   * * Create a new [Database] instance which will open the new DB file
   */
  fun close()

  companion object {
    /**
     * Attempts to release memory that SQLite holds but does not require to operate properly.
     * Typically this memory will come from the page cache. This function may be called when
     * when Android reports low memory to the app via [android.app.Application.onTrimMemory] or
     * other [android.content.ComponentCallbacks2] instance.
     * @return the number of bytes actually released
     */
    fun releaseMemory(): Int {
      return SQLiteDatabase.releaseMemory()
    }

    operator fun invoke(
      context: Context,
      fileName: String,
      version: Int,
      tables: List<Table>,
      migrations: List<Migration>,
      requireMigration: Boolean = true,
      dispatcher: CoroutineDispatcher = Dispatchers.IO,
      configure: DatabaseLifecycle.() -> Unit = {}
    ): Database {
      return doMake(
        context,
        fileName,
        version,
        tables,
        migrations,
        requireMigration,
        dispatcher,
        configure
      )
    }

    operator fun invoke(
      context: Context,
      version: Int,
      tables: List<Table>,
      migrations: List<Migration>,
      requireMigration: Boolean = true,
      dispatcher: CoroutineDispatcher = Dispatchers.IO,
      configure: DatabaseLifecycle.() -> Unit = {}
    ): Database {
      return doMake(
        context,
        null,
        version,
        tables,
        migrations,
        requireMigration,
        dispatcher,
        configure
      )
    }

    private fun doMake(
      context: Context,
      fileName: String?,
      version: Int,
      tables: List<Table>,
      migrations: List<Migration>,
      requireMigration: Boolean,
      dispatcher: CoroutineDispatcher,
      configure: (DatabaseLifecycle) -> Unit
    ): Database {
      return WeLiteDatabase(
        context,
        fileName,
        version,
        tables,
        migrations,
        requireMigration,
        dispatcher,
        configure
      )
    }
  }
}

private val LOG by lazyLogger(Database::class)

interface DbConfig {
  val dispatcher: CoroutineDispatcher
  val db: SQLiteDatabase
}

private class WeLiteDatabase(
  context: Context,
  fileName: String?,
  version: Int,
  tables: List<Table>,
  migrations: List<Migration>,
  requireMigration: Boolean,
  override val dispatcher: CoroutineDispatcher,
  configure: DatabaseLifecycle.() -> Unit
) : Database, DbConfig, AutoCloseable {
  private var closed = false

  private val openHelper: OpenHelper =
    OpenHelper(context, this, fileName, version, tables, migrations, requireMigration, configure)

  override val tables: List<Table>
    get() = openHelper.tablesInCreateOrder

  override var allowWorkOnUiThread: Boolean = false

  override fun close() {
    if (!closed) {
      LOG.i { it("Closing Database") }
      openHelper.close()
      closed = true
    }
  }

  inline fun Exception.asUnsuccessful(errorMessage: () -> String): Unsuccessful {
    return Unsuccessful.make(errorMessage(), this)
  }

  /**
   * Note: The client may not use the transaction return value (could be of type Unit) so we don't
   * return WeResult<R> and instead rethrow exceptions occurring in work coroutine.
   */
  override suspend fun <R> transaction(
    exclusive: Boolean,
    unitOfWork: String,
    throwIfNoChoice: Boolean,
    work: suspend Transaction.() -> R
  ): R {
    check(!closed) { "Database has been closed" }
    return when (
      val result = withContext(dispatcher) {
        try {
          assertNotUiThread() // client can set dispatcher, need to check
          Success(beginTransaction(exclusive, unitOfWork, throwIfNoChoice).use { it.work() })
        } catch (e: Exception) {
          e.asUnsuccessful { "Exception during transaction" }
        }
      }
    ) {
      is Success -> result.value
      is Unsuccessful -> throw result.exception
    }
  }

  /**
   * Note: The client may not use the transaction return value (could be of type Unit) so we don't
   * return WeResult<R> and instead rethrow exceptions occurring in work coroutine.
   */
  override suspend fun <R> query(
    exclusive: Boolean,
    unitOfWork: String,
    work: suspend Queryable.() -> R
  ): R {
    check(!closed) { "Database has been closed" }
    return when (
      val result = withContext(dispatcher) {
        try {
          assertNotUiThread() // client can set dispatcher, need to check
          beginTransaction(exclusive, unitOfWork, true).use { txn ->
            Success(txn.work().also { txn.setSuccessful() })
          }
        } catch (e: Exception) {
          e.asUnsuccessful { "Exception during query" }
        }
      }
    ) {
      is Success -> result.value
      is Unsuccessful -> throw result.exception
    }
  }

  /**
   * Provides the interface to an ongoing transaction. Client is not concerned with commit/rollback
   * which is handled at another level
   */
  override fun <R> ongoingTransaction(work: TransactionInProgress.() -> R): R {
    try {
      assertNotUiThread()
      check(!closed) { "Database has been closed" }
      return TransactionInProgress(this).work()
    } catch (e: Exception) {
      throw WeLiteException("Exception during ongoingTransaction", e)
    }
  }

  /**
   * @return True if the current thread is in a transaction
   */
  override val inTransaction: Boolean
    get() = db.inTransaction()

  private fun beginTransaction(
    exclusive: Boolean,
    unitOfWork: String,
    throwIfNoChoice: Boolean
  ): Transaction {
    return Transaction(this, exclusive, unitOfWork, throwIfNoChoice)
  }

  override val db: SQLiteDatabase
    get() = openHelper.writableDatabase

  private fun assertNotUiThread() {
    if (!allowWorkOnUiThread && isUiThread)
      throw IllegalStateException("Cannot access the Database on the UI thread.")
  }
}

/**
 * OpenHelper startup
 * onConfigure(db)  - after DB is opened
 * if (newVersion)
 *     if (version > 0 && version < minimumSupportedVersion)
 *         onBeforeDelete(db)
 *     else
 *         beginTxn
 *             if (version = 0)
 *                 onCreate(db)
 *             else
 *                onUpgrade(db, version, newVersion) or onDowngrade(db, version, newVersion)
 *         endTxn
 *     endif
 * endif
 * onOpen(db)
 */
private class OpenHelper private constructor(
  context: Context,
  private val database: Database,
  name: String?,
  version: Int,
  private val tables: List<Table>,
  private val migrations: List<Migration>,
  private val requireMigration: Boolean
) : SQLiteOpenHelper(context, name, null, version), DatabaseLifecycle {

  private var preOpen: ((OpenParams) -> Unit) = {}
  private var onConfigure: ((DatabaseConfiguration) -> Unit) = {}
  private var onCreate: ((Database) -> Unit) = {}
  private var onOpen: ((Database) -> Unit) = {}

  var allowWorkOnUiThread: Boolean
    get() = database.allowWorkOnUiThread
    set(value) {
      database.allowWorkOnUiThread = value
    }

  val tablesInCreateOrder: List<Table>
    get() {
      val tableDependencies = TableDependencies(tables)
      if (tableDependencies.tablesAreCyclic()) LOG.w { it("Tables dependencies are cyclic ") }
      return tableDependencies.sortedTableList
    }

  override fun preOpen(block: (OpenParams) -> Unit) {
    preOpen = block
  }

  override fun onConfigure(block: (DatabaseConfiguration) -> Unit) {
    onConfigure = block
  }

  override fun onCreate(block: (Database) -> Unit) {
    onCreate = block
  }

  override fun onOpen(block: (Database) -> Unit) {
    onOpen = block
  }

  override fun onConfigure(db: SQLiteDatabase) {
    onConfigure.invoke(ConfigurationImpl(db))
  }

  private fun doPreOpen() {
    preOpen(OpenParamsImpl(this))
  }

  override fun onCreate(db: SQLiteDatabase) {
    val executor = object : SqlExecutor {
      override fun exec(sql: String, vararg bindArgs: Any) {
        db.execSQL(sql, bindArgs)
      }

      override fun exec(sql: List<String>) {
        sql.forEach { exec(it) }
      }
    }
    val orderedTables = tablesInCreateOrder
    orderedTables.forEach { table ->
      table.create(executor)
    }
    orderedTables.forEach { table ->
      table.postCreate(executor)
    }
    onCreate(database)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val migrationPath = migrations.findMigrationPath(oldVersion, newVersion)
    when {
      migrationPath != null -> {
        migrationPath.forEach { migration ->
          migration.execute(database)
        }
      }
      requireMigration -> throw IllegalStateException(
        "A migration from " + oldVersion + " to " + newVersion +
          " was not found. Create one, or more, Migrations or do not require migration which " +
          "results in the database being deleted and recreated."
      )
      else -> {
        LOG.w { it("No migration path found and migration not required, drop all and recreate") }
        dropAll()
        onCreate(db)
      }
    }
  }

  private fun dropAll() {
    database.ongoingTransaction {
      setSchemaWritable(true)
      SQLiteMaster.deleteWhere { SQLiteMaster.type inList MasterType.knownTypes.map { it.value } }
        .delete()
      setSchemaWritable(false)
      vacuum()
      tegridyCheck()
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    onOpen(database)
  }

  private fun setSchemaWritable(writable: Boolean) {
    if (writable) execPragma("writable_schema = ${writable.toStatementString()}")
  }

  /**
   * "PRAGMA " is prepended to [statement] and then it's executed as SQL. For example:
   * ```
   * execPragma("synchronous=NORMAL")
   * ```
   * results in [SQLiteDatabase.execSQL] ("PRAGMA synchronous=NORMAL;")
   * @throws SQLException if the SQL string is invalid
   */
  @Throws(SQLException::class)
  private fun execPragma(statement: String) {
    writableDatabase.execSQL("PRAGMA $statement;")
  }

  companion object {
    private val LOG by lazyLogger()

    operator fun invoke(
      context: Context,
      database: Database,
      name: String?,
      version: Int,
      tables: List<Table>,
      migrations: List<Migration>,
      requireMigration: Boolean,
      configure: (DatabaseLifecycle) -> Unit
    ): OpenHelper {
      return OpenHelper(
        context,
        database,
        name,
        version,
        tables,
        migrations,
        requireMigration
      ).apply {
        configure(this)
        doPreOpen()
      }
    }
  }
}

private class OpenParamsImpl(private val openHelper: OpenHelper) : OpenParams {
  override var allowWorkOnUiThread: Boolean
    get() = openHelper.allowWorkOnUiThread
    set(value) {
      openHelper.allowWorkOnUiThread = value
    }

  /**
   * Enables or disables the use of write-ahead logging for the database.
   *
   * Write-ahead logging cannot be used with read-only databases so the value of
   * this flag is ignored if the database is opened read-only.
   *
   * @param enable True if write-ahead logging should be enabled, false if it
   * should be disabled.
   */
  override fun enableWriteAheadLogging(enable: Boolean) {
    openHelper.setWriteAheadLoggingEnabled(enable)
  }

  /**
   * Configures <a href="https://sqlite.org/malloc.html#lookaside">lookaside memory allocator</a>
   *
   * <p> lookaside memory configuration can only be changed when no connection is using it
   *
   * <p>SQLite default settings will be used, if this method isn't called.
   * Use {@code setLookasideConfig(0,0)} to disable lookaside
   *
   * <p><strong>Note:</strong> Provided slotSize/slotCount configuration is just a recommendation.
   * The system may choose different values depending on a device, e.g. lookaside allocations
   * can be disabled on low-RAM devices
   *
   * @param slotSize The size in bytes of each lookaside slot.
   * @param slotCount The total number of lookaside memory slots per database connection.
   */
  override fun setLookasideConfig(slotSize: Int, slotCount: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      openHelper.setLookasideConfig(slotSize, slotCount)
    }
  }
}

private class ConfigurationImpl(private val db: SQLiteDatabase) : DatabaseConfiguration {
  override fun enableForeignKeyConstraints(enable: Boolean) =
    db.setForeignKeyConstraintsEnabled(enable)

  override fun setLocale(locale: Locale) = db.setLocale(locale)

  override var maximumSize: Long
    get() = db.maximumSize
    set(value) {
      db.maximumSize = value
    }

  override fun execPragma(statement: String) {
    db.execSQL("PRAGMA $statement")
  }
}

class NeitherSuccessNorRollbackException(unitOfWork: String) :
  SQLiteException("Txn '$unitOfWork' was not set as successful nor rolled back")
