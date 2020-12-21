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
import com.ealva.welite.db.table.BaseColumnSet
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ColumnSet
import com.ealva.welite.db.table.Join
import com.ealva.welite.db.table.JoinType
import com.ealva.welite.db.table.QueryBuilder
import com.ealva.welite.db.table.SimpleDelegatingColumn
import com.ealva.welite.db.table.hasNoLimit
import com.ealva.welite.db.table.hasNoOrderBy
import com.ealva.welite.db.type.AppendsToSqlBuilder
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.asIdentity

public enum class CompoundSelectOp(private val op: String) : AppendsToSqlBuilder {
  /**
   * The UNION operator combines rows from result sets into a single result set removing duplicate
   * rows.
   */
  Union(" UNION "),

  /**
   * The UNION ALL operator is the same as [Union] except duplicate rows are not removed.
   */
  UnionAll(" UNION ALL "),

  /**
   * SQLite INTERSECT operator compares the result sets of two queries and returns distinct rows
   * that are output by both queries.
   */
  Intersect(" INTERSECT "),

  /**
   * SQLite EXCEPT operator compares the result sets of two queries and returns distinct rows from
   * the left query that are not output by the right query.
   */
  Except(" EXCEPT ");

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply { append(op) }
}

public interface SelectColumnToResultColumnMap {
  public operator fun get(column: Expression<*>): Expression<*>?

  public fun copy(): SelectColumnToResultColumnMap
}

/**
 * Two or more simple SELECT statements may be connected together to form a compound SELECT using
 * the UNION, UNION ALL, INTERSECT or EXCEPT operator. A CompoundSelect is series of select
 * statements ([QueryBuilder]s) joined by [CompoundSelectOp]s, such as Union, UnionAll, etc. There
 * are functions for each type of operator to connect QueryBuilders or add to a existing
 * CompoundSelect.
 *
 * ```kotlin
 * val compoundSelect = tableA.select(tableA.id).all() union tableB.select(tableB.id).all()
 * ```
 * In a compound SELECT, all the constituent SELECTs must return the same number of result columns.
 * As the components of a compound SELECT must be simple SELECT statements, they may not contain
 * ORDER BY or LIMIT clauses. ORDER BY and LIMIT clauses may only occur at the end of the entire
 * compound SELECT, and then only if the final element of the compound is not a VALUES clause.
 *
 * A compound SELECT created using UNION ALL operator returns all the rows from the SELECT to the
 * left of the UNION ALL operator, and all the rows from the SELECT to the right of it. The UNION
 * operator works the same way as UNION ALL, except that duplicate rows are removed from the final
 * result set. The INTERSECT operator returns the intersection of the results of the left and right
 * SELECTs. The EXCEPT operator returns the subset of rows returned by the left SELECT that are not
 * also returned by the right-hand SELECT. Duplicate rows are removed from the results of INTERSECT
 * and EXCEPT operators before the result set is returned.
 *
 * For the purposes of determining duplicate rows for the results of compound SELECT operators, NULL
 * values are considered equal to other NULL values and distinct from all non-NULL values. The
 * collation sequence used to compare two text values is determined as if the columns of the left
 * and right-hand SELECT statements were the left and right-hand operands of the equals (=)
 * operator, except that greater precedence is not assigned to a collation sequence specified with
 * the postfix COLLATE operator. No affinity transformations are applied to any values when
 * comparing rows as part of a compound SELECT.
 *
 * When three or more simple SELECTs are connected into a compound SELECT, they group from left to
 * right. In other words, if "A", "B" and "C" are all simple SELECT statements, (A op B op C) is
 * processed as ((A op B) op C).
 *
 * [SQLite Compound Select](https://sqlite.org/lang_select.html#compound_select_statements)
 */
public interface CompoundSelect<out C : ColumnSet> : ColumnSet {
  public val firstColumnSet: C

  public fun add(op: CompoundSelectOp, builder: QueryBuilder<*>)

  public val resultColumns: List<Expression<*>>

