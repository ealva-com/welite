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

public class Alias<out T : Table>(
  private val delegate: T,
  private val alias: String,
  forceQuoteName: Boolean
) : Table() {
  override val tableName: String get() = alias
  public val tableNameWithAlias: String = "${delegate.tableName} AS $alias"
  override val identity: Identity = Identity.make(alias, forceQuoteName)

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(tableNameWithAlias)
  }

  private fun <T : Any?> Column<T>.clone(): Column<T> = Column(name, this@Alias, persistentType)

  @Suppress("unused", "UNCHECKED_CAST")
  public fun <R> originalColumn(column: Column<R>): Column<R>? =
    delegate.columns.firstOrNull { column.name == it.name } as? Column<R>

  override val columns: List<Column<*>> = delegate.cloneColumnsFor(this)

  override fun equals(other: Any?): Boolean =
    if (other !is Alias<*>) false else tableNameWithAlias == other.tableNameWithAlias

  override fun hashCode(): Int = tableNameWithAlias.hashCode()

  @Suppress("UNCHECKED_CAST")
  public operator fun <T : Any?> get(original: Column<T>): Column<T> =
    delegate.find(original)
      ?.let { it.clone() as? Column<T> }
      ?: error("Column not found in original table")
}

public class ExpressionAlias<T>(
  public val original: Expression<T>,
  public val alias: String
) : BaseExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(original)
    append(" ")
    append(alias)
  }

  override fun aliasOrSelf(): Expression<T> {
    return if (original is SqlTypeExpression<T>) {
      object : Function<T>(original.persistentType) {
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

  override fun originalOrSelf(): Expression<T> = original
}

public class SqlTypeExpressionAlias<T>(
  public val original: SqlTypeExpression<T>,
  public val alias: String
) : BaseSqlTypeExpression<T>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(original)
    append(" ")
    append(alias)
  }

  override fun aliasOrSelf(): SqlTypeExpression<T> {
    return object : Function<T>(original.persistentType) {
      override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
        sqlBuilder.apply { append(alias) }
    }
  }

  override fun originalOrSelf(): Expression<T> = original

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    if (!super.equals(other)) return false

    other as SqlTypeExpressionAlias<*>

    if (original != other.original) return false
    if (alias != other.alias) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + original.hashCode()
    result = 31 * result + alias.hashCode()
    return result
  }

  override val persistentType: PersistentType<T>
    get() = original.persistentType
}

public class QueryBuilderAlias<C : ColumnSet>(
  private val queryBuilder: QueryBuilder<C>,
  public val alias: String,
  forceQuoteName: Boolean
) : BaseColumnSet() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("(")
    append(queryBuilder)
    append(") ")
    append(alias)
  }

  override val identity: Identity = alias.asIdentity(forceQuoteName)

  override val columns: List<Column<*>>
    get() = queryBuilder.sourceSetColumnsInResult().map { it.makeAlias(alias,) }

  @Suppress("UNCHECKED_CAST")
  public operator fun <T : Any?> get(original: Column<T>): Column<T> =
    queryBuilder.findSourceSetOriginal(original)
      ?.makeAlias(alias,) as? Column<T>
      ?: error("Column not found in original table")

  @Suppress("UNCHECKED_CAST")
  public operator fun <T : Any?> get(original: SqlTypeExpression<T>): SqlTypeExpression<T> {
    val expressionAlias = queryBuilder.findResultColumnExpressionAlias(original)
      ?: error("Field not found in original table fields")

    return expressionAlias.original.alias("$alias.${expressionAlias.alias}").aliasOrSelf()
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

public fun <T : Table> T.alias(alias: String, forceQuoteName: Boolean = false): Alias<T> =
  Alias(this, alias, forceQuoteName)

public fun <C : ColumnSet> QueryBuilder<C>.alias(
  alias: String,
  forceQuoteName: Boolean = false
): QueryBuilderAlias<C> = QueryBuilderAlias(this, alias, forceQuoteName)

public fun <T> Expression<T>.alias(alias: String): ExpressionAlias<T> =
  ExpressionAlias(this, alias)

public fun <T> SqlTypeExpression<T>.alias(alias: String): SqlTypeExpressionAlias<T> =
  SqlTypeExpressionAlias(this, alias)

public fun <C : ColumnSet> Join.joinQuery(
  on: ((QueryBuilderAlias<C>) -> Op<Boolean>),
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder<C>
): Join {
  val qAlias = joinPart().alias("q${parts.count { it.joinPart is QueryBuilderAlias<*> }}")
  return join(joinTo = qAlias, joinType = joinType) { on(qAlias) }
}

@Suppress("unused")
public fun Table.joinQuery(
  on: (QueryBuilderAlias<Table>) -> Op<Boolean>,
  joinType: JoinType = JoinType.INNER,
  joinPart: () -> QueryBuilder<Table>
): Join = Join(this).joinQuery(on, joinType, joinPart)

public val Join.lastQueryBuilderAlias: QueryBuilderAlias<*>?
  get() = lastPartAsQueryBuilderAlias()

private fun Join.lastPartAsQueryBuilderAlias() = parts.map {
  it.joinPart as? QueryBuilderAlias<*>
}.firstOrNull()
