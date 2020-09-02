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
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.type.SqlBuilder

typealias JoinCondition = Pair<Expression<*>, Expression<*>>

enum class JoinType(val hasCondition: Boolean) {
  INNER(true),
  LEFT(true),
  CROSS(false),
  NATURAL(false)
}

inline val JoinType.hasNoCondition
  get() = !hasCondition

fun <C1 : ColumnSet, C2 : ColumnSet> C1.innerJoin(
  otherTable: C2,
  onColumn: C1.() -> Expression<*>,
  otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.INNER, onColumn(), otherTable.otherColumn())

fun <C1 : ColumnSet, C2 : ColumnSet> C1.leftJoin(
  otherTable: C2,
  onColumn: C1.() -> Expression<*>,
  otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.LEFT, onColumn(), otherTable.otherColumn())

fun <C1 : ColumnSet, C2 : ColumnSet> C1.crossJoin(
  otherTable: C2,
  onColumn: C1.() -> Expression<*>,
  otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.CROSS, onColumn(), otherTable.otherColumn())

fun <C1 : ColumnSet, C2 : ColumnSet> C1.naturalJoin(
  otherTable: C2,
  onColumn: C1.() -> Expression<*>,
  otherColumn: C2.() -> Expression<*>
): Join = join(otherTable, JoinType.NATURAL, onColumn(), otherTable.otherColumn())

class Join(val columnSet: ColumnSet) : ColumnSet {

  override val columns: List<Column<*>>
    get() = _joinParts.flatMapTo(columnSet.columns.toMutableList()) { it.joinPart.columns }

  private val _joinParts: MutableList<JoinPart> = mutableListOf()
  internal val joinParts: List<JoinPart>
    get() = _joinParts.toList()

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    columnSet.appendTo(this)
    _joinParts.forEach { p ->
      append(" ")
      append(p.joinType.toString())
      append(" JOIN ")
      val isJoin = p.joinPart is Join
      if (isJoin) {
        append("(")
      }
      p.joinPart.appendTo(this)
      if (isJoin) {
        append(")")
      }
      if (p.joinType.hasCondition) {
        append(" ON ")
        p.appendConditions(this)
      }
    }
  }

  private fun asJoinCondition(
    onColumn: Expression<*>?,
    otherColumn: Expression<*>?
  ): List<JoinCondition> {
    if (onColumn == null || otherColumn == null) return emptyList()
    return listOf(JoinCondition(onColumn, otherColumn))
  }

  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>?,
    otherColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join {
    return doJoin(joinTo, joinType, asJoinCondition(thisColumn, otherColumn), additionalConstraint)
  }

  override infix fun innerJoin(joinTo: ColumnSet): Join = doJoin(joinTo, JoinType.INNER)
  override infix fun leftJoin(joinTo: ColumnSet): Join = doJoin(joinTo, JoinType.LEFT)
  override infix fun crossJoin(joinTo: ColumnSet): Join = doJoin(joinTo, JoinType.CROSS)
  override infix fun naturalJoin(joinTo: ColumnSet): Join = doJoin(joinTo, JoinType.NATURAL)

  private fun doJoin(
    otherTable: ColumnSet,
    joinType: JoinType,
    additionalConstraint: (() -> Op<Boolean>)? = null
  ): Join {
    val fkKeys = findKeys(this, otherTable) ?: findKeys(otherTable, this) ?: emptyList()
    return when {
      joinType.hasCondition && fkKeys.isEmpty() && additionalConstraint == null -> {
        error("Can't join $otherTable no matching primary/foreign key and constraint")
      }
      fkKeys.any { it.second.size > 1 } && additionalConstraint == null -> {
        val references = fkKeys.joinToString(" & ") { "${it.first} -> ${it.second.joinToString()}" }
        error("Can't join $otherTable multiple primary/foreign key references.\n$references")
      }
      else -> {
        doJoin(
          otherTable,
          joinType,
          fkKeys.filter { it.second.size == 1 }.map { it.first to it.second.single() },
          additionalConstraint
        )
      }
    }
  }

  private fun doJoin(
    joinTo: ColumnSet,
    joinType: JoinType,
    cond: List<JoinCondition>,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join = Join(columnSet).also {
    it._joinParts.addAll(this._joinParts)
    it._joinParts.add(JoinPart(joinType, joinTo, cond, additionalConstraint))
  }

  private fun findKeys(a: ColumnSet, b: ColumnSet): List<Pair<Column<*>, List<Column<*>>>>? =
    a.columns
      .map { a_pk -> a_pk to b.columns.filter { it.refersTo == a_pk } }
      .filter { it.second.isNotEmpty() }
      .takeIf { it.isNotEmpty() }

  internal class JoinPart(
    val joinType: JoinType,
    val joinPart: ColumnSet,
    private val conditions: List<JoinCondition>,
    private val additionalConstraint: (() -> Op<Boolean>)? = null
  ) {
    init {
      require(joinType.hasNoCondition || hasConditions() || hasAdditionalConstraint()) {
        "Missing join condition on $${this.joinPart}"
      }
    }

    fun appendConditions(builder: SqlBuilder) = builder.apply {
      conditions.appendTo(this, " AND ") { (pk, fk) ->
        append(pk)
        append(" = ")
        append(fk)
      }
      additionalConstraint?.let {
        if (hasConditions()) {
          append(" AND ")
        }
        append(" (")
        append((additionalConstraint)())
        append(")")
      }
    }

    private fun hasAdditionalConstraint() = additionalConstraint != null

    private fun hasConditions() = conditions.isNotEmpty()
  }

  companion object {
    operator fun invoke(
      table: ColumnSet,
      otherTable: ColumnSet,
      joinType: JoinType = JoinType.INNER,
      onColumn: Expression<*>? = null,
      otherColumn: Expression<*>? = null,
      additionalConstraint: (() -> Op<Boolean>)? = null
    ): Join {
      return Join(table).run {
        if (onColumn != null && otherColumn != null) {
          join(otherTable, joinType, onColumn, otherColumn, additionalConstraint)
        } else {
          doJoin(otherTable, joinType, additionalConstraint)
        }
      }
    }
  }
}