  /**
   * The result columns of a CompoundSelect are the columns of the first select (QueryBuilder) but
   * without a fully qualified name. The result columns must be used when referring to the results
   * of the CompoundSelect. For example, ```QueryBuilder.orderBy``` must use the result column.
   *
   * For example, if the first select uses CustomerTable and selects CustomerTable.id,
   * CustomerTable.firstName, and CustomerTable.lastName, referring to those columns typically
   * requires mapping. eg. ```mapSelectToResult[CustomerTable.firstName]```
   */
  public val mapSelectToResult: SelectColumnToResultColumnMap

  public companion object {
    public operator fun <C : ColumnSet> invoke(
      op: CompoundSelectOp,
      first: QueryBuilder<C>,
      second: QueryBuilder<*>
    ): CompoundSelect<C> = CompoundSelectImpl(op, first, second)
  }
}

private const val SIZE_ERROR =
  "In a compound SELECT, all the constituent SELECTs must return the same number of result columns."

private fun QueryBuilder<*>.requireSimpleSelect() {
  require(hasNoOrderBy && hasNoLimit) { "Simple SELECT must have no ORDER BY or LIMIT" }
}

private class CompoundSelectImpl<out C : ColumnSet>(
  op: CompoundSelectOp,
  first: QueryBuilder<C>,
  second: QueryBuilder<*>
) : BaseColumnSet(), CompoundSelect<C>, SelectColumnToResultColumnMap {
  override val firstColumnSet: C = first.sourceSet

  private val builderList = mutableListOf(first, second).also {
    val sizeFirst = first.resultColumns.size
    val sizeSecond = second.resultColumns.size
    require(sizeFirst == sizeSecond) {
      "$SIZE_ERROR first=$sizeFirst second=$sizeSecond"
    }
    first.requireSimpleSelect()
    second.requireSimpleSelect()
  }
  private val operatorList = mutableListOf(op)

  override val columns: List<Column<*>> = builderList[0].sourceSetColumnsInResult()
  override val identity: Identity = "".asIdentity(false)

  private val selectToResultMap = mutableMapOf<Expression<*>, Expression<*>>()
  private fun mapSelectColumnToResult(column: Expression<*>): Expression<*> {
    return if (column is Column) {
      SimpleDelegatingColumn(column).also { selectToResultMap[column] = it }
    } else {
      column
    }
  }

  override val resultColumns: List<Expression<*>> = first
    .resultColumns
    .map { expression -> mapSelectColumnToResult(expression) }

  override fun add(op: CompoundSelectOp, builder: QueryBuilder<*>) {
    builder.requireSimpleSelect()
    val sizeAdd = builder.resultColumns.size
    val sizeCurrent = resultColumns.size
    require(sizeAdd == sizeCurrent) {
      "$SIZE_ERROR current=$sizeCurrent add=$sizeAdd"
    }
    operatorList += op
    builderList += builder
    check(builderList.size == operatorList.size + 1)
  }

  override val mapSelectToResult: SelectColumnToResultColumnMap
    get() = this

  override fun get(column: Expression<*>): Expression<*>? = selectToResultMap[column]

  override fun copy(): SelectColumnToResultColumnMap {
    class SelectColumnToResultColumnMapImpl(
      private val map: Map<Expression<*>, Expression<*>>
    ) : SelectColumnToResultColumnMap {
      override fun get(column: Expression<*>): Expression<*>? = map[column]

      override fun copy(): SelectColumnToResultColumnMap =
        SelectColumnToResultColumnMapImpl(map.toMap())
    }
    return SelectColumnToResultColumnMapImpl(selectToResultMap.toMap())
  }

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    check(builderList.size == operatorList.size + 1)
    append("(")
    append(builderList[0])
    operatorList.forEachIndexed { index, type ->
      append(type)
      append(builderList[index + 1])
    }
    append(")")
  }

  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    column: Expression<*>?,
    joinToColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join = Join(this, joinTo, column, joinToColumn, joinType, additionalConstraint)

  override infix fun innerJoin(joinTo: ColumnSet): Join =
    Join(this, joinTo, joinType = JoinType.INNER)
  override infix fun leftJoin(joinTo: ColumnSet): Join =
    Join(this, joinTo, joinType = JoinType.LEFT)
  override infix fun crossJoin(joinTo: ColumnSet): Join =
    Join(this, joinTo, joinType = JoinType.CROSS)
  override fun naturalJoin(joinTo: ColumnSet): Join =
    Join(this, joinTo, joinType = JoinType.NATURAL)
}
