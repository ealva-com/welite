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
import com.ealva.welite.db.expr.BaseSqlTypeExpression
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Function
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.SqlBuilder
import com.ealva.welite.db.type.asIdentity
import com.ealva.welite.db.type.buildStr

@Suppress("NOTHING_TO_INLINE")
private inline operator fun Identity.plus(identity: Identity): Identity {
  return buildStr {
    append(unquoted)
    append('_')
    append(identity.unquoted)
  }.asIdentity()
}

class Alias<out T : Table>(private val delegate: T, private val alias: String) : Table() {
  override val tableName: String get() = alias
  val tableNameWithAlias: String = "${delegate.tableName} AS $alias"
  override val identity: Identity = Identity.make(alias)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(tableNameWithAlias)
  }

  private fun <T : Any?> Column<T>.clone(): Column<T> = Column(this@Alias, name, persistentType)

  @Suppress("unused", "UNCHECKED_CAST")
  fun <R> originalColumn(column: Column<R>): Column<R>? =
    delegate.columns.firstOrNull { column.name == it.name } as Column<R>

  override val columns: List<Column<*>> = delegate.columns.map { it.clone() }

  override fun equals(other: Any?): Boolean =
    if (other !is Alias<*>) false else tableNameWithAlias == other.tableNameWithAlias

  override fun hashCode(): Int = tableNameWithAlias.hashCode()

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any?> get(original: Column<T>): Column<T> =
    delegate.columns.find { it == original }
      ?.let { it.clone() as? Column<T> }
      ?: error("Column not found in original table")
}

class ExpressionAlias<T>(val delegate: Expression<T>, val alias: String) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(delegate)
    append(" ")
    append(alias)
  }

  fun aliasOnlyExpression(): Expression<T> {
    return if (delegate is SqlTypeExpression<T>) {
      object : Function<T>(delegate.persistentType) {
        override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
          sqlBuilder.apply { append(alias) }
      }
    } else {
      object : BaseExpression<T>() {
        override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
          sqlBuilder.apply { append(alias) }
      }
    }
  }
}

class SqlTypeExpressionAlias<T>(
  val delegate: SqlTypeExpression<T>,
  val alias: String
) : BaseSqlTypeExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(delegate)
    append(" ")
    append(alias)
  }

  fun aliasOnlyExpression(): SqlTypeExpression<T> {
    return object : Function<T>(delegate.persistentType) {
      override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
        sqlBuilder.apply { append(alias) }
    }
  }

  override val persistentType: PersistentType<T>
    get() = delegate.persistentType
}

class QueryBuilderAlias(private val queryBuilder: QueryBuilder, val alias: String) : ColumnSet {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("(")
    append(queryBuilder)
    append(") ")
    append(alias)
  }

  override val identity: Identity = alias.asIdentity()

  override val columns: List<Column<*>>
    get() = queryBuilder.sourceSetColumnsInResult().map { it.clone() }

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any?> get(original: Column<T>): Column<T> =
    queryBuilder.findSourceSetOriginal(original)
      ?.clone() as? Column<T>
      ?: error("Column not found in original table")

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any?> get(original: SqlTypeExpression<T>): SqlTypeExpression<T> {
    val expressionAlias = queryBuilder.findResultColumnExpressionAlias(original)
      ?: error("Field not found in original table fields")

    return expressionAlias.delegate.alias("$alias.${expressionAlias.alias}").aliasOnlyExpression()
  }

  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>?,
    otherColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join = Join(this, joinTo, joinType, thisColumn, otherColumn, additionalConstraint)

  override infix fun innerJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.INNER)
  override infix fun leftJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.LEFT)
  override infix fun crossJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.CROSS)
  override fun naturalJoin(joinTo: ColumnSet): Join = Join(this, joinTo, JoinType.NATURAL)

  private fun <T : Any?> Column<T>.clone(): Column<T> = makeAlias(alias)
}

fun <T : Table> T.alias(alias: String) = Alias(this, alias)
fun QueryBuilder.alias(alias: String) = QueryBuilderAlias(this, alias)
fun <T> SqlTypeExpression<T>.alias(alias: String) = SqlTypeExpressionAlias(this, alias)

fun Join.joinQuery(
  on: ((QueryBuilderAlias) -> Op<Boolean>),
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder
): Join {
  val qAlias = joinPart().alias("q${joinParts.count { it.joinPart is QueryBuilderAlias }}")
  return join(qAlias, joinType, additionalConstraint = { on(qAlias) })
}

@Suppress("unused")
fun Table.joinQuery(
  on: (QueryBuilderAlias) -> Op<Boolean>,
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder
) = Join(this).joinQuery(on, joinType, joinPart)

val Join.lastQueryBuilderAlias: QueryBuilderAlias?
  get() = lastPartAsQueryBuilderAlias()

private fun Join.lastPartAsQueryBuilderAlias() = joinParts.map {
  it.joinPart as? QueryBuilderAlias
}.firstOrNull()
