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

package com.ealva.welite.sharedtest

import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table

/**
 * Expose some non-public items for easier testing
 */
abstract class TestTable(name: String = "") : Table(name) {
  fun ddlForTest() = ddl
}

object MediaFileTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { unique() }
  val artistId = long("ArtistId") { references(ArtistTable.id) }
  val albumId = long("AlbumId") { references(AlbumTable.id) }
}

object ArtistTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val artistName = text("ArtistName") { collateNoCase().uniqueIndex() }
}

object AlbumTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val albumName = text("AlbumName") { collateNoCase().uniqueIndex() }
}

object ArtistAlbumTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val artistId = long("ArtistId") { index().references(ArtistTable.id, ForeignKeyAction.CASCADE) }
  val albumId = long("AlbumId") { index().references(AlbumTable.id, ForeignKeyAction.CASCADE) }
  init {
    uniqueIndex(artistId, albumId)
  }
}

