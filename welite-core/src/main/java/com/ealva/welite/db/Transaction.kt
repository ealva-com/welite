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

import android.database.sqlite.SQLiteDatabase
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.unaryPlus
import com.ealva.welite.db.log.WeLiteLog
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.WeLiteMarker
import java.lang.Exception

/**
 * Transaction is the interface presented to the client to control commit or rollback. This is
 * typically automatically handled via [Database.transaction] ```autoCommit``` parameter, so the
 * client only need use these functions/properties if finer grain control of commit/rollback is
 * necessary.
 */
@WeLiteMarker
public interface Transaction : TransactionInProgress {
  public val isClosed: Boolean
  public val successful: Boolean
  public val rolledBack: Boolean

  /**
   * Marks this txn as successful and will commit on close. No more database work should be done
   * between this call and closing the txn
   * @throws IllegalStateException if the txn is already closed or rolled back
   */
  public fun setSuccessful()

  /**
   * Mark this txn rolled back and txn ends during close. No more database work should be done
   * between this call and closing the txn
   * @throws IllegalStateException if the txn is already closed
   */
  public fun rollback()
}

internal interface CloseableTransaction : Transaction, AutoCloseable {
  companion object {
    /**
     * Start a transaction and return the Transaction object which is [AutoCloseable] and expected
     * to be used within a [use] block to control the Transaction lifetime. Client's use
     * [Database.transaction], [Database.query], and [Database.ongoingTransaction], so this is not
     * expected to be exposed to a client.
     */
    internal operator fun invoke(
      dbConfig: DbConfig,
      exclusiveLock: Boolean,
      unitOfWork: String,
      throwIfNoChoice: Boolean
    ): CloseableTransaction {
      val db = dbConfig.db
      if (exclusiveLock) db.beginTransaction() else db.beginTransactionNonExclusive()
      return TransactionImpl(
        db,
        exclusiveLock,
        unitOfWork,
        throwIfNoChoice,
        TransactionInProgress(dbConfig)
      )
    }
  }
}

private val LOG by lazyLogger(Transaction::class, WeLiteLog.marker)
private const val TXN_NOT_MARKED =
  "Txn '%s' closing without it being marked successful or rolled back."

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
private class TransactionImpl(
  private val db: SQLiteDatabase,
  private val exclusiveLock: Boolean,
  private val unitOfWork: String,
  private val throwIfNoChoice: Boolean,
  private val transactionInProgress: TransactionInProgress
) : TransactionInProgress by transactionInProgress, CloseableTransaction {
  override var successful = false
    private set
  override var rolledBack = false
    private set
  override var isClosed = false
    private set

  override fun setSuccessful() {
    check(!isClosed) { "Txn '$unitOfWork' already closed" }
    check(!rolledBack) { "Txn '$unitOfWork' rolled back, cannot be marked successful" }
    if (!successful) {
      db.setTransactionSuccessful()
      successful = true
    }
  }

  override fun rollback() {
    check(!isClosed) { "Txn '$unitOfWork' already closed" }
    if (!rolledBack) {
      successful = false
      rolledBack = true
    }
  }

  override fun close() {
    if (!isClosed) {
      try {
        isClosed = true
        if (!successful && !rolledBack) {
          val ex = UnmarkedTransactionException(unitOfWork)
          LOG.e(ex) { it(TXN_NOT_MARKED, unitOfWork) }
          if (throwIfNoChoice) throw ex
        }
      } finally {
        db.endTransaction()
        if (successful) notifyOfCommit()
      }
    }
  }

  private val onCommitList: MutableSet<() -> Unit> = mutableSetOf()
  override fun onCommit(block: () -> Unit) {
    onCommitList.add(block)
  }

  private fun notifyOfCommit() {
    onCommitList.forEach { block ->
      try {
        block()
      } catch (e: Exception) {
        LOG.e { +it("onCommit lambda threw unexpected exception") }
      }
    }
  }

  override val isFrameworkTransaction: Boolean = true

  override fun toString(): String {
    return "Txn='" + unitOfWork + "' closed=" + isClosed + " exclusive=" + exclusiveLock +
      " success=" + successful + " rolledBack=" + rolledBack
  }
}
