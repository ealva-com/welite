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
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.invoke

class Exists(private val queryBuilder: QueryBuilder) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("EXISTS (")
      this@Exists.queryBuilder.appendTo(this)
      append(")")
    }
}

class NotExists(private val queryBuilder: QueryBuilder) : Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append("NOT EXISTS (")
      this@NotExists.queryBuilder.appendTo(this)
      append(")")
    }
}

class InSubQueryOp<T>(private val expr: Expression<T>, private val queryBuilder: QueryBuilder) :
  Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append(expr)
      append(" IN (")
      this@InSubQueryOp.queryBuilder.appendTo(this)
      append(")")
    }
}

class NotInSubQueryOp<T>(private val expr: Expression<T>, private val queryBuilder: QueryBuilder) :
  Op<Boolean>() {
  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
    sqlBuilder {
      append(expr)
      append(" NOT IN (")
      this@NotInSubQueryOp.queryBuilder.appendTo(this)
      append(")")
    }
}
