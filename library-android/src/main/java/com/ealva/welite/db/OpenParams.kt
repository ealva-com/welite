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

@file:Suppress("unused")

package com.ealva.welite.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

data class OpenParams(
  /**
   * Used to dispatch transaction and query functions. Defaults to Dispatchers.IO which should
   * generally suffice.
   */
  val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  /**
   * Defaults to true, ie. enforce foreign key constraints
   *
   * When foreign key constraints are disabled, the database does not check whether changes to the
   * database will violate foreign key constraints. Likewise, when foreign key constraints are
   * disabled, the database will not execute cascade delete or update triggers. As a result, it is
   * possible for the database state to become inconsistent.
   *
   * See also [SQLite Foreign Key Constraints](http://sqlite.org/foreignkeys.html)
   * for more details about foreign key constraint support.
   */
  val enableForeignKeyConstraints: Boolean = true,
  /**
   * Write-ahead logging cannot be used with read-only databases so the value of this value is
   * ignored if the database is opened read-only.
   */
  val enableWriteAheadLogging: Boolean = true,
  /** Defaults to [SynchronousMode.NORMAL], which works well with [enableWriteAheadLogging]=true */
  val synchronousMode: SynchronousMode = SynchronousMode.NORMAL,
  /**
   * Ignored if [enableWriteAheadLogging] is true (which is WAL mode).
   * Defaults to [JournalMode.DELETE].
   * @see [JournalMode]
   */
  val journalMode: JournalMode = JournalMode.DELETE,
  /**
   * Defaults to using system default values. 0,0 disables lookaside. Only used if
   * Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1. This is only a recommendation, the system
   * may choose different values.
   * @see [LookasideSlot]
   */
  val lookasideSlot: LookasideSlot? = null,
  /**
   * If false an IllegalStateException is thrown if work is dispatched on the UI thread. Default is
   * false (don't do I/O on the UI thread). This is here primarily for testing coroutines. WeLite
   * tests use a rule that confines coroutines to a single thread to simplify testing.
   */
  var allowWorkOnUiThread: Boolean = false
)

/**
 *  * OFF - With synchronous OFF, SQLite continues without syncing as soon as it has handed data off
 *  to the operating system. If the application running SQLite crashes, the data will be safe, but
 *  the database might become corrupted if the operating system crashes or the computer loses power
 *  before that data has been written to the disk surface. On the other hand, commits can be orders
 *  of magnitude faster with synchronous OFF.
 *  * NORMAL - When synchronous is NORMAL, the SQLite
 *  database engine will still sync at the most critical moments, but less often than in FULL mode.
 *  There is a very small (though non-zero) chance that a power failure at just the wrong time could
 *  corrupt the database in journal_mode=DELETE on an older filesystem. WAL mode is safe from
 *  corruption with synchronous=NORMAL, and probably DELETE mode is safe too on modern filesystems.
 *  * WAL mode is always consistent with synchronous=NORMAL, but WAL mode does lose durability. A
 *  transaction committed in WAL mode with synchronous=NORMAL might roll back following a power loss
 *  or system crash. Transactions are durable across application crashes regardless of the
 *  synchronous setting or journal mode. The synchronous=NORMAL setting is a good choice for most
 *  applications running in WAL mode.
 *  * FULL - When synchronous is FULL, the SQLite database engine
 *  will use the xSync method of the VFS to ensure that all content is safely written to the disk
 *  surface prior to continuing. This ensures that an operating system crash or power failure will
 *  not corrupt the database. FULL synchronous is very safe, but it is also slower. FULL is the most
 *  commonly used synchronous setting when not in WAL mode.
 *  * EXTRA - EXTRA synchronous is like FULL
 *  with the addition that the directory containing a rollback journal is synced after that journal
 *  is unlinked to commit a transaction in DELETE mode. EXTRA provides additional durability if the
 *  commit is followed closely by a power loss.
 *
 *  [SQLite synchronous flag](https://sqlite.org/pragma.html#pragma_synchronous)
 */
