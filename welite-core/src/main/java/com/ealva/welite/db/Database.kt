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

import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.DefaultDatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import androidx.core.database.sqlite.transaction
import com.ealva.ealvalog.e
import com.ealva.ealvalog.i
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.WeLiteResult.Success
import com.ealva.welite.db.WeLiteResult.Unsuccessful
import com.ealva.welite.db.expr.inList
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.table.Creatable
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.MasterType
import com.ealva.welite.db.table.SQLiteSchema
import com.ealva.welite.db.table.SqlExecutor
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.TableDependencies
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.table.knownTypes
import com.ealva.welite.db.table.longForQuery
import com.ealva.welite.db.table.stringForQuery
import com.ealva.welite.db.type.toStatementString
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import java.sql.SQLException
import java.util.Locale

/**
 * Has the [tableName] and all the create SQL statements associated with it and a type.  For both
 * tables and indices, the SQL field is the text of the original CREATE TABLE or CREATE INDEX
 * statement that created the table or index. For automatically created indices (used to implement
 * the PRIMARY KEY or UNIQUE constraints) the sql field is NULL.
 */
public data class SchemaSql(
  /** The table name as it appears in sqlite_master.tbl_name */
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
public interface Database {
  /**
   * Tables this Database instance is aware of, in creation order
   */
  public val tables: List<Table>

  /**
   * If false an IllegalStateException is thrown if work is dispatched on the UI thread. Default is
   * false (don't do I/O on the UI thread). This is here primarily for testing coroutines. WeLite
   * tests use a rule that confines coroutines to a single thread to simplify testing.
   *
   * Initially set via [OpenParams] when a Database instance is created
   */
  public var allowWorkOnUiThread: Boolean

  /**
   * Begin a [Transaction] named [unitOfWork] and call [work] with the transaction as the receiver.
   * The [R] returned from the [work] function is returned from this function. WeLite attempts to
   * require an explicit transaction be started for all DB access (otherwise SQLite will start an
   * implicit txn). By default [exclusive] is false and the transaction runs in IMMEDIATE mode. All
   * db work should be completed within the [work] block and the txn must be marked as either
   * successful, [Transaction.setSuccessful], or rolled back, [Transaction.rollback].
   *
   * If [autoCommit] is true, which is the default, and [Transaction.rollback] is not invoked,
   * [Transaction.setSuccessful] is automatically called for the client. If [autoCommit] is false
   * the client must invoke setSuccessful() or rollback().
   *
   * When the txn closes, if neither success nor rollback has been called an error will be logged.
   * If [throwIfNoChoice] is true, which is the default, an [UnmarkedTransactionException] will be
   * thrown. If [throwIfNoChoice] is false, execution continues but the txn will not be committed.
   *
   * If [exclusive] is false, which is the default, a reserved lock is acquired. While holding a
   * reserved lock, this session is allowed to read or write but other sessions are only allowed to
   * read.
   *
   * If [exclusive] is true, an exclusive lock is acquired. While holding an exclusive lock, this
   * session is allowed to read or write but no other sessions are allowed to access the database.
   *
   * The work function will be executed on the underlying CoroutineDispatcher which is typically
   * either Dispatchers.IO or a custom dispatcher just for DB work.
   *
   * Query functions are also available within a txn as the [work] receiver [Transaction] is also a
   * [Queryable]
   *
   * [SQLite docs](https://www.sqlite.org/lang_transaction.html): No reads or writes occur except
   * within a transaction. Any command that accesses the database (basically, any SQL command,
   * except a few PRAGMA statements) will automatically start a transaction if one is not already in
   * effect. Automatically started transactions are committed when the last SQL statement finishes.
   *
   * @see query
   * @throws IllegalStateException if the database has been closed
   * @throws WeLiteException if an exception is thrown in the coroutine code, but not in the [work]
   * function. For example, if [work] is dispatched on the main UI thread an
   * [IllegalStateException] is thrown which is in turn wrapped in a [WeLiteException].
   * @throws UnmarkedTransactionException if [autoCommit] is false and a txn is not marked as
   * successful or rolled back, this exception will be thrown
   * @throws WeLiteUncaughtException if an exception is thrown from [work]. The
   * [WeLiteException.cause] is set to the underlying exception.
   */
  public suspend fun <R> transaction(
    exclusive: Boolean = false,
    unitOfWork: String = UNNAMED_TXN,
    autoCommit: Boolean = true,
    throwIfNoChoice: Boolean = true,
    work: Transaction.() -> R
  ): R

  /**
   * Called to do queries and no other CRUD operation. This is the same as starting a [transaction]
   * and only doing queries. [R] is returned from the [work] function and is returned from
   * this function.
   *
   * The primary difference between this function and [transaction] is no table updating is to
   * occur and the client does not have [Transaction] functions available since [Queryable] is the
   * [work] function receiver. [query] is basically [transaction] with autoCommit set to true, but
   * indicates only queries will be performed in the [work] function.
   *
   * Possible exceptions are the same as the [transaction] function.
   *
   * @see [transaction]
   */
  public suspend fun <R> query(
    exclusive: Boolean = false,
    unitOfWork: String = UNNAMED_TXN,
    work: Queryable.() -> R
  ): R

  /**
   * True if this thread is currently within a transaction, ie. a transaction is in progress.
   */
  public val inTransaction: Boolean

  /**
   * Provides the interface to an ongoing transaction. Client is not concerned with commit/rollback
   * which is handled at some other level.
   *
   * The [work] function is executed on the current thread, unlike [transaction] and [query] which
   * use coroutines to execute off the main thread.
   *
   * An optional [coroutineScope] can be passed so that the [TransactionInProgress] receiver for
   * work can test [TransactionInProgress.isActive] or call [TransactionInProgress.ensureActive] for
   * cooperative coroutine cancellation.
   *
   * @throws IllegalStateException if the caller illegally invokes this function from the UI thread,
   * the database has been closed, or the current thread is not in a transaction
   * @throws WeLiteUncaughtException if an exception is thrown from [work]. The
   * [WeLiteException.cause] is set to the underlying exception.
   * @see inTransaction
   */
  public fun <R> ongoingTransaction(
    coroutineScope: CoroutineScope? = null,
    work: TransactionInProgress.() -> R
  ): R

  /**
   * Close this Database connection. Calling other public functions after close will result in an
   * IllegalStateException. This method isn't usually needed for Android apps. Typically
   * Android apps will leave the DB connected for the life of the application.
   *
   * This function is idempotent.
   *
   * If the client needs to do something like overwrite the DB file:
   * * Call close on this Database instance and release all references to it
   * * Do file copying, etc.
   * * Create a new [Database] instance which will open the new DB file
   */
  public fun close()

  /** The path to the database file */
  public val path: String

  /** True if the database exists in memory. If true [path] will be ":memory:" */
  public val isInMemoryDatabase: Boolean

  public companion object {
    public const val UNNAMED_TXN: String = "Unnamed Txn"

    /**
     * Attempts to release memory that SQLite holds but does not require to operate properly.
     * Typically this memory will come from the page cache. This function may be called when
     * when Android reports low memory to the app via [android.app.Application.onTrimMemory] or
     * other [android.content.ComponentCallbacks2] instance. Returns the number of bytes actually
     * released.
     */
    public fun releaseMemory(): Int = SQLiteDatabase.releaseMemory()

    /**
     * Construct a Database instance with the given [fileName] and [context] used to locate the
     * database file path. The database instance maintains the list of [tables] and will construct
     * them if Android calls onCreate. [version] is used to determine if the schema needs to be
     * migrated to a new version using [migrations]. [otherCreatables] are Views, Triggers, or other
     * [Creatable] types to be created after all [tables] are created (dependency order is important
     * and is not managed by this class). [openParams] provide various configuration settings and
     * has reasonable defaults. See [OpenParams] for details. An optional [configure] function will
     * be called to provide for configuring the database connection at various points during
     * creation.
     */
    public operator fun invoke(
      context: Context,
      fileName: String,
      tables: Set<Table>,
      version: Int,
      otherCreatables: List<Creatable> = emptyList(),
      migrations: List<Migration> = emptyList(),
      requireMigration: Boolean = true,
      openParams: OpenParams = OpenParams(),
      configure: DatabaseLifecycle.() -> Unit = {}
    ): Database {
      return doMake(
        context,
        fileName,
        version,
        tables,
        otherCreatables,
        migrations,
        requireMigration,
        openParams,
        configure
      )
    }

    /**
     * Construct an in-memory Database instance with the given [context] used to locate the
     * database file path. The database instance maintains the list of [tables] and will construct
     * them if Android calls onCreate. [version] is used to determine if the schema needs to be
     * migrated to a new version using [migrations]. [otherCreatables] are Views, Triggers, or other
     * [Creatable] types to be created after all [tables] are created (dependency order is important
     * and is not managed by this class). [openParams] provide various configuration
     * settings and has reasonable defaults. See [OpenParams] for details. An optional [configure]
     * function will be called to provide for configuring the database connection at various points
     * during creation.
     */
    public operator fun invoke(
      context: Context,
      tables: Set<Table>,
      version: Int,
      migrations: List<Migration>,
      otherCreatables: List<Creatable> = emptyList(),
      requireMigration: Boolean = true,
      openParams: OpenParams = OpenParams(),
      configure: DatabaseLifecycle.() -> Unit = {}
    ): Database {
      return doMake(
        context,
        null,
        version,
        tables,
        otherCreatables,
        migrations,
        requireMigration,
        openParams,
        configure
      )
    }

    private fun doMake(
      context: Context,
      fileName: String?,
      version: Int,
      tables: Set<Table>,
      otherCreatables: List<Creatable>,
      migrations: List<Migration>,
      requireMigration: Boolean,
      openParams: OpenParams,
      configure: (DatabaseLifecycle) -> Unit
    ): Database {
      return WeLiteDatabase(
        context.applicationContext,
        fileName,
        version,
        tables,
        otherCreatables,
        migrations,
        requireMigration,
        openParams,
        configure
      )
    }
  }
}

private val LOG by lazyLogger(Database::class, WeLiteLog.marker)

private fun Exception.asUncaught(errorMessage: () -> String): Unsuccessful {
  return Unsuccessful.makeUncaught(errorMessage(), this)
}

private class WeLiteDatabase(
  context: Context,
  fileName: String?,
  version: Int,
  tables: Set<Table>,
  otherCreatables: List<Creatable>,
  migrations: List<Migration>,
  requireMigration: Boolean,
  openParams: OpenParams,
  configure: DatabaseLifecycle.() -> Unit
) : Database, DbConfig {
  private var closed = false
  private val openHelper: OpenHelper = OpenHelper(
    context = context,
    database = this,
    name = fileName,
    version = version,
    tables = tables,
    otherCreatables = otherCreatables,
    migrations = migrations,
    requireMigration = requireMigration,
    openParams = openParams,
    configure = configure
  )
  override val tables: List<Table>
    get() = openHelper.tablesInCreateOrder.toList()
  override var allowWorkOnUiThread: Boolean = openParams.allowWorkOnUiThread
  override val dispatcher: CoroutineDispatcher = openParams.dispatcher

  /**
   * We want to use the db instance passed to the open helper onConfigure method because it's usable
   * in onCreate, onUpgrade, onOpen, etc. If we call openHelper.writableDatabase in onCreate or
   * onUpgrade we'll get an exception as SQLiteOpenHelper is still initializing. So the first
   * call to openHelper.writableDatabase begins the process and our open helper calls back
   * and sets internalDb which is safe to use from that point forward
   */
  var internalDb: SQLiteDatabase? = null
  override val db: SQLiteDatabase
    get() = internalDb ?: openHelper.writableDatabase

  override fun close() {
    if (!closed) {
      LOG.i { it("Closing Database") }
      openHelper.close()
      internalDb = null
      closed = true
    }
  }

  override val path: String
    get() = db.path

  override val isInMemoryDatabase: Boolean
    get() = ":memory:" == path

  /**
   * Note: The client may not use the transaction return value (could be of type Unit) so we don't
   * return WeResult<R> and instead rethrow exceptions wrapped in WeResult
   */
  override suspend fun <R> transaction(
    exclusive: Boolean,
    unitOfWork: String,
    autoCommit: Boolean,
    throwIfNoChoice: Boolean,
    work: Transaction.() -> R
  ): R = workInTxn(exclusive, unitOfWork, autoCommit, throwIfNoChoice, work)

  /**
   * This function sets autoCommit to true which results in the txn being marked as successful,
   * which is not technically necessary as only reads (select) are performed. However, this keeps
   * the code simpler and consistent. In SQLite END TRANSACTION is an alias for COMMIT.
   */
  override suspend fun <R> query(
    exclusive: Boolean,
    unitOfWork: String,
    work: Queryable.() -> R
  ): R = workInTxn(exclusive, unitOfWork, autoCommit = true, throwIfNoChoice = false, work = work)

  /**
   * Dispatch work on a coroutine inside of a transaction and handle marking the txn as required.
   * Converts the returned [WeLiteResult] to the value returned from [work] if successful, else
   * converts to an exception which is then thrown. The [WeLiteResult] is not returned as many times
   * the return value is not checked, which would allow exceptions to be ignored.
   */
  private suspend fun <R> workInTxn(
    exclusive: Boolean,
    unitOfWork: String,
    autoCommit: Boolean,
    throwIfNoChoice: Boolean,
    work: Transaction.() -> R
  ): R {
    check(!closed) { "Database has been closed" }
    withContext(dispatcher) {
      try {
        assertNotUiThread() // client can set dispatcher, need to check
        beginTransaction(exclusive, unitOfWork, throwIfNoChoice, this).use { txn ->
          txn.doWork(unitOfWork, work).also { result -> txn.doAutoOrRollback(result, autoCommit) }
        }
      } catch (e: Exception) {
        Unsuccessful(e.asWeLiteException("Exception on coroutine '$unitOfWork'"))
      }
    }.let { result ->
      when (result) {
        is Success -> return result.value
        is Unsuccessful -> throw result.exception
      }
    }
  }

  private fun <R> Transaction.doAutoOrRollback(result: WeLiteResult<R>, autoCommit: Boolean) {
    if (!isClosed) {
      if (result is Success) {
        if (autoCommit && !rolledBack) setSuccessful()
      } else {
        rollback()
      }
    }
  }

  private fun <R> Transaction.doWork(
    unitOfWork: String,
    work: Transaction.() -> R
  ): WeLiteResult<R> = try {
    Success(work())
  } catch (e: Exception) {
    e.asUncaught { "Exception during transaction '$unitOfWork'" }
  }

  override fun <R> ongoingTransaction(
    coroutineScope: CoroutineScope?,
    work: TransactionInProgress.() -> R
  ): R {
    assertNotUiThread()
    check(!closed) { "Database has been closed" }
    val txn = TransactionInProgress(this, coroutineScope)
    try {
      return txn.work()
    } catch (e: Exception) {
      throw WeLiteUncaughtException("Exception during ongoingTransaction", e)
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
    throwIfNoChoice: Boolean,
    coroutineScope: CoroutineScope
  ): CloseableTransaction = CloseableTransaction(
    this,
    exclusive,
    unitOfWork,
    throwIfNoChoice,
    coroutineScope
  )

  private fun assertNotUiThread() =
    check(allowWorkOnUiThread || isNotUiThread) { "Cannot access the Database on the UI thread." }
}

private fun Exception.asWeLiteException(msg: String): WeLiteException {
  return when (this) {
    is WeLiteException -> this
    else -> WeLiteException(msg, this)
  }
}

private class ErrorHandler(private val database: Database) : DatabaseErrorHandler {
  var useDefault = true
  var onError: ((Database) -> Unit) = {}

  private val defaultDatabaseErrorHandler = DefaultDatabaseErrorHandler()
  override fun onCorruption(dbObj: SQLiteDatabase?) {
    if (useDefault) {
      defaultDatabaseErrorHandler.onCorruption(dbObj)
    }
    onError(database)
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
  private val database: WeLiteDatabase,
  name: String?,
  version: Int,
  private val tables: Set<Table>,
  private val otherCreatables: List<Creatable>,
  private val migrations: List<Migration>,
  private val requireMigration: Boolean,
  openParams: OpenParams,
  private val errorHandler: ErrorHandler
) : SQLiteOpenHelper(context, name, null, version, errorHandler), DatabaseLifecycle {

  private val openParams = openParams.copy()

  private var onConfigure: ((DatabaseConfiguration) -> Unit) = {}
  private var onCreate: (TransactionInProgress.(Database) -> Unit) = {}
  private var onOpen: (TransactionInProgress.(Database) -> Unit) = {}

  val tablesInCreateOrder: Set<Table> by lazy {
    TableDependencies(tables).also { dependencies ->
      if (dependencies.tablesAreCyclic()) LOG.w { it("Table dependencies are cyclic") }
    }.sortedTableList
  }

  override fun onConfigure(block: (DatabaseConfiguration) -> Unit) {
    onConfigure = block
  }

  override fun onCreate(block: TransactionInProgress.(Database) -> Unit) {
    onCreate = block
  }

  override fun onOpen(block: TransactionInProgress.(Database) -> Unit) {
    onOpen = block
  }

  override fun onCorruption(useDefaultHandler: Boolean, block: (Database) -> Unit) {
    errorHandler.useDefault = useDefaultHandler
    errorHandler.onError = block
  }

  override fun onConfigure(db: SQLiteDatabase) {
    LOG.i { it("onConfigure") }
    val config = ConfigurationImpl(db)
    db.setForeignKeyConstraintsEnabled(openParams.enableForeignKeyConstraints)
    if (openParams.enableWriteAheadLogging) {
      db.enableWriteAheadLogging()
    } else {
      db.disableWriteAheadLogging()
      config.journalMode = openParams.journalMode
    }
    config.synchronousMode = openParams.synchronousMode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      openParams.lookasideSlot?.let { lookaside ->
        setLookasideConfig(lookaside.size, lookaside.count)
      }
    }
    database.internalDb = db
    onConfigure(config)
    config.closed = true
  }

  fun SQLiteDatabase.toSqlExecutor(): SqlExecutor {
    return object : SqlExecutor {
      override fun exec(sql: String, vararg bindArgs: Any) {
        execSQL(sql, bindArgs)
      }

      override fun exec(sql: List<String>) {
        sql.forEach { exec(it) }
      }
    }
  }

  override fun onCreate(db: SQLiteDatabase) {
    LOG.i { it("onCreate") }
    val executor = db.toSqlExecutor()
    val orderedTables = tablesInCreateOrder
    orderedTables.forEach { table ->
      table.create(executor)
    }
    orderedTables.forEach { table ->
      table.postCreate(executor)
    }
    otherCreatables.forEach { creatable ->
      try {
        creatable.create(executor)
      } catch (e: Exception) {
        LOG.e(e) { it("Error creating %s", creatable.identity) }
      }
    }
    database.ongoingTransaction {
      onCreate(database)
    }
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    val migrationPath = migrations.findMigrationPath(oldVersion, newVersion)
    when {
      migrationPath != null -> {
        database.ongoingTransaction {
          migrationPath.forEach { migration ->
            migration.doExec(this, database)
          }
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
      SQLiteSchema.delete { type inList MasterType.knownTypes.map { it.value } }
      setSchemaWritable(false)
      vacuum()
      tegridyCheck()
    }
  }

  override fun onOpen(db: SQLiteDatabase) {
    db.transaction {
      database.ongoingTransaction {
        onOpen(database)
      }
    }
  }

  private fun setSchemaWritable(writable: Boolean) {
    if (writable)
      execPragma("writable_schema = ${writable.toStatementString()}")
  }

  /**
   * "PRAGMA " is prepended to [statement] and then it's executed as SQL
   * @throws SQLException if the SQL string is invalid
   */
  @Throws(SQLException::class)
  private fun execPragma(statement: String) {
    writableDatabase.execSQL("PRAGMA $statement")
  }

  companion object {
    private val LOG by lazyLogger(WeLiteLog.marker)

    /**
     * Make an OpenHelper which is a subclass of [SQLiteOpenHelper]
     */
    internal operator fun invoke(
      context: Context,
      database: WeLiteDatabase,
      name: String?,
      version: Int,
      tables: Set<Table>,
      otherCreatables: List<Creatable>,
      migrations: List<Migration>,
      requireMigration: Boolean,
      openParams: OpenParams,
      configure: (DatabaseLifecycle) -> Unit
    ): OpenHelper {
      return OpenHelper(
        context,
        database,
        name,
        version,
        tables,
        otherCreatables,
        migrations,
        requireMigration,
        openParams,
        ErrorHandler(database)
      ).apply {
        configure(this)
      }
    }
  }
}

private fun ConfigurationImpl.checkNotClosed() {
  if (closed) throw IllegalStateException("Configuration may only be performed during onConfigure")
}

private class ConfigurationImpl(private val db: SQLiteDatabase) : DatabaseConfiguration {
  var closed = false

  override fun setLocale(locale: Locale) {
    checkNotClosed()
    db.setLocale(locale)
  }

  override var maximumSize: Long
    get() = db.maximumSize
    set(value) {
      checkNotClosed()
      db.maximumSize = value
    }

  override fun execPragma(statement: String) {
    checkNotClosed()
    db.execSQL("PRAGMA $statement")
  }

  override fun queryPragmaString(statement: String): String {
    checkNotClosed()
    return db.stringForQuery("PRAGMA $statement")
  }

  override fun queryPragmaLong(statement: String): Long {
    checkNotClosed()
    return db.longForQuery("PRAGMA $statement")
  }
}

public class UnmarkedTransactionException(unitOfWork: String) :
  WeLiteException("Txn '$unitOfWork' was not set as successful nor rolled back")
