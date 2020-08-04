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

import com.ealva.welite.db.expr.SqlTypeExpression

/**
 * A SelectFrom is a subset of columns from a ColumnSet, [resultColumns]s, which are the
 * fields to be read in a query. The columns know how to read themselves from the underlying DB
 * layer. Also contains the [sourceSet] which is the ```FROM``` part of a query.
 */
interface SelectFrom {
  /** Result columns as they appear in a Select */
  val resultColumns: List<SqlTypeExpression<*>>

  /** Represents the ```FROM``` clause of a query */
  val sourceSet: ColumnSet

  companion object {
    operator fun invoke(
      resultColumns: List<SqlTypeExpression<*>>,
      sourceSet: ColumnSet
    ): SelectFrom = SelectFromImpl(resultColumns, sourceSet)
  }
}

private data class SelectFromImpl(
  override val resultColumns: List<SqlTypeExpression<*>>,
  override val sourceSet: ColumnSet
) : SelectFrom
