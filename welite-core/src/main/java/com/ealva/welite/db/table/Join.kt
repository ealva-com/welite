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
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder

public typealias JoinCondition = Pair<Expression<*>, Expression<*>>

public enum class JoinType(public val hasCondition: Boolean) {
  INNER(true),
  LEFT(true),
  CROSS(false),
  NATURAL(false)
}

public inline val JoinType.hasNoCondition: Boolean
  get() = !hasCondition

public fun <C1 : ColumnSet, C2 : ColumnSet> C1.innerJoin(
  column: C1.() -> Expression<*>,
  joinTo: C2,
  joinToColumn: C2.() -> Expression<*>
): Join = join(joinTo, JoinType.INNER, column(), joinTo.joinToColumn())

@Suppress("unused")
public fun <C1 : ColumnSet, C2 : ColumnSet> C1.leftJoin(
  column: C1.() -> Expression<*>,
  joinTo: C2,
  joinToColumn: C2.() -> Expression<*>
): Join = join(joinTo, JoinType.LEFT, column(), joinTo.joinToColumn())

public fun <C1 : ColumnSet, C2 : ColumnSet> C1.crossJoin(
  column: C1.() -> Expression<*>,
  other: C2,
  otherColumn: C2.() -> Expression<*>
): Join = join(other, JoinType.CROSS, column(), other.otherColumn())

@Suppress("unused")
public fun <C1 : ColumnSet, C2 : ColumnSet> C1.naturalJoin(
  column: C1.() -> Expression<*>,
  joinTo: C2,
  joinToColumn: C2.() -> Expression<*>
): Join = join(joinTo, JoinType.NATURAL, column(), joinTo.joinToColumn())

public class Join(private val columnSet: ColumnSet) : BaseColumnSet() {

  override val identity: Identity = columnSet.identity

  override val columns: List<Column<*>>
    get() = _parts.flatMapTo(columnSet.columns.toMutableList()) { it.joinPart.columns }

  private val _parts: MutableList<JoinPart> = mutableListOf()
  internal val parts: List<JoinPart>
    get() = _parts.toList()

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    columnSet.appendTo(this)
    _parts.forEach { p ->
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
  ): List<JoinCondition> = if (onColumn == null || otherColumn == null) {
    emptyList()
  } else {
    listOf(JoinCondition(onColumn, otherColumn))
  }

  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    column: Expression<*>?,
    joinToColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join {
    return doJoin(joinTo, joinType, asJoinCondition(column, joinToColumn), additionalConstraint)
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
  ): Join = Join(columnSet).also { newJoin ->
    newJoin._parts.addAll(this._parts)
    newJoin._parts.add(JoinPart(joinType, joinTo, cond, additionalConstraint))
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

  public companion object {
    /**
     * Make a Join [from]/[fromColumn] to [joinTo]/[joinToColumn] of type [joinType], which defaults
     * to [JoinType.INNER], adding any [additionalConstraint]
     */
    public operator fun invoke(
      from: ColumnSet,
      joinTo: ColumnSet,
      fromColumn: Expression<*>? = null,
      joinToColumn: Expression<*>? = null,
      joinType: JoinType = JoinType.INNER,
      additionalConstraint: (() -> Op<Boolean>)? = null
    ): Join {
      return Join(from).run {
        if (fromColumn != null && joinToColumn != null) {
          join(joinTo, joinType, fromColumn, joinToColumn, additionalConstraint)
        } else {
          doJoin(joinTo, joinType, additionalConstraint)
        }
      }
    }
  }
}
