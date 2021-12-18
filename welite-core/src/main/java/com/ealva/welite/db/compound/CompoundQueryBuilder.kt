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

package com.ealva.welite.db.compound

import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.OrderBy
import com.ealva.welite.db.table.QueryBuilder

/**
 * Select all columns and all rows
 */
public fun <C : ColumnSet> CompoundSelect<C>.selectAll(): CompoundQueryBuilder<C> =
  select().where(null)

/**
 * Select COUNT(*) (no columns) using optional [where]
 */
@Suppress("unused")
public fun <C : ColumnSet> CompoundSelect<C>.selectCount(
  where: (C.() -> Op<Boolean>)? = null
): CompoundQueryBuilder<C> = CompoundQueryBuilder(
  QueryBuilder(select(emptyList()), where?.let { firstColumnSet.it() }, count = true),
  mapSelectToResult.copy()
)

public fun <C : ColumnSet> CompoundSelectFrom<C>.where(
  where: Op<Boolean>?
): CompoundQueryBuilder<C> = CompoundQueryBuilder(QueryBuilder(this, where), mapSelectToResult)

public inline fun <C : ColumnSet> CompoundSelectFrom<C>.where(
  where: C.() -> Op<Boolean>
): QueryBuilder<C> = where(sourceSet.where())

/** All rows will be returned */
public fun <C : ColumnSet> CompoundSelectFrom<C>.all(): CompoundQueryBuilder<C> = where(null)

public class CompoundQueryBuilder<C : ColumnSet>(
  private val original: QueryBuilder<C>,
  private val selectToResultMap: SelectColumnToResultColumnMap
) : QueryBuilder<C> by original {
  override fun addGroupBy(column: Expression<*>): QueryBuilder<C> = apply {
    original.addGroupBy(selectToResultMap[column] ?: column)
  }

  override fun addOrderBy(orderBy: OrderBy): QueryBuilder<C> = apply {
    if (orderBy !== OrderBy.NONE) {
      val exp = orderBy.expression
      original.addOrderBy(
        selectToResultMap[exp]?.let { OrderBy(it, orderBy.ascDesc, orderBy.collate) } ?: orderBy
      )
    }
  }
}

/**
 * Apply the [Union][CompoundSelectOp.Union] operator to this simple select with another
 * simple select
 */
public infix fun <C : ColumnSet> QueryBuilder<C>.union(
  other: QueryBuilder<*>
): CompoundSelect<C> = CompoundSelect(CompoundSelectOp.Union, this, other)

/**
 * Apply the [UnionAll][CompoundSelectOp.UnionAll] operator to this simple select with another
 * simple select
 */
public infix fun <C : ColumnSet> QueryBuilder<C>.unionAll(
  other: QueryBuilder<*>
): CompoundSelect<C> = CompoundSelect(CompoundSelectOp.UnionAll, this, other)

/**
 * Apply the [Intersect][CompoundSelectOp.Intersect] operator to this simple select with another
 * simple select
 */
public infix fun <C : ColumnSet> QueryBuilder<C>.intersect(
  other: QueryBuilder<*>
): CompoundSelect<C> = CompoundSelect(CompoundSelectOp.Intersect, this, other)

/**
 * Apply the [Except][CompoundSelectOp.Except] operator to this simple select with another
 * simple select
 */
public infix fun <C : ColumnSet> QueryBuilder<C>.except(
  other: QueryBuilder<*>
): CompoundSelect<C> = CompoundSelect(CompoundSelectOp.Except, this, other)

/**
 * Apply the [Union][CompoundSelectOp.Union] operator adding another simple select to this
 * CompoundSelect
 */
public infix fun <C : ColumnSet> CompoundSelect<C>.union(
  other: QueryBuilder<*>
): CompoundSelect<C> = apply { add(CompoundSelectOp.Union, other) }

/**
 * Apply the [UnionAll][CompoundSelectOp.UnionAll] operator adding another simple select to this
 * CompoundSelect
 */
public infix fun <C : ColumnSet> CompoundSelect<C>.unionAll(
  other: QueryBuilder<*>
): CompoundSelect<C> = apply { add(CompoundSelectOp.UnionAll, other) }

/**
 * Apply the [Intersect][CompoundSelectOp.Intersect] operator adding another simple select to this
 * CompoundSelect
 */
public infix fun <C : ColumnSet> CompoundSelect<C>.intersect(
  other: QueryBuilder<*>
): CompoundSelect<C> = apply { add(CompoundSelectOp.Intersect, other) }

/**
 * Apply the [Except][CompoundSelectOp.Except] operator adding another simple select to this
 * CompoundSelect
 */
public infix fun <C : ColumnSet> CompoundSelect<C>.except(
  other: QueryBuilder<*>
): CompoundSelect<C> = apply { add(CompoundSelectOp.Except, other) }
