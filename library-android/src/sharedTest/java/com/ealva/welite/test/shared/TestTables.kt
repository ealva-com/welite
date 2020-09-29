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

package com.ealva.welite.test.shared

import com.ealva.welite.db.table.Table

object MediaFileTable : Table() {
  val id = long("_id") { primaryKey() }
  val mediaTitle = text("MediaTitle")
  val mediaUri = text("MediaUri") { unique() }
  val artistId = reference("ArtistId", ArtistTable.id)
  val albumId = reference("AlbumId", AlbumTable.id)
}

object ArtistTable : Table() {
  val id = long("_id") { primaryKey() }
  val artistName = text("ArtistName") { collateNoCase().uniqueIndex() }
}

object AlbumTable : Table() {
  val id = long("_id") { primaryKey() }
  val albumName = text("AlbumName") { collateNoCase() }
  val artistName = text("ArtistName") { collateNoCase() }

  init {
    uniqueIndex(albumName, artistName)
  }
}

object ArtistAlbumTable : Table() {
  val id = long("_id") { primaryKey() }
  val artistId = reference("ArtistId", ArtistTable.id)
  val albumId = reference("AlbumId", AlbumTable.id)

  init {
    index(artistId)
    index(albumId)
    uniqueIndex(artistId, albumId)
  }
}
