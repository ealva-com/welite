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
import com.ealva.welite.db.type.SqlBuilder

fun exists(queryBuilder: QueryBuilder): Op<Boolean> = Exists(queryBuilder)

class Exists(private val queryBuilder: QueryBuilder) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("EXISTS (")
    this@Exists.queryBuilder.appendTo(this)
    append(")")
  }
}

fun notExists(queryBuilder: QueryBuilder): Op<Boolean> = NotExists(queryBuilder)

class NotExists(private val queryBuilder: QueryBuilder) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append("NOT EXISTS (")
    this@NotExists.queryBuilder.appendTo(this)
    append(")")
  }
}

infix fun <T> Expression<T>.inSubQuery(queryBuilder: QueryBuilder): Op<Boolean> =
  InSubQueryOp(this, queryBuilder)

class InSubQueryOp<T>(
  private val expr: Expression<T>,
  private val queryBuilder: QueryBuilder
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" IN (")
    this@InSubQueryOp.queryBuilder.appendTo(this)
    append(")")
  }
}

infix fun <T> Expression<T>.notInSubQuery(queryBuilder: QueryBuilder): Op<Boolean> =
  NotInSubQueryOp(this, queryBuilder)

class NotInSubQueryOp<T>(
  private val expr: Expression<T>,
  private val queryBuilder: QueryBuilder
) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder.apply {
    append(expr)
    append(" NOT IN (")
    this@NotInSubQueryOp.queryBuilder.appendTo(this)
    append(")")
  }
}
