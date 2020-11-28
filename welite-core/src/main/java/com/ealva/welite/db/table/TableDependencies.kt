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
public class TableDependencies(private val tables: List<Table>) {
  private val setOfAllTables: Set<Table> = mutableSetOf<Table>().apply {
    fun parseTable(table: Table) {
      if (add(table)) table.columns.forEach {
        it.refersTo?.table?.let(::parseTable)
      }
    }
    tables.forEach(::parseTable)
  }

  public val sortedTableList: List<Table> = ArrayList<Table>(tables.size).apply {
    val graph = setOfAllTables.associateWith { table ->
      table.columns.mapNotNull { column ->
        column.refersTo?.let { referent ->
          referent.table to column.persistentType.nullable
        }
      }.toMap()
    }

    val visited = mutableSetOf<Table>()

    fun traverse(tableToTraverse: Table) {
      if (tableToTraverse !in visited) {
        visited += tableToTraverse
        graph.getValue(tableToTraverse).forEach { (table, _) ->
          if (table !in visited) {
            traverse(table)
          }
        }
        add(tableToTraverse)
      }
    }
    tables.forEach(::traverse)
  }

  /**
   * Returns true if there is a cyclic dependency between tables
   */
  public fun tablesAreCyclic(): Boolean {
    val graph = Graph()
    val vertexMap = mutableMapOf<Table, Vertex>().apply {
      sortedTableList.forEach { table ->
        Vertex(table).let { vertex ->
          put(table, vertex)
          graph.addVertex(vertex)
        }
      }
    }
    sortedTableList.forEach { table ->
      table.columns.forEach { col ->
        col.refersTo?.let { referent ->
          graph.addEdge(
            checkNotNull(vertexMap[table]) { "No vertex for $table" },
            checkNotNull(vertexMap[referent.table]) { "No vertex for ${referent.table}"}
          )
        }
      }
    }
    return graph.hasCycle()
  }
}

private data class Vertex(val table: Table) {
  var isVisited = false
  var isBeingVisited = false
  var adjacencyList = mutableListOf<Vertex>()

  fun addNeighbour(adjacent: Vertex) {
    adjacencyList.add(adjacent)
  }
}

private class Graph {
  private var vertices = mutableListOf<Vertex>()
  fun addVertex(vertex: Vertex) {
    vertices.add(vertex)
  }

  fun addEdge(from: Vertex, to: Vertex) = from.addNeighbour(to)

  fun hasCycle(): Boolean {
    vertices.forEach { vertex ->
      if (!vertex.isVisited && hasCycle(vertex)) return true
    }
    return false
  }

  @Suppress("ReturnCount")
  fun hasCycle(sourceVertex: Vertex): Boolean {
    sourceVertex.isBeingVisited = true
    sourceVertex.adjacencyList.forEach { neighbour ->
      if (neighbour.isBeingVisited || (!neighbour.isVisited && hasCycle(neighbour))) {
        return true
      }
    }
    sourceVertex.isBeingVisited = false
    sourceVertex.isVisited = true
    return false
  }
}
