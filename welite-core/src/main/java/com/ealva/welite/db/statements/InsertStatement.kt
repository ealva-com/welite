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

package com.ealva.welite.db.statements

import android.database.sqlite.SQLiteDatabase
import com.ealva.welite.db.expr.appendTo
import com.ealva.welite.db.table.ArgBindings
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.WeLiteMarker
import com.ealva.welite.db.type.PersistentType
import com.ealva.welite.db.type.StatementSeed
import com.ealva.welite.db.type.buildSql

/**
 * Represents a compiled SQLiteStatement with bind parameters that can be used to repeatedly insert
 * a row binding any necessary arguments during execution.
 */
@WeLiteMarker
public interface InsertStatement : Statement {

  public companion object {
    /**
     * Make an InsertStatement for the given [Table]. The statement may be saved and reused for
     * multiple inserts.
     */
    public operator fun <T : Table> invoke(
      table: T,
      onConflict: OnConflict = OnConflict.Unspecified,
      assignColumns: T.(ColumnValues) -> Unit
    ): InsertStatement = InsertStatementImpl(statementSeed(table, onConflict, assignColumns))

    public fun <T : Table> statementSeed(
      table: T,
      onConflict: OnConflict,
      assignColumns: T.(ColumnValues) -> Unit
    ): StatementSeed = buildSql {
      append(onConflict.insertOr)
      append(" INTO ")
      append(table.identity.value)
      ColumnValues().apply { table.assignColumns(this) }.columnValueList.let { list ->
        list.appendTo(this, prefix = " (", postfix = ") ") { columnValue ->
          appendName(columnValue)
        }
        append("VALUES")
        list.appendTo(this, prefix = " (", postfix = ")") { columnValue ->
          appendValue(columnValue)
        }
      }
    }
  }
}

/**
 * Makes an InsertStatement into which arguments can be bound for insertion.
 *
 *```
 * db.transaction {
 *   with(MediaFileTable.insertValues()) {
 *     insert {
 *       it[mediaUri] = "/dir/Music/file.mpg"
 *       it[fileName] = "filename"
 *       it[mediaTitle] = "title"
 *     }
 *     insert {
 *       it[mediaUri] = "/dir/Music/anotherFile.mpg"
 *       it[fileName] = "2nd file"
 *       it[mediaTitle] = "another title"
 *     }
 *   }
 * }
 * ```
 * [SQLite INSERT](https://sqlite.org/lang_insert.html)
 * @see InsertStatement
 */
public fun <T : Table> T.insertValues(
  onConflict: OnConflict = OnConflict.Unspecified,
  assignColumns: T.(ColumnValues) -> Unit
): InsertStatement {
  return InsertStatement(this, onConflict, assignColumns)
}

private class InsertStatementImpl(
  private val seed: StatementSeed
) : BaseStatement(), InsertStatement {
  override val sql: String
    get() = seed.sql

  override val types: List<PersistentType<*>>
    get() = seed.types

  override fun execute(db: SQLiteDatabase, bindArgs: (ArgBindings) -> Unit): Long =
    getStatementAndTypes(db).executeInsert(bindArgs)
}
