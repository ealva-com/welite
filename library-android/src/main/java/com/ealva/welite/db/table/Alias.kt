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
import com.ealva.welite.db.expr.ExpressionBuilder
import com.ealva.welite.db.expr.Function
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.SqlTypeExpression
import com.ealva.welite.db.expr.invoke
import com.ealva.welite.db.type.PersistentType

@Suppress("NOTHING_TO_INLINE")
private inline operator fun Identity.plus(identity: Identity): Identity {
  return """${unquoted}_${identity.unquoted}""".asIdentity()
}

class Alias<out T : Table>(private val delegate: T, private val alias: String) : Table() {

  override val tableName: String get() = alias

  val tableNameWithAlias: String = "${delegate.tableName} AS $alias"

  override fun identity(): Identity {
    return delegate.identity() + Identity.make(
      alias
    )
  }

  private fun <T : Any?> Column<T>.clone(): Column<T> =
    Column(this@Alias, name, persistentType)

  fun <R> originalColumn(column: Column<R>): Column<R>? {
    @Suppress("UNCHECKED_CAST")
    return if (column.inTable(this))
      delegate.columns.first { column.name == it.name } as Column<R>
    else
      null
  }

  override val columns: List<Column<*>> = delegate.columns.map { it.clone() }

  override fun equals(other: Any?): Boolean {
    if (other !is Alias<*>) return false
    return this.tableNameWithAlias == other.tableNameWithAlias
  }

  override fun hashCode(): Int = tableNameWithAlias.hashCode()

  @Suppress("UNCHECKED_CAST")
  operator fun <T : Any?> get(original: Column<T>): Column<T> =
    delegate.columns.find { it == original }
      ?.let { it.clone() as? Column<T> }
      ?: error("Column not found in original table")
}

class ExpressionAlias<T>(val delegate: Expression<T>, val alias: String) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder { append(delegate).append(" $alias") }

  fun aliasOnlyExpression(): Expression<T> {
    return if (delegate is SqlTypeExpression<T>) {
      object : Function<T>(delegate.persistentType) {
        override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
          sqlBuilder { append(alias) }
      }
    } else {
      object : BaseExpression<T>() {
        override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
          sqlBuilder { append(alias) }
      }
    }
  }
}

class SqlTypeExpressionAlias<T>(
  val delegate: SqlTypeExpression<T>,
  val alias: String
) : BaseSqlTypeExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder { append(delegate).append(" $alias") }

  fun aliasOnlyExpression(): SqlTypeExpression<T> {
    return object : Function<T>(delegate.persistentType) {
      override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
        sqlBuilder { append(alias) }
    }
  }

  override val persistentType: PersistentType<T>
    get() = delegate.persistentType
}

class QueryBuilderAlias(
  private val queryBuilder: QueryBuilder,
  private val alias: String
) : ColumnSet {

  override fun appendTo(sqlBuilder: SqlBuilder) = sqlBuilder {
    append("(")
    queryBuilder.appendTo(this)
    append(") ")
    append(alias)
  }

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

  @ExperimentalUnsignedTypes
  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>?,
    otherColumn: Expression<*>?,
    additionalConstraint: (ExpressionBuilder.() -> Op<Boolean>)?
  ): Join =
    Join(
      this,
      joinTo,
      joinType,
      thisColumn,
      otherColumn,
      additionalConstraint
    )

  override infix fun innerJoin(joinTo: ColumnSet): Join =
    Join(
      this,
      joinTo,
      JoinType.INNER
    )

  override infix fun leftJoin(joinTo: ColumnSet): Join =
    Join(
      this,
      joinTo,
      JoinType.LEFT
    )

  override infix fun crossJoin(joinTo: ColumnSet): Join =
    Join(
      this,
      joinTo,
      JoinType.CROSS
    )

  override fun naturalJoin(joinTo: ColumnSet): Join =
    Join(
      this,
      joinTo,
      JoinType.NATURAL
    )

  private fun <T : Any?> Column<T>.clone(): Column<T> = makeAlias(alias)
}

fun <T : Table> T.alias(alias: String) =
  Alias(this, alias)

fun QueryBuilder.alias(alias: String) =
  QueryBuilderAlias(this, alias)

fun <T> SqlTypeExpression<T>.alias(alias: String) =
  SqlTypeExpressionAlias(this, alias)

fun Join.joinQuery(
  on: (ExpressionBuilder.(QueryBuilderAlias) -> Op<Boolean>),
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder
): Join {
  val qAlias = joinPart().alias("q${joinParts.count { it.joinPart is QueryBuilderAlias }}")
  return join(qAlias, joinType, additionalConstraint = { on(qAlias) })
}


fun Table.joinQuery(
  on: (ExpressionBuilder.(QueryBuilderAlias) -> Op<Boolean>),
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder
) = Join(this).joinQuery(on, joinType, joinPart)

val Join.lastQueryBuilderAlias: QueryBuilderAlias?
  get() = lastPartAsQueryBuilderAlias()

private fun Join.lastPartAsQueryBuilderAlias() =
  joinParts.map { it.joinPart as? QueryBuilderAlias }.firstOrNull()

fun <T : Any> wrapAsExpression(query: QueryBuilder) = object : BaseExpression<T?>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("(")
      query.appendTo(this)
      append(")")
    }
}
