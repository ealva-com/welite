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

package com.ealva.welite.db.schema

import com.ealva.welite.db.table.Table

sealed class MasterType(val value: String) {
  object Table : MasterType("table")
  object Index : MasterType("index")
  object View : MasterType("view")
  object Trigger : MasterType("trigger")
  class Unknown(value: String) : MasterType(value)

  override fun toString() = value

  companion object {
    val knownTypes = setOf(Table, Index, View, Trigger)
  }
}

fun String.asMasterType(): MasterType = when (this) {
  MasterType.Table.value -> MasterType.Table
  MasterType.Index.value -> MasterType.Index
  MasterType.Trigger.value -> MasterType.Trigger
  MasterType.View.value -> MasterType.View
  else -> MasterType.Unknown(this)
}

/**
 * The sqlite_master table contains one row for each table, index, view, and trigger (collectively
 * "objects") in the database schema, except there is no entry for the sqlite_master table itself.
 * The sqlite_master table contains entries for internal schema objects in addition to application-
 * and programmer-defined objects.
 */
@Suppress("ClassName")
object SQLiteMaster : Table(name = "sqlite_master", systemTable = true) {
  /**
   * The sqlite_master.type column will be one of the following text strings: 'table', 'index',
   * 'view', or 'trigger' according to the type of object defined. The 'table' string is used for
   * both ordinary and virtual tables.
   */
  val type = text("type")

  /**
   * The sqlite_master.name column will hold the name of the object. UNIQUE and PRIMARY KEY
   * constraints on tables cause SQLite to create internal indexes with names of the form
   * "sqlite_autoindex_TABLE_N" where TABLE is replaced by the name of the table that contains the
   * constraint and N is an integer beginning with 1 and increasing by one with each constraint seen
   * in the table definition. In a WITHOUT ROWID table, there is no sqlite_master entry for the
   * PRIMARY KEY, but the "sqlite_autoindex_TABLE_N" name is set aside for the PRIMARY KEY as if the
   * sqlite_master entry did exist. This will affect the numbering of subsequent UNIQUE constraints.
   * The "sqlite_autoindex_TABLE_N" name is never allocated for an INTEGER PRIMARY KEY, either in
   * rowid tables or WITHOUT ROWID tables.
   */
  val name = text("name")

  /**
   * The sqlite_master.tbl_name column holds the name of a table or view that the object is
   * associated with. For a table or view, the tbl_name column is a copy of the name column. For an
   * index, the tbl_name is the name of the table that is indexed. For a trigger, the tbl_name
   * column stores the name of the table or view that causes the trigger to fire.
   */
  val tbl_name = text("tbl_name")

  /**
   * The sqlite_master.rootpage column stores the page number of the root b-tree page for tables and
   * indexes. For rows that define views, triggers, and virtual tables, the rootpage column is 0 or
   * NULL.
   */
  @Suppress("unused")
  val rootpage = integer("rootpage")

  /**
   * The sqlite_master.sql column stores SQL text that describes the object. This SQL text is a
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
   * The text in the sqlite_master.sql column is a copy of the original CREATE statement text that
   * created the object, except normalized as described above and as modified by subsequent ALTER
   * TABLE statements. The sqlite_master.sql is NULL for the internal indexes that are automatically
   * created by UNIQUE or PRIMARY KEY constraints.
   */
  val sql = text("sql")

  override fun preCreate() {
    error(
      "Cannot create system table sqlite_master. " +
        "sqlite_master is available after database creation"
    )
  }
}