enum class SynchronousMode(val value: String, val numValue: Long) {
  OFF("OFF", SYNC_MODE_OFF),
  NORMAL("NORMAL", SYNC_MODE_NORMAL),
  FULL("FULL", SYNC_MODE_FULL),
  EXTRA("EXTRA", SYNC_MODE_EXTRA),
  /** UNKNOWN is a sentinel value, used as return value only */
  UNKNOWN("UNKNOWN", Long.MAX_VALUE);

  override fun toString(): String = value
}

private const val SYNC_MODE_OFF = 0L
private const val SYNC_MODE_NORMAL = 1L
private const val SYNC_MODE_FULL = 2L
private const val SYNC_MODE_EXTRA = 3L

private val syncModes = SynchronousMode.values()
fun Long.toSynchronousMode(): SynchronousMode =
  syncModes.firstOrNull { mode -> mode.numValue == this } ?: SynchronousMode.UNKNOWN
fun String.toSynchronousMode(): SynchronousMode =
  syncModes.firstOrNull { mode -> mode.value.equals(this, true) } ?: SynchronousMode.UNKNOWN

/**
 *  * DELETE journaling mode is the normal behavior. In the DELETE mode, the rollback journal is
 *  deleted at the conclusion of each transaction. Indeed, the delete operation is the action that
 *  causes the transaction to commit. (See the document titled Atomic Commit In SQLite for
 *  additional detail.)
 *  * TRUNCATE journaling mode commits transactions by truncating the rollback journal to
 *  zero-length instead of deleting it. On many systems, truncating a file is much faster than
 *  deleting the file since the containing directory does not need to be changed.
 *  * PERSIST journaling mode prevents the rollback journal from being deleted at the end of each
 *  transaction. Instead, the header of the journal is overwritten with zeros. This will prevent
 *  other database connections from rolling the journal back. The PERSIST journaling mode is useful
 *  as an optimization on platforms where deleting or truncating a file is much more expensive than
 *  overwriting the first block of a file with zeros.
 *  * MEMORY journaling mode stores the rollback journal in volatile RAM. This saves disk I/O but
 *  at the expense of database safety and integrity. If the application using SQLite crashes
 *  in the middle of a transaction when the MEMORY journaling mode is set, then the database file
 *  will very likely go corrupt.
 *  * WAL journaling mode uses a write-ahead log instead of a rollback journal to implement
 *  transactions. The WAL journaling mode is persistent; after being set it stays in effect across
 *  multiple database connections and after closing and reopening the database.
 *  * OFF journaling mode disables the rollback journal completely. No rollback journal is ever
 *  created and hence there is never a rollback journal to delete. The OFF journaling mode disables
 *  the atomic commit and rollback capabilities of SQLite.
 *
 * [SQLite journal_mode](https://sqlite.org/pragma.html#pragma_journal_mode)
 */
enum class JournalMode(val value: String) {
  DELETE("DELETE"),
  TRUNCATE("TRUNCATE"),
  PERSIST("PERSIST"),
  MEMORY("MEMORY"),
  WAL("WAL"),
  OFF("OFF"),
  /** UNKNOWN is a sentinel value, used as return value only */
  UNKNOWN("UNKNOWN");

  override fun toString(): String = value
}

private val journalModes = JournalMode.values()
fun String.toJournalMode(): JournalMode =
  journalModes.firstOrNull { mode -> mode.value.equals(this, true) } ?: JournalMode.UNKNOWN

/**
 * [size]=0 [count]=0 disable lookaside memory allocation. Android may modify these values in
 * different situations, eg. on low memory devices values will be set to 0,0 disabling lookaside.
 *
 * Only used if Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
 *
 * [SQLite Lookaside Memory Allocator](https://sqlite.org/malloc.html#lookaside)
 */
interface LookasideSlot {
  val size: Int
  val count: Int

  companion object {
    /**
     * Create a LookasideSlot with [size] and [count]
     */
    operator fun invoke(size: Int, count: Int): LookasideSlot {
      data class LookasideSlotImpl(override val size: Int, override val count: Int) : LookasideSlot
      require(size >= 0 && count >= 0) { "Size and count must be >= 0, size:$size count:$count" }
      require((size > 0 && count > 0) || (size == 0 && count == 0))
      return LookasideSlotImpl(size, count)
    }
  }
}
