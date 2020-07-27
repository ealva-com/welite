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

package com.ealva.welite.db.android

import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table

object MediaFileTable : Table() {
  val id = long("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { notNull().unique() }
  val artistId = long("ArtistId") { references(ArtistTable.id) }
  val albumId = long("AlbumId") { references(AlbumTable.id) }
}

object ArtistTable : Table() {
  val id = long("_id") { primaryKey() }
  val artistName = text("ArtistName") { notNull().collateNoCase().uniqueIndex() }
}

object AlbumTable : Table() {
  val id = long("_id") { primaryKey() }
  val albumName = text("AlbumName") { notNull().collateNoCase().uniqueIndex() }
}

object ArtistAlbumTable : Table() {
  val id = long("_id") { primaryKey() }
  val artistId = long("ArtistId") { index().references(ArtistTable.id, ForeignKeyAction.CASCADE) }
  val albumId = long("AlbumId") { index().references(AlbumTable.id, ForeignKeyAction.CASCADE) }
  init {
    uniqueIndex(artistId, albumId)
  }
}
