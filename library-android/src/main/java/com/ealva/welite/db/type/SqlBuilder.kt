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

package com.ealva.welite.db.type

import java.util.Queue
import java.util.concurrent.LinkedBlockingQueue

interface AppendsToSqlBuilder {
  fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder
}

interface SqlBuilder : Appendable {
  val types: List<PersistentType<*>>
  fun <T> Iterable<T>.appendEach(
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    append: SqlBuilder.(T) -> Unit
  )

  fun append(value: String): SqlBuilder
  fun append(value: Long): SqlBuilder
  fun append(value: AppendsToSqlBuilder): SqlBuilder
  fun append(identity: Identity): SqlBuilder
  fun <T> registerBindable(sqlType: PersistentType<T>)
  fun <T> registerArgument(sqlType: PersistentType<T>, argument: T)
  fun <T> registerArguments(sqlType: PersistentType<T>, arguments: Iterable<T>)

  /** for test */
  val length: Int

  /** for test */
  val capacity: Int

  companion object {
    const val minimumBuilderCapacity = MIN_BUILDER_CAPACITY
    fun getCacheStats(): SqlBuilderCacheStats = SqlBuilderCache.getStats()
    fun resetCache() = SqlBuilderCache.resetCache()
  }
}

private class SqlBuilderImpl(private val maxCapacity: Int) : SqlBuilder {
  private val strBuilder = StringBuilder(maxCapacity)
  private val _types = mutableListOf<PersistentType<*>>()
  override val types: List<PersistentType<*>>
    get() = _types.toList()

  /**
   * Indicates the builder is being returned to the pool and should clear types and check capacity
   */
  fun returnedToPool(): Boolean {
    _types.clear()
    return ensureCapacity()
  }

  override var length: Int
    get() = strBuilder.length
    set(value) {
      strBuilder.setLength(value)
    }

  override val capacity: Int
    get() = strBuilder.capacity()

  /**
   * Ensure the capacity is correct size
   */
  private fun ensureCapacity(): Boolean {
    return if (strBuilder.capacity() > maxCapacity) {
      strBuilder.setLength(maxCapacity)
      strBuilder.trimToSize()
      true
    } else false
  }

  override fun <T> Iterable<T>.appendEach(
    separator: CharSequence,
    prefix: CharSequence,
    postfix: CharSequence,
    append: SqlBuilder.(T) -> Unit
  ) {
    strBuilder.append(prefix)
    forEachIndexed { index, element ->
      if (index > 0) {
        strBuilder.append(separator)
      }
      append(element)
    }
    strBuilder.append(postfix)
  }

  override fun append(value: Char): SqlBuilder = apply { strBuilder.append(value) }

  override fun append(csq: CharSequence?): Appendable = apply { strBuilder.append(csq) }

  override fun append(csq: CharSequence?, start: Int, end: Int): Appendable = apply {
    strBuilder.append(csq, start, end)
  }

  override fun append(value: String): SqlBuilder = apply { strBuilder.append(value) }

  override fun append(value: Long): SqlBuilder = apply { strBuilder.append(value) }

  override fun append(value: AppendsToSqlBuilder): SqlBuilder = value.appendTo(this)

  override fun append(identity: Identity) = append(identity.value)

  override fun <T> registerBindable(sqlType: PersistentType<T>) {
    _types.add(sqlType)
    append("?")
  }

  override fun <T> registerArgument(sqlType: PersistentType<T>, argument: T) {
    append(sqlType.valueToString(argument))
  }

  override fun <T> registerArguments(sqlType: PersistentType<T>, arguments: Iterable<T>) {
    arguments.forEach { arg -> registerArgument(sqlType, arg) }
  }

  override fun toString(): String = strBuilder.toString()
}

/** test only */
interface SqlBuilderCacheStats {
  /** Current maximum number of items in the cache */
  val maxEntries: Int

  /** Current maximum builder capacity */
  val maxBuilderCapacity: Int

  /** Number of times a builder used more than [DEFAULT_BUILDER_CAPACITY] */
  val exceededCapacity: Int

