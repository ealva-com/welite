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

public sealed class MasterType(public val value: String) {
  public object Table : MasterType("table")
  public object Index : MasterType("index")
  public object View : MasterType("view")
  public object Trigger : MasterType("trigger")
  public class Unknown(value: String) : MasterType(value)

  override fun toString(): String = value

  public companion object
}

private val KNOWN_TYPES =
  listOf(MasterType.Table, MasterType.Index, MasterType.View, MasterType.Trigger)
public val MasterType.Companion.knownTypes: List<MasterType>
  get() = KNOWN_TYPES

public fun String?.asMasterType(): MasterType {
  return KNOWN_TYPES.firstOrNull { type -> type.value.equals(this, ignoreCase = true) }
    ?: MasterType.Unknown(this ?: "NULL")
}

/**
 * The [SQLiteSchema] table contains one row for each table, index, view, and trigger (collectively
 * "objects") in the database schema, except there is no entry for the sqlite_schema table itself.
 * The sqlite_schema table contains entries for internal schema objects in addition to application-
 * and programmer-defined objects.
 *
 * This table is a system table and cannot be created or dropped.
 *
 * The underlying table is referred to as sqlite_schema, though current documentation references
 * sqlite_schema. sqlite_schema is supported and still used in some places within SQLite and
 * what was chosen to use here.
 *
 * [SQLite documentation](https://www.sqlite.org/schematab.html)
 */
public object SQLiteSchema : Table(name = "sqlite_master", systemTable = true) {
  /**
   * The sqlite_schema.type column will be one of the following text strings: 'table', 'index',
   * 'view', or 'trigger' according to the type of object defined. The 'table' string is used for
   * both ordinary and virtual tables.
   */
  public val type: Column<String> = text("type")

  /**
   * The sqlite_schema.name column will hold the name of the object. UNIQUE and PRIMARY KEY
   * constraints on tables cause SQLite to create internal indexes with names of the form
   * "sqlite_autoindex_TABLE_N" where TABLE is replaced by the name of the table that contains the
   * constraint and N is an integer beginning with 1 and increasing by one with each constraint seen
   * in the table definition. In a WITHOUT ROWID table, there is no sqlite_schema entry for the
   * PRIMARY KEY, but the "sqlite_autoindex_TABLE_N" name is set aside for the PRIMARY KEY as if the
   * sqlite_schema entry did exist. This will affect the numbering of subsequent UNIQUE constraints.
   * The "sqlite_autoindex_TABLE_N" name is never allocated for an INTEGER PRIMARY KEY, either in
   * rowid tables or WITHOUT ROWID tables.
   */
  public val name: Column<String> = text("name")

  /**
   * The sqlite_schema.tbl_name column holds the name of a table or view that the object is
   * associated with. For a table or view, the tbl_name column is a copy of the name column. For an
   * index, the tbl_name is the name of the table that is indexed. For a trigger, the tbl_name
   * column stores the name of the table or view that causes the trigger to fire.
   */
  public val tbl_name: Column<String> = text("tbl_name")

  /**
   * The sqlite_schema.rootpage column stores the page number of the root b-tree page for tables and
   * indexes. For rows that define views, triggers, and virtual tables, the rootpage column is 0 or
   * NULL.
   */
  @Suppress("unused")
  public val rootpage: Column<Int> = integer("rootpage")

  /**
   * The sqlite_schema.sql column stores SQL text that describes the object. This SQL text is a
   * CREATE TABLE, CREATE VIRTUAL TABLE, CREATE INDEX, CREATE VIEW, or CREATE TRIGGER statement that
   * if evaluated against the database file when it is the main database of a database connection
   * would recreate the object. The text is usually a copy of the original statement used to create
   * the object but with normalizations applied so that the text conforms to the following rules:
   *
   * * The CREATE, TABLE, VIEW, TRIGGER, and INDEX keywords at the beginning of the statement are
   * converted to all upper case letters.
   * * The TEMP or TEMPORARY keyword is removed if it occurs after the initial CREATE keyword.
   * * Any database name qualifier that occurs prior to the name of the object being created is
   * removed.
   * * Leading spaces are removed.
   * * All spaces following the first two keywords are converted into a single space.
   *
   * The text in the sqlite_schema.sql column is a copy of the original CREATE statement text that
   * created the object, except normalized as described above and as modified by subsequent ALTER
   * TABLE statements. The sqlite_schema.sql is NULL for the internal indexes that are automatically
   * created by UNIQUE or PRIMARY KEY constraints.
   */
  public val sql: Column<String> = text("sql")
}
