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
import com.ealva.welite.db.type.SqlBuilder

public fun <C : ColumnSet> exists(queryBuilder: QueryBuilder<C>): Op<Boolean> = Exists(queryBuilder)

public class Exists<C : ColumnSet>(private val queryBuilder: QueryBuilder<C>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("EXISTS (")
    this@Exists.queryBuilder.appendTo(this)
    append(")")
  }
}

@Suppress("unused")
public fun <C : ColumnSet> notExists(queryBuilder: QueryBuilder<C>): Op<Boolean> =
  NotExists(queryBuilder)

public class NotExists<C : ColumnSet>(private val queryBuilder: QueryBuilder<C>) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("NOT EXISTS (")
    this@NotExists.queryBuilder.appendTo(this)
    append(")")
  }
}

public infix fun <C : ColumnSet, T> Expression<T>.inSubQuery(
  queryBuilder: QueryBuilder<C>
): Op<Boolean> =
  InSubQueryOp(this, queryBuilder)

public class InSubQueryOp<C : ColumnSet, T>(
  private val expr: Expression<T>,
  private val queryBuilder: QueryBuilder<C>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" IN (")
    this@InSubQueryOp.queryBuilder.appendTo(this)
    append(")")
  }
}

@Suppress("unused")
public infix fun <C : ColumnSet, T> Expression<T>.notInSubQuery(
  queryBuilder: QueryBuilder<C>
): Op<Boolean> =
  NotInSubQueryOp(this, queryBuilder)

public class NotInSubQueryOp<C : ColumnSet, T>(
  private val expr: Expression<T>,
  private val queryBuilder: QueryBuilder<C>
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" NOT IN (")
    this@NotInSubQueryOp.queryBuilder.appendTo(this)
    append(")")
  }
}
