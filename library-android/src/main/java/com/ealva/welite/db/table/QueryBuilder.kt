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

package com.ealva.welite.db.table

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.type.AppendsToSqlBuilder
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.buildSql

typealias LimitOffset = Pair<Long, Long>

inline val LimitOffset.limit
  get() = first

inline val LimitOffset.offset
  get() = second

typealias OrderByPair = Pair<Expression<*>, SortOrder>

inline val OrderByPair.expression
  get() = first

inline val OrderByPair.ascDesc
  get() = second

@WeLiteMarker
class QueryBuilder private constructor(
  private var set: SelectFrom,
  private var where: Op<Boolean>?,
  private var count: Boolean = false
) : AppendsToSqlBuilder {
  private var groupBy = mutableListOf<Expression<*>>()
  private var orderBy = mutableListOf<OrderByPair>()
  private var having: Op<Boolean>? = null
  private var distinct: Boolean = false
  private var limitOffset: LimitOffset? = null

  fun copy(): QueryBuilder = QueryBuilder(set, where).also { copy ->
    copy.groupBy = groupBy.toMutableList()
    copy.orderBy = orderBy.toMutableList()
    copy.having = having
    copy.distinct = distinct
    copy.limitOffset = limitOffset
    copy.count = count
  }

  fun sourceSetColumnsInResult(): List<Column<*>> =
    set.sourceSet.columns.filter { it in set.resultColumns }

  /**
   * Changes [where] field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>) = apply { where = where.body() }

  /**
   * Save this query to reuse and possibly bind parameters each time it is used
   */
  fun build(): Query = Query(makeQuerySeed())

  fun makeQuerySeed(): QuerySeed {
    val (sql, types) = buildSql {
      append(this@QueryBuilder)
    }
    return QuerySeed(set.resultColumns, sql, types)
  }

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("SELECT ")

    if (count) {
      append("COUNT(*)")
    } else {
      if (distinct) append("DISTINCT ")
      set.resultColumns.appendEach { append(it) }
    }

    append(" FROM ")
    set.sourceSet.appendTo(this)

    where?.let { where ->
      append(" WHERE ").append(where)
    }

    if (!count) {
      if (groupBy.isNotEmpty()) {
        append(" GROUP BY ")
        groupBy.appendEach { expression ->
          append(((expression as? ExpressionAlias)?.aliasOnlyExpression() ?: expression))
        }
      }

      having?.let { having ->
        append(" HAVING ").append(having)
      }

      if (orderBy.isNotEmpty()) {
        append(" ORDER BY ")
        orderBy.appendEach { orderByPair ->
          val alias = (orderByPair.expression as? ExpressionAlias<*>)?.alias
          if (alias != null) append(alias) else append(orderByPair.expression)
          append(" ").append(orderByPair.ascDesc.sqlName)
        }
      }

      limitOffset?.let { limitOffset ->
        append(" LIMIT ").append(limitOffset.limit)
        if (limitOffset.second > 0) {
          append(" OFFSET ").append(limitOffset.offset)
        }
      }
    }
  }

  fun distinct(value: Boolean = true) = apply { distinct = value }

  fun groupBy(vararg columns: Expression<*>) = apply { groupBy.addAll(columns) }

  fun having(op: () -> Op<Boolean>) = apply {
    if (having != null) {
      error(
        """HAVING clause is specified twice. Old value = '""" + having +
          """', new value = '""" + Op.build { op() } + """'"""
      )
    }
    having = Op.build { op() }
  }

  fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC) = orderBy(column to order)

  fun orderBy(vararg order: OrderByPair) = apply { orderBy.addAll(order) }

  fun limit(limit: Long, offset: Long = 0) = apply {
    limitOffset = LimitOffset(limit, offset)
  }

  fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? {
    return set.sourceSet.columns.find { it == original }
  }

  fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? {
    @Suppress("UNCHECKED_CAST")
    return set.resultColumns.find { it == original } as? SqlTypeExpressionAlias<T>
  }

  companion object {
    internal operator fun invoke(
      set: SelectFrom,
      where: Op<Boolean>?,
      count: Boolean = false
    ): QueryBuilder = QueryBuilder(set, where, count)
  }
}

/**
 * Add `andPart` to where condition with `and` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun QueryBuilder.andWhere(andPart: () -> Op<Boolean>) = adjustWhere {
  val expr = Op.build { andPart() }
  if (this == null) expr else this and expr
}

/**
 * Add `orPart` to where condition with `or` operator.
 * @return same Query instance which was provided as a receiver.
 */
fun QueryBuilder.orWhere(orPart: () -> Op<Boolean>) = adjustWhere {
  val expr = Op.build { orPart() }
  if (this == null) expr else this or expr
}
