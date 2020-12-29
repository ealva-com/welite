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
 * Refer to [SQLite sqlite_sequence](https://sqlite.org/fileformat2.html#seqtab) for up-to-date
 * reference.
 *
 * The sqlite_sequence table is an internal table used to help implement AUTOINCREMENT. The
 * sqlite_sequence table is created automatically whenever any ordinary table with an AUTOINCREMENT
 * integer primary key is created. Once created, the sqlite_sequence table exists in the
 * sqlite_schema table forever; it cannot be dropped. The schema for the sqlite_sequence table is:
 *
 * CREATE TABLE sqlite_sequence(name,seq);
 *
 * There is a single row in the sqlite_sequence table for each ordinary table that uses
 * AUTOINCREMENT. The name of the table (as it appears in sqlite_schema.name) is in the
 * sqlite_sequence.main field and the largest INTEGER PRIMARY KEY ever inserted into that table is
 * in the sqlite_sequence.seq field. New automatically generated integer primary keys for
 * AUTOINCREMENT tables are guaranteed to be larger than the sqlite_sequence.seq field for that
 * table. If the sqlite_sequence.seq field of an AUTOINCREMENT table is already at the largest
 * integer value (9223372036854775807) then attempts to add new rows to that table with an
 * automatically generated integer primary will fail with an SQLITE_FULL error. The
 * sqlite_sequence.seq field is automatically updated if required when new entries are inserted to
 * an AUTOINCREMENT table. The sqlite_sequence row for an AUTOINCREMENT table is automatically
 * deleted when the table is dropped. If the sqlite_sequence row for an AUTOINCREMENT table does not
 * exist when the AUTOINCREMENT table is updated, then a new sqlite_sequence row is created. If the
 * sqlite_sequence.seq value for an AUTOINCREMENT table is manually set to something other than an
 * integer and there is a subsequent attempt to insert the or update the AUTOINCREMENT table, then
 * the behavior is undefined.
 *
 * Application code is allowed to modify the sqlite_sequence table, to add new rows, to delete rows,
 * or to modify existing rows. However, application code cannot create the sqlite_sequence table if
 * it does not already exist. Application code can delete all entries from the sqlite_sequence
 * table, but application code cannot drop the sqlite_sequence table.
 */
public object SQLiteSequence : Table(name = "sqlite_sequence", systemTable = true) {
  public val name: Column<String> = text("name")
  public val seq: Column<Long> = long("seq")

  override fun preCreate() {
    error("Cannot create system table sqlite_sequence. It is available after database creation")
  }
}
