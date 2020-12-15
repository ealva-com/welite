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

  public companion object {
    public operator fun invoke(list: List<Expression<*>>): ExpressionToIndexMap =
      ExpressionToIntMapImpl(list)
  }
}

private class ExpressionToIntMapImpl(list: List<Expression<*>>) : ExpressionToIndexMap {
  val map = Object2IntOpenHashMap<Expression<*>>(list.size).apply {
    defaultReturnValue(-1)
    list.forEachIndexed { index, expression ->
      put(expression, index)
      if (expression is SimpleDelegatingColumn) put(expression.original, index)
    }
  }

  override fun get(expression: Expression<*>): Int {
    return map.getInt(expression)
  }
}
