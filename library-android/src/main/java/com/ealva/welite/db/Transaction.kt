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

import android.database.sqlite.SQLiteDatabase
import com.ealva.ealvalog.e
import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.welite.db.table.DbConfig
import com.ealva.welite.db.table.WeLiteMarker

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
    internal operator fun invoke(
      dbConfig: DbConfig,
      exclusiveLock: Boolean,
      unitOfWork: String,
      throwIfNoChoice: Boolean
    ): Transaction {
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

private val LOG by lazyLogger(Transaction::class)
private const val TXN_NOT_MARKED =
  "<<==Warning==>> Closing a txn without it being marked successful or rolled back. %s"

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
    return "Txn='" + unitOfWork + "' closed=" + isClosed + " exclusive=" + exclusiveLock +
      " success=" + successful + " rolledBack=" + rolledBack
  }
}
