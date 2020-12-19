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
import com.ealva.welite.db.type.Identity
import com.ealva.welite.db.type.SqlBuilder

/**
 * This ColumnSet is used when there is no FROM clause in a SELECT. Instead of passing null into
 * a SelectFrom, indicating no FROM statement, we will use this type of Null Object Pattern as
 * a SELECT with a FROM is valid in SQLite and we want to use the QueryBuilder apparatus we already
 * have. Since QueryBuilder is parameterized on ColumnSet, a type of ColumnSet is required, hence
 * this ColumnSet specialization. It's possible this could have future uses other than indicating
 * no FROM in a SELECT.
 */
public class NoIdentityColumnSet : BaseColumnSet() {
  override val columns: List<Column<*>>
    get() = emptyList()
  override val identity: Identity
    get() = Identity.NO_IDENTITY

  override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder

  override fun appendFromTo(sqlBuilder: SqlBuilder): SqlBuilder = sqlBuilder

  override fun join(
    joinTo: ColumnSet,
    joinType: JoinType,
    thisColumn: Expression<*>?,
    otherColumn: Expression<*>?,
    additionalConstraint: (() -> Op<Boolean>)?
  ): Join {
    TODO("Not yet implemented")
  }

  override fun innerJoin(joinTo: ColumnSet): Join {
    TODO("Not yet implemented")
  }

  override fun leftJoin(joinTo: ColumnSet): Join {
    TODO("Not yet implemented")
  }

  override fun crossJoin(joinTo: ColumnSet): Join {
    TODO("Not yet implemented")
  }

  override fun naturalJoin(joinTo: ColumnSet): Join {
    TODO("Not yet implemented")
  }
}
