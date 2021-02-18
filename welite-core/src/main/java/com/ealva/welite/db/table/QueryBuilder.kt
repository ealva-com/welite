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

public interface QueryBuilder<out C : ColumnSet> : AppendsToSqlBuilder {

  /**
   * Source of the query, typically a Table, Join, View, or Alias. This is carried from select
   * through to this builder to be used as a receiver to try to shorten references to columns. It
   * usually alleviates the need to specify the Table. Not useful in other situations such as a
   * [Join] or CompoundSelect
   */
  public val sourceSet: C

  /**
   * List representing the columns in the result of the query. [Cursor] supports getting the
   * column value using the Expression. Expressions are typically table [Column]s but can be
   * any range of expressions.
   */
  public val resultColumns: List<Expression<*>>

  /**
   * If [distinct] is true duplicate rows are removed from the results
   */
  public fun distinct(distinct: Boolean = true): QueryBuilder<C>

  /**
   * Adds a column ([Expression<*>]) to group the results by.
   *
   * Prefer the extension functions [groupBy] and [groupsBy] as they have the original source set
   * (typically a [Table]) as receiver and are inline
   */
  public fun addGroupBy(column: Expression<*>): QueryBuilder<C>

  /**
   * If a HAVING clause is specified, it is evaluated once for each group of rows as a boolean
   * expression. If the result of evaluating the HAVING clause is false, the group is discarded. If
   * the HAVING clause is an aggregate expression, it is evaluated across all rows in the group. If
   * a HAVING clause is a non-aggregate expression, it is evaluated with respect to an arbitrarily
   * selected row from the group. The HAVING expression may refer to values, even aggregate
   * functions, that are not in the result.
   */
  public fun having(op: C.() -> Op<Boolean>): QueryBuilder<C>

  /**
   * Adds an OrderByPair (column to [SortOrder]) to sort the results by.
   *
   * Prefer the extension functions [orderByAsc], [orderBy], and [ordersBy] as they have the
   * original source set (typically a [Table]) as the receiver and are inline
   */
  public fun addOrderBy(pair: OrderByPair): QueryBuilder<C>

  /**
   * Does this query contain 1 or more order by clause. Simple select statements do not have an
   * order by clause.
   */
  public val hasOrderBy: Boolean

  /**
   * Add a [limit] and [offset] to this query. The [offset] defaults to 0
   */
  public fun limit(limit: Long, offset: Long = 0): QueryBuilder<C>

  /**
   * Does this query have a limit/offset clause. Simple select statements do not have a
   * limit/offset.
   */
  public val hasLimit: Boolean

  /**
   * Changes where field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  public fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): QueryBuilder<C>

  /**
   * Build the QuerySeed as a basis for a Query instance or executing a query
   */
  public fun build(): QuerySeed<C>

  public fun statementSeed(): StatementSeed

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
    public operator fun <C : ColumnSet> invoke(
      set: SelectFrom<C>,
      where: Op<Boolean>?,
      count: Boolean = false
    ): QueryBuilder<C> = QueryBuilderImpl(set, where, count)
  }
}

/**
 * Select all columns [where]
 */
public fun <C : ColumnSet> C.selectWhere(where: Op<Boolean>?): QueryBuilder<C> =
  selects().where(where)

/**
 * Select all columns of this [ColumnSet] and call [where] to make the where expression
 */
public fun <C : ColumnSet> C.selectWhere(
  where: C.() -> Op<Boolean>
): QueryBuilder<C> = selectWhere(where())

/**
 * Select all columns and all rows
 */
public fun <C : ColumnSet> C.selectAll(): QueryBuilder<C> = selectWhere(null)

/**
 * Select COUNT(*) (no columns) using optional [where]
 */
public fun <C : ColumnSet> C.selectCount(where: (C.() -> Op<Boolean>)? = null): QueryBuilder<C> =
  QueryBuilder(set = selects { emptyList() }, where = where?.invoke(this), count = true)

public fun <C : ColumnSet> SelectFrom<C>.where(where: Op<Boolean>?): QueryBuilder<C> =
  QueryBuilder(this, where)

public inline fun <C : ColumnSet> SelectFrom<C>.where(
  where: C.() -> Op<Boolean>
): QueryBuilder<C> = where(where(sourceSet))

/** All rows will be returned */
public fun <C : ColumnSet> SelectFrom<C>.all(): QueryBuilder<C> = where(null)

public inline val <C : ColumnSet> QueryBuilder<C>.hasNoOrderBy: Boolean
  get() = !hasOrderBy

public inline val <C : ColumnSet> QueryBuilder<C>.hasNoLimit: Boolean
  get() = !hasLimit