  /** The number of SqlBuilders requested */
  val gets: Int

  /**
   * The number of SqlBuilders returned to the pool. [gets] - [puts] = number of SqlBuilders
   * requested that were not returned to the cache. Happens when requesting more than [maxEntries]
   */
  val puts: Int
}

private const val DEFAULT_MAX_CACHE_ENTRIES = 4
private const val MIN_BUILDER_CAPACITY = 1024
private const val DEFAULT_BUILDER_CAPACITY = 2048
private var queueCapacity = DEFAULT_MAX_CACHE_ENTRIES
@Volatile private var queue: Queue<SqlBuilderImpl> = LinkedBlockingQueue(queueCapacity)
private var exceededCapacity = 0
private var gets = 0
private var puts = 0

private object SqlBuilderCache {
  var builderCapacity = DEFAULT_BUILDER_CAPACITY

  var maxEntries: Int
    get() = queueCapacity
    set(value) {
      if (queueCapacity != value) {
        queueCapacity = value
        queue = LinkedBlockingQueue(value)
      }
    }

  fun getStats(): SqlBuilderCacheStats {
    data class CacheStatsImpl(
      override val maxEntries: Int,
      override val maxBuilderCapacity: Int,
      override val exceededCapacity: Int,
      override val gets: Int,
      override val puts: Int
    ) : SqlBuilderCacheStats
    return CacheStatsImpl(
      maxEntries,
      builderCapacity,
      exceededCapacity,
      gets,
      puts
    )
  }

  fun resetCache() {
    queue.clear()
    exceededCapacity = 0
    gets = 0
    puts = 0
  }

  private fun newBuilder() = SqlBuilderImpl(builderCapacity)

  fun get(): SqlBuilderImpl {
    return (queue.poll()?.apply { length = 0 } ?: newBuilder()).also { gets++ }
  }

  fun put(builder: SqlBuilderImpl) {
    if (queue.size < maxEntries) {
      puts++
      if (builder.returnedToPool()) {
        exceededCapacity++
      }
      queue.offer(builder)
    }
  }
}

/**
 * Set the maximum number of entries allowed in the cache. The current cache will be abandoned if
 * maxEntries is modified
 */
fun setSqlBuilderCacheCapacity(maxEntries: Int) {
  SqlBuilderCache.maxEntries = maxEntries
}

/**
 * Set the largest capacity of a builder that has been returned to the cache. If capacity
 * exceeds this number, the capacity is adjusted down. If a new builder is created it's initial
 * capacity will be this number.
 *
 * Capacity defaults to [DEFAULT_BUILDER_CAPACITY] which is currently 2048. Capacity cannot be set
 * smaller than [MIN_BUILDER_CAPACITY] which is currently 1024
 */
fun setSqlBuilderCapacity(defaultCapacity: Int) {
  SqlBuilderCache.builderCapacity = maxOf(MIN_BUILDER_CAPACITY, defaultCapacity)
}

/**
 * The base data for all Statements and Query, contains a list of types representing each
 * parameter which much be bound (which may be empty) and the statement/query sql
 */
data class StatementSeed(
  /**
   * The list of the types of arguments which need to be bound for each statement execution. This is
   * each place a "?" appears in [sql]. The [PersistentType] is responsible for accepting
   * an argument from the client, converting if necessary, and binding it into statement args.
   */
  val types: List<PersistentType<*>>,
  /**
   * The full sql of the statement
   */
  val sql: String,
)

fun SqlBuilder.append(seed: StatementSeed): SqlBuilder = apply {
  append(seed.sql)
}

fun buildSql(builderAction: SqlBuilder.() -> Unit): StatementSeed {
  val builder = SqlBuilderCache.get()
  val sql = builder.apply(builderAction).toString()
  val types = builder.types
  SqlBuilderCache.put(builder)
  return StatementSeed(types, sql)
}

fun buildStr(builderAction: SqlBuilder.() -> Unit): String {
  val builder = SqlBuilderCache.get()
  return builder.apply(builderAction).toString().also { SqlBuilderCache.put(builder) }
}
