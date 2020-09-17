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

import com.ealva.welite.db.expr.BaseExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.or
import com.ealva.welite.db.type.AppendsToSqlBuilder
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.StatementSeed
import com.ealva.welite.db.type.buildSql

typealias LimitOffset = Pair<Long, Long>

inline val LimitOffset.limit: Long
  get() = first

inline val LimitOffset.offset: Long
  get() = second

typealias OrderByPair = Pair<Expression<*>, SortOrder>

inline val OrderByPair.expression: Expression<*>
  get() = first

inline val OrderByPair.ascDesc: SortOrder
  get() = second

@WeLiteMarker
class QueryBuilder private constructor(
  private var selectFrom: SelectFrom,
  private var where: Op<Boolean>?,
  private var count: Boolean = false
) : AppendsToSqlBuilder {
  private var groupBy = mutableListOf<Expression<*>>()
  private var orderBy = mutableListOf<OrderByPair>()
  private var having: Op<Boolean>? = null
  private var distinct: Boolean = false
  private var limitOffset: LimitOffset? = null

  fun copy(): QueryBuilder = QueryBuilder(selectFrom, where).also { copy ->
    copy.groupBy = groupBy.toMutableList()
    copy.orderBy = orderBy.toMutableList()
    copy.having = having
    copy.distinct = distinct
    copy.limitOffset = limitOffset
    copy.count = count
  }

  /**
   * Changes [where] field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>) = apply { where = where.body() }

  /**
   * Build the QuerySeed as a basis for a Query instance or executing a query
   */
  internal fun build(): QuerySeed = makeQuerySeed()

  internal fun statementSeed(): StatementSeed = buildSql { append(this@QueryBuilder) }

  private fun makeQuerySeed(): QuerySeed = QuerySeed(statementSeed(), selectFrom.resultColumns)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("SELECT ")

    if (count) {
      append("COUNT(*)")
    } else {
      if (distinct) append("DISTINCT ")
      selectFrom.appendResultColumns(this)
    }

    selectFrom.appendFrom(this)

    where?.let { where ->
      append(" WHERE ")
      append(where)
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
        if (limitOffset.offset > 0) {
          append(" OFFSET ").append(limitOffset.offset)
        }
      }
    }
  }

  fun distinct(value: Boolean = true) = apply { distinct = value }

  fun groupBy(vararg columns: Expression<*>) = apply { groupBy.addAll(columns) }

  @Suppress("unused")
  fun having(op: () -> Op<Boolean>) = apply {
    if (having != null) {
      error("""HAVING specified twice. Old value = '$having', new value = '${Op.build { op() }}'""")
    }
    having = Op.build { op() }
  }

  fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC) = orderBy(column to order)

  fun orderBy(vararg order: OrderByPair) = apply { orderBy.addAll(order) }

  fun limit(limit: Long, offset: Long = 0) = apply {
    limitOffset = LimitOffset(limit, offset)
  }

  /**
   * Used in QueryBuilderAlias
   */
  internal fun sourceSetColumnsInResult(): List<Column<*>> = selectFrom.sourceSetColumnsInResult()

  /**
   * Used in QueryBuilderAlias
   */
  internal fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? =
    selectFrom.findSourceSetOriginal(original)

  /**
   * Used in QueryBuilderAlias
   */
  internal fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? = selectFrom.findResultColumnExpressionAlias(original)

  companion object {
    /**
     * Make a QueryBuilder from the initial SelectFrom, a where clause, and indicate if this will
     * be a "count" query.
     */
    internal operator fun invoke(
      set: SelectFrom,
      where: Op<Boolean>?,
      count: Boolean = false
    ): QueryBuilder = QueryBuilder(set, where, count)
  }
}

/**
 * In this QueryBuilder add `andPart` to where condition with `and` operator, and return the same
 * QueryBuilder.
 */
fun QueryBuilder.andWhere(andPart: () -> Op<Boolean>) = adjustWhere {
  val expr = Op.build { andPart() }
  if (this == null) expr else this and expr
}

/**
 * In this QueryBuilder add `orPart` to where condition with `or` operator, and return the same
 * QueryBuilder.
 */
@Suppress("unused")
fun QueryBuilder.orWhere(orPart: () -> Op<Boolean>) = adjustWhere {
  val expr = Op.build { orPart() }
  if (this == null) expr else this or expr
}

/**
 * Use this query builder as an expression
 */
fun <T : Any> QueryBuilder.asExpression(): Expression<T> {
  return wrapAsExpression(this)
}

fun <T : Any> wrapAsExpression(queryBuilder: QueryBuilder) = object : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("(")
    queryBuilder.appendTo(this)
    append(")")
  }
}