@WeLiteMarker
private class QueryBuilderImpl<out C : ColumnSet>(
  private var selectFrom: SelectFrom<C>,
  private var where: Op<Boolean>?,
  private var count: Boolean = false
) : QueryBuilder<C> {
  private var groupBy: MutableSet<Expression<*>> = LinkedHashSet()
  private var orderBy: MutableSet<OrderByPair> = LinkedHashSet()
  private var having: Op<Boolean>? = null
  private var distinct: Boolean = false
  private var limitOffset: LimitOffset? = null

  override val sourceSet: C
    get() = selectFrom.sourceSet

  override val resultColumns: List<Expression<*>>
    get() = selectFrom.resultColumns

  override fun distinct(distinct: Boolean): QueryBuilder<C> = apply { this.distinct = distinct }

  override fun addGroupBy(column: Expression<*>): QueryBuilder<C> = apply {
    groupBy.add(column)
  }

  override fun having(op: C.() -> Op<Boolean>): QueryBuilder<C> = apply {
    val newHaving = Op.build { sourceSet.op() }
    if (having != null) {
      error("""HAVING specified twice. Old value = '$having', new value = '$newHaving'""")
    }
    having = newHaving
  }

  override fun addOrderBy(pair: OrderByPair): QueryBuilder<C> = apply {
    orderBy.add(pair)
  }

  override val hasOrderBy: Boolean
    get() = orderBy.isNotEmpty()

  override fun limit(limit: Long, offset: Long): QueryBuilder<C> = apply {
    limitOffset = LimitOffset(limit, offset)
  }

  override val hasLimit: Boolean
    get() = limitOffset != null

  /**
   * Changes [where] field of a Query.
   * @param body new WHERE condition builder, previous value used as a receiver
   */
  override fun adjustWhere(body: Op<Boolean>?.() -> Op<Boolean>): QueryBuilder<C> =
    apply { where = where.body() }

  /**
   * Build the QuerySeed as a basis for a Query. Query's are reusable and are executed within the
   * scope of a Queryable or Transaction, using sequence or a forEach to convert rows from a cursor
   * to an object. QueryBuilder may be executed directly and uses a Query at a lower level.
   * Typically building a Query then binding any parameters at execution time is more efficient.
   */
  override fun build(): QuerySeed<C> = QuerySeed(statementSeed(), selectFrom)

  /**
   * Statement seed for a Select statement used typically within a Trigger
   */
  override fun statementSeed(): StatementSeed = buildSql { append(this@QueryBuilderImpl) }

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

public inline fun <C : ColumnSet> QueryBuilder<C>.groupBy(
  column: C.() -> Expression<*>
): QueryBuilder<C> = apply {
  addGroupBy(sourceSet.column())
}

public inline fun <C : ColumnSet> QueryBuilder<C>.groupsBy(
  list: C.() -> List<Expression<*>>
): QueryBuilder<C> = apply {
  sourceSet.list().forEach { column -> addGroupBy(column) }
}

public inline fun <C : ColumnSet> QueryBuilder<C>.ordersBy(
  orderList: C.() -> List<OrderByPair>
): QueryBuilder<C> = apply {
  sourceSet.orderList().forEach { pair -> addOrderBy(pair) }
}

public inline fun <C : ColumnSet> QueryBuilder<C>.orderBy(
  pair: C.() -> OrderByPair
): QueryBuilder<C> = apply {
  addOrderBy(sourceSet.pair())
}

public inline fun <C : ColumnSet> QueryBuilder<C>.orderByAsc(
  column: C.() -> Expression<*>
): QueryBuilder<C> = apply {
  addOrderBy(sourceSet.column() to SortOrder.ASC)
}

/**
 * In this QueryBuilder add `andPart` to where condition with `and` operator, and return the same
 * QueryBuilder.
 */
public fun <C : ColumnSet> QueryBuilder<C>.andWhere(
  andPart: () -> Op<Boolean>
): QueryBuilder<C> = adjustWhere {
  val expr = Op.build { andPart() }
  if (this == null) expr else this and expr
}

/**
 * In this QueryBuilder add `orPart` to where condition with `or` operator, and return the same
 * QueryBuilder.
 */
@Suppress("unused")
public fun <C : ColumnSet> QueryBuilder<C>.orWhere(
  orPart: () -> Op<Boolean>
): QueryBuilder<C> = adjustWhere {
  val expr = Op.build { orPart() }
  if (this == null) expr else this or expr
}

/**
 * Use this query builder as an expression
 */
public fun <T : Any> QueryBuilder<*>.asExpression(): Expression<T> {
  return wrapAsExpression(this)
}

public fun <T : Any> wrapAsExpression(
  queryBuilder: QueryBuilder<*>
): BaseExpression<T> =
  object : BaseExpression<T>() {
    override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
      append("(")
      queryBuilder.appendTo(this)
      append(")")
    }
  }
