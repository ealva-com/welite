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

public typealias LimitOffset = Pair<Long, Long>

public inline val LimitOffset.limit: Long
  get() = first

public inline val LimitOffset.offset: Long
  get() = second

public typealias OrderByPair = Pair<Expression<*>, SortOrder>

public inline val OrderByPair.expression: Expression<*>
  get() = first

public inline val OrderByPair.ascDesc: SortOrder
  get() = second

public interface QueryBuilder : AppendsToSqlBuilder {
  public val resultColumns: List<Expression<*>>

  /**
   * Changes where field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  public fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): QueryBuilder

  /**
   * Build the QuerySeed as a basis for a Query instance or executing a query
   */
  public fun build(): QuerySeed
  public fun statementSeed(): StatementSeed
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder
  public fun distinct(value: Boolean = true): QueryBuilder
  public fun groupBy(column: Expression<*>): QueryBuilder
  public fun groupBy(columnList: List<Expression<*>>): QueryBuilder
  public fun having(op: () -> Op<Boolean>): QueryBuilder
  public fun orderBy(column: Expression<*>, order: SortOrder = SortOrder.ASC): QueryBuilder
  public fun orderBy(pair: OrderByPair): QueryBuilder
  public fun orderBy(orderList: List<OrderByPair>): QueryBuilder
  public val hasOrderBy: Boolean
  public fun limit(limit: Long, offset: Long = 0): QueryBuilder
  public val hasLimit: Boolean

  /**
   * Used in QueryBuilderAlias
   */
  public fun sourceSetColumnsInResult(): List<Column<*>>

  /**
   * Used in QueryBuilderAlias
   */
  public fun <T> findSourceSetOriginal(original: Column<T>): Column<*>?

  /**
   * Used in QueryBuilderAlias
   */
  public fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>?

  public companion object {
    public operator fun invoke(
      set: SelectFrom,
      where: Op<Boolean>?,
      count: Boolean = false
    ): QueryBuilder = QueryBuilderImpl(set, where, count)
  }
}

public inline val QueryBuilder.hasNoOrderBy: Boolean
  get() = !hasOrderBy

public inline val QueryBuilder.hasNoLimit: Boolean
  get() = !hasLimit

@WeLiteMarker
private class QueryBuilderImpl(
  private var selectFrom: SelectFrom,
  private var where: Op<Boolean>?,
  private var count: Boolean = false
) : QueryBuilder {
  private var groupBy: MutableSet<Expression<*>> = LinkedHashSet()
  private var orderBy: MutableSet<OrderByPair> = LinkedHashSet()
  private var having: Op<Boolean>? = null
  private var distinct: Boolean = false
  private var limitOffset: LimitOffset? = null

  override val resultColumns: List<Expression<*>>
    get() = selectFrom.resultColumns

  /**
   * Changes [where] field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  override fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): QueryBuilder =
    apply { where = where.body() }

  /**
   * Build the QuerySeed as a basis for a Query instance or executing a query
   */
  override fun build(): QuerySeed = makeQuerySeed()

  override fun statementSeed(): StatementSeed = buildSql { append(this@QueryBuilderImpl) }

  private fun makeQuerySeed(): QuerySeed = QuerySeed(statementSeed(), selectFrom.resultColumns)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("SELECT ")

    if (count) {
      append("COUNT(*)")
    } else {
      if (distinct) {
        append("DISTINCT ")
      }
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

  override fun distinct(value: Boolean): QueryBuilder = apply { distinct = value }

  override fun groupBy(column: Expression<*>): QueryBuilder = apply {
    groupBy.add(column)
  }

  override fun groupBy(columnList: List<Expression<*>>): QueryBuilder = apply {
    columnList.forEach { column -> groupBy(column) }
  }

  override fun having(op: () -> Op<Boolean>): QueryBuilder = apply {
    if (having != null) {
      error("""HAVING specified twice. Old value = '$having', new value = '${Op.build { op() }}'""")
    }
    having = Op.build { op() }
  }

  override fun orderBy(column: Expression<*>, order: SortOrder): QueryBuilder =
    orderBy(column to order)

  override fun orderBy(pair: OrderByPair): QueryBuilder = apply {
    orderBy.add(pair)
  }

  override fun orderBy(orderList: List<OrderByPair>): QueryBuilder = apply {
    orderList.forEach { pair -> orderBy(pair) }
  }

  override val hasOrderBy: Boolean
    get() = orderBy.isNotEmpty()

  override fun limit(limit: Long, offset: Long): QueryBuilder = apply {
    limitOffset = LimitOffset(limit, offset)
  }

  override val hasLimit: Boolean
    get() = limitOffset != null

  /**
   * Used in QueryBuilderAlias
   */
  override fun sourceSetColumnsInResult(): List<Column<*>> = selectFrom.sourceSetColumnsInResult()

  /**
   * Used in QueryBuilderAlias
   */
  override fun <T> findSourceSetOriginal(original: Column<T>): Column<*>? =
    selectFrom.findSourceSetOriginal(original)

  /**
   * Used in QueryBuilderAlias
   */
  override fun <T> findResultColumnExpressionAlias(
    original: SqlTypeExpression<T>
  ): SqlTypeExpressionAlias<T>? = selectFrom.findResultColumnExpressionAlias(original)
}

/**
 * In this QueryBuilder add `andPart` to where condition with `and` operator, and return the same
 * QueryBuilder.
 */
public fun QueryBuilder.andWhere(andPart: () -> Op<Boolean>): QueryBuilder = adjustWhere {
  val expr = Op.build { andPart() }
  if (this == null) expr else this and expr
}

/**
 * In this QueryBuilder add `orPart` to where condition with `or` operator, and return the same
 * QueryBuilder.
 */
@Suppress("unused")
public fun QueryBuilder.orWhere(orPart: () -> Op<Boolean>): QueryBuilder = adjustWhere {
  val expr = Op.build { orPart() }
  if (this == null) expr else this or expr
}

/**
 * Use this query builder as an expression
 */
public fun <T : Any> QueryBuilder.asExpression(): Expression<T> {
  return wrapAsExpression(this)
}

public fun <T : Any> wrapAsExpression(queryBuilder: QueryBuilder): BaseExpression<T> =
  object : BaseExpression<T>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
      append("(")
      queryBuilder.appendTo(this)
      append(")")
    }
  }
