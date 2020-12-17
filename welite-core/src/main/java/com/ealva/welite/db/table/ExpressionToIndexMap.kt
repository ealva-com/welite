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

package com.ealva.welite.db.table

import com.ealva.welite.db.expr.Expression
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * Maps an expression to an index in a list. This map is constructed with a list of [Expression]s
 * and provides a fast mapping of the expression to it's index in the list. Also, if the list
 * contains a [SimpleDelegatingColumn], a search for that column or the original column returns the
 * same index. This provides for [CompoundSelect] results being indexed by the result columns of the
 * first simple select of the compound select. See [CompoundSelect] for more details.
 */
public interface ExpressionToIndexMap {
  public operator fun get(expression: Expression<*>): Int

  public operator fun set(expression: Expression<*>, index: Int)

  public fun clear()

  public fun makeCopy(): ExpressionToIndexMap

  public companion object {
    public operator fun invoke(list: List<Expression<*>> = emptyList()): ExpressionToIndexMap =
      ExpressionToIndexMapImpl(list)
  }
}

private typealias MapType = Object2IntMap<Expression<*>>
private typealias ConcreteMap = Object2IntOpenHashMap<Expression<*>>

private fun MapType.withDefaultReturn(): MapType = apply { defaultReturnValue(-1) }
private fun makeMap(capacity: Int): MapType = ConcreteMap(capacity).withDefaultReturn()
private fun makeMap(from: MapType): MapType = ConcreteMap(from).withDefaultReturn()

private fun List<Expression<*>>.toMap(): MapType {
  return makeMap(if (isNotEmpty()) size else DEFAULT_MAP_SIZE).apply {
    forEachIndexed { index, expression ->
      put(expression, index)
      if (expression is SimpleDelegatingColumn) put(expression.original, index)
    }
  }
}

private const val DEFAULT_MAP_SIZE = 16

private class ExpressionToIndexMapImpl(private val map: MapType) : ExpressionToIndexMap {
  constructor(list: List<Expression<*>>) : this(list.toMap())

  override fun makeCopy(): ExpressionToIndexMap {
    return ExpressionToIndexMapImpl(makeMap(map))
  }

  override fun get(expression: Expression<*>): Int {
    return map.getInt(expression)
  }

  override fun set(expression: Expression<*>, index: Int) {
    @Suppress("ReplacePutWithAssignment") // don't want my Int boxed
    map.put(expression, index)
  }

  override fun clear() {
    map.clear()
  }
}
