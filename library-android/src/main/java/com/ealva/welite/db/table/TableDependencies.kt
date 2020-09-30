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

/**
 * Constructs a "sorted" list of Tables ordered by dependencies. If TableA depends on TableB,
 * TableB should be constructed first and appears earlier in the list.
 *
 * If [tablesAreCyclic] is true it indicates one or more cycles in Table dependencies, eg. TableA ->
 * TableB -> TableC -> TableA
 */
class TableDependencies(private val tables: List<Table>) {
  private val setOfAllTables: Set<Table>
    get() {
      return mutableSetOf<Table>().apply {
        fun parseTable(table: Table) {
          if (add(table)) table.columns.forEach {
            it.refersTo?.table?.let(::parseTable)
          }
        }
        tables.forEach(::parseTable)
      }
    }

  private val graph = setOfAllTables.associateWith { table ->
    table.columns.mapNotNull { column ->
      column.refersTo?.let { referent ->
        referent.table to column.persistentType.nullable
      }
    }.toMap()
  }

  val sortedTableList: List<Table> = ArrayList<Table>(tables.size).apply {
    val visited = mutableSetOf<Table>()

    fun traverse(tableToTravers: Table) {
      if (tableToTravers !in visited) {
        visited += tableToTravers
        graph.getValue(tableToTravers).forEach { (table, _) ->
          if (table !in visited) {
            traverse(table)
          }
        }
        this += tableToTravers
      }
    }
    tables.forEach(::traverse)
  }

  /**
   * Returns true if there is a cyclic dependency between tables
   */
  fun tablesAreCyclic(): Boolean {
    val visited = mutableSetOf<Table>()
    val recursion = mutableSetOf<Table>()

    val sortedTables = sortedTableList

    fun traverse(table: Table): Boolean {
      return if (table !in recursion) {
        if (table !in visited) {
          recursion += table
          visited += table
          (graph[table]?.any { traverse(it.key) } ?: false).also { if (!it) recursion -= table }
        } else true
      } else false
    }

    return sortedTables.any { traverse(it) }
  }
}
