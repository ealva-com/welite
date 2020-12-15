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

package com.ealva.welite.test.shared

import com.ealva.welite.db.Database
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Table
import com.nhaarman.expect.expect

public object MediaFileTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val mediaTitle: Column<String> = text("MediaTitle")
  public val mediaUri: Column<String> = text("MediaUri") { unique() }
  public val artistId: Column<Long> = reference("ArtistId", ArtistTable.id)
  public val albumId: Column<Long> = reference("AlbumId", AlbumTable.id)
}

public object ArtistTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val artistName: Column<String> = text("ArtistName") { collateNoCase().uniqueIndex() }
}

public object AlbumTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val albumName: Column<String> = text("AlbumName") { collateNoCase() }
  public val artistName: Column<String> = text("ArtistName") { collateNoCase() }

  init {
    uniqueIndex(albumName, artistName)
  }
}

public object ArtistAlbumTable : Table() {
  public val id: Column<Long> = long("_id") { primaryKey() }
  public val artistId: Column<Long> = reference("ArtistId", ArtistTable.id)
  public val albumId: Column<Long> = reference("AlbumId", AlbumTable.id)

  init {
    index(artistId)
    index(albumId)
    uniqueIndex(artistId, albumId)
  }
}

public object ArtistMediaTable : Table() {
  public val artistId: Column<Long> = reference("ArtistId", ArtistTable.id)
  public val mediaId: Column<Long> = reference("MediaIdId", MediaFileTable.id)
  override val primaryKey: PrimaryKey = PrimaryKey(artistId, mediaId)

  init {
    index(artistId)
    index(mediaId)
  }
}

public val MEDIA_TABLES: Set<Table> =
  setOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable, ArtistMediaTable)

public suspend fun Database.expectMediaTablesExist() {
  query {
    expect(MediaFileTable.exists).toBe(true)
    expect(AlbumTable.exists).toBe(true)
    expect(ArtistTable.exists).toBe(true)
    expect(ArtistAlbumTable.exists).toBe(true)
  }
}
