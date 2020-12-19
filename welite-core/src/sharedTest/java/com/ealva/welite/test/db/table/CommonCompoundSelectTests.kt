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

package com.ealva.welite.test.db.table

import android.content.Context
import android.net.Uri
import com.ealva.welite.db.Database
import com.ealva.welite.db.Transaction
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.literal
import com.ealva.welite.db.statements.deleteWhere
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.all
import com.ealva.welite.db.table.asExpression
import com.ealva.welite.db.table.except
import com.ealva.welite.db.table.intersect
import com.ealva.welite.db.table.orderBy
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.selectCount
import com.ealva.welite.db.table.union
import com.ealva.welite.db.table.unionAll
import com.ealva.welite.db.table.where
import com.ealva.welite.db.type.buildStr
import com.ealva.welite.test.shared.AlbumTable
import com.ealva.welite.test.shared.ArtistAlbumTable
import com.ealva.welite.test.shared.ArtistMediaTable
import com.ealva.welite.test.shared.ArtistTable
import com.ealva.welite.test.shared.MEDIA_TABLES
import com.ealva.welite.test.shared.MediaFileTable
import com.ealva.welite.test.shared.expectMediaTablesExist
import com.ealva.welite.test.shared.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.CoroutineDispatcher
import java.io.File

public object CommonCompoundSelectTests {
  public suspend fun testUnionTwoSelects(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
      expectMediaTablesExist()
      transaction {
        val uri1 = Uri.fromFile(File("""/Music/Song.mp3"""))
        val uri2 = Uri.fromFile(File("""/Music/Song2.mp3"""))
        val (artist1, _, media1) = insertMediaData("Title 1", "Artist 1", "Album 1", uri1)
        val (artist2, _, media2) = insertMediaData("Title 2", "Artist 2", "Album 2", uri2)
        ArtistMediaTable.insert {
          it[artistId] = artist1
          it[mediaId] = media1
        }
        ArtistMediaTable.insert {
          it[artistId] = artist2
          it[mediaId] = media2
        }

        val union = ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
          artistId eq artist1
        } union MediaFileTable.select(MediaFileTable.id).where {
          artistId eq artist1
        }

        val expectedUnion: String = buildStr { append(union) }
        expect(expectedUnion).toBe(
          """(SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" WHERE """ +
            """"ArtistMedia"."ArtistId" = 1 UNION SELECT "MediaFile"."_id" FROM "MediaFile" """ +
            """WHERE "MediaFile"."ArtistId" = 1)"""
        )

        val selectCount = union.selectCount()
        val expectedQuery = buildStr { append(selectCount) }
        expect(expectedQuery).toBe(
          """SELECT COUNT(*) FROM (SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" """ +
            """WHERE "ArtistMedia"."ArtistId" = 1 UNION SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        expect(selectCount.longForQuery()).toBe(1)
      }
    }
  }

  public suspend fun testUnionAllTwoSelects(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
      expectMediaTablesExist()
      transaction {
        val uri1 = Uri.fromFile(File("""/Music/Song.mp3"""))
        val uri2 = Uri.fromFile(File("""/Music/Song2.mp3"""))
        val (artist1, _, media1) = insertMediaData("Title 1", "Artist 1", "Album 1", uri1)
        val (artist2, _, media2) = insertMediaData("Title 2", "Artist 2", "Album 2", uri2)
        ArtistMediaTable.insert {
          it[artistId] = artist1
          it[mediaId] = media1
        }
        ArtistMediaTable.insert {
          it[artistId] = artist2
          it[mediaId] = media2
        }

        val union = ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
          artistId eq artist1
        } unionAll MediaFileTable.select(MediaFileTable.id).where {
          artistId eq artist1
        }

        val expectedUnion: String = buildStr { append(union) }
        expect(expectedUnion).toBe(
          """(SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" WHERE """ +
            """"ArtistMedia"."ArtistId" = 1 UNION ALL SELECT "MediaFile"."_id" FROM""" +
            """ "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        val selectCount = union.selectCount()
        val expectedQuery = buildStr { append(selectCount) }
        expect(expectedQuery).toBe(
          """SELECT COUNT(*) FROM (SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" """ +
            """WHERE "ArtistMedia"."ArtistId" = 1 UNION ALL SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        expect(selectCount.longForQuery()).toBe(2)
      }
    }
  }

  public suspend fun testIntersectTwoSelects(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
      expectMediaTablesExist()
      transaction {
        val uri1 = Uri.fromFile(File("""/Music/Song.mp3"""))
        val uri2 = Uri.fromFile(File("""/Music/Song2.mp3"""))
        val (artist1, _, media1) = insertMediaData("Title 1", "Artist 1", "Album 1", uri1)
        val (artist2, _, media2) = insertMediaData("Title 2", "Artist 2", "Album 2", uri2)
        ArtistMediaTable.insert {
          it[artistId] = artist1
          it[mediaId] = media1
        }
        ArtistMediaTable.insert {
          it[artistId] = artist2
          it[mediaId] = media2
        }

        val union = ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
          artistId eq artist1
        } intersect MediaFileTable.select(MediaFileTable.id).where {
          artistId eq artist1
        }

        val expectedUnion: String = buildStr { append(union) }
        expect(expectedUnion).toBe(
          """(SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" WHERE """ +
            """"ArtistMedia"."ArtistId" = 1 INTERSECT SELECT "MediaFile"."_id" FROM""" +
            """ "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        val selectCount = union.selectCount()
        val expectedQuery = buildStr { append(selectCount) }
        expect(expectedQuery).toBe(
          """SELECT COUNT(*) FROM (SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" """ +
            """WHERE "ArtistMedia"."ArtistId" = 1 INTERSECT SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        expect(selectCount.longForQuery()).toBe(1L)
      }
    }
  }

  public suspend fun testExceptTwoSelects(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
      expectMediaTablesExist()
      transaction {
        val uri1 = Uri.fromFile(File("""/Music/Song.mp3"""))
        val uri2 = Uri.fromFile(File("""/Music/Song2.mp3"""))
        val (artist1, _, media1) = insertMediaData("Title 1", "Artist 1", "Album 1", uri1)
        val (artist2, _, media2) = insertMediaData("Title 2", "Artist 2", "Album 2", uri2)
        ArtistMediaTable.insert {
          it[artistId] = artist1
          it[mediaId] = media1
        }
        ArtistMediaTable.insert {
          it[artistId] = artist2
          it[mediaId] = media2
        }

        val union = ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
          artistId eq artist1
        } except MediaFileTable.select(MediaFileTable.id).where {
          artistId eq artist1
        }

        val expectedUnion: String = buildStr { append(union) }
        expect(expectedUnion).toBe(
          """(SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" WHERE """ +
            """"ArtistMedia"."ArtistId" = 1 EXCEPT SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        val selectCount = union.selectCount()
        val expectedQuery = buildStr { append(selectCount) }
        expect(expectedQuery).toBe(
          """SELECT COUNT(*) FROM (SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" """ +
            """WHERE "ArtistMedia"."ArtistId" = 1 EXCEPT SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = 1)"""
        )

        expect(selectCount.longForQuery()).toBe(0)
      }
    }
  }

  public suspend fun testDeleteArtistUnionCountZero(
    appCtx: Context,
    testDispatcher: CoroutineDispatcher
  ) {
    withTestDatabase(appCtx, MEDIA_TABLES, testDispatcher) {
      expectMediaTablesExist()
      val artistBob = "Bob the Artist"
      val bobId = transaction {
        val uri1 = Uri.fromFile(File("""/Music/Song.mp3"""))
        val uri2 = Uri.fromFile(File("""/Music/Song2.mp3"""))
        val (artist1, _, media1) = insertMediaData("Title 1", "Artist 1", "Album 1", uri1)
        val (artist2, _, media2) = insertMediaData("Title 2", "Artist 2", "Album 2", uri2)
        ArtistMediaTable.insert {
          it[artistId] = artist1
          it[mediaId] = media1
        }
        ArtistMediaTable.insert {
          it[artistId] = artist2
          it[mediaId] = media2
        }

        ArtistTable.insert {
          it[artistName] = artistBob
        }
      }
      query {
        expect(ArtistTable.selectCount { artistName eq artistBob }.longForQuery())
          .toBe(1)
        expect(ArtistTable.selectCount { id eq bobId }.longForQuery()).toBe(1)
      }
      transaction {
        // We can use ArtistTable.id in the expression because we're going to use it in a
        // deleteWhere on ArtistTable. Typically you would just put the expression directly
        // in deleteWhere { literal(0) eq countExpression } but we want to build a string
        val countExpression = (
          ArtistMediaTable.select(ArtistMediaTable.mediaId).where {
            artistId eq ArtistTable.id
          } union MediaFileTable.select(MediaFileTable.id).where {
            artistId eq ArtistTable.id
          }
          ).selectCount().asExpression<Long>()

        val expression = buildStr { append(countExpression) }
        expect(expression).toBe(
          """(SELECT COUNT(*) FROM (SELECT "ArtistMedia"."MediaIdId" FROM "ArtistMedia" """ +
            """WHERE "ArtistMedia"."ArtistId" = "Artist"."_id" UNION SELECT "MediaFile"."_id" """ +
            """FROM "MediaFile" WHERE "MediaFile"."ArtistId" = "Artist"."_id"))"""
        )

        val deleted = ArtistTable.deleteWhere { literal(0) eq countExpression }.delete()

        expect(deleted).toBe(1)
      }
      query {
        expect(ArtistTable.selectCount { artistName eq artistBob }.longForQuery())
          .toBe(0)
        expect(ArtistTable.selectCount { id eq bobId }.longForQuery())
          .toBe(0)
      }
    }
  }

  public suspend fun testSimpleUnion(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = TableA.select(TableA.id).all() union TableB.select(TableB.id).all()
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION SELECT""" +
            """ "TableB"."_id" FROM "TableB")"""
        )

        val unionList = compoundSelect
          .selectAll()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(3)
        expect(unionList).toBe(listOf(3, 2, 1))
      }
    }
  }

  public suspend fun testSimpleUnionAll(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = TableA.select(TableA.id).all() unionAll TableB.select(TableB.id).all()
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION ALL SELECT""" +
            """ "TableB"."_id" FROM "TableB")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(4)
        expect(unionList).toBe(listOf(1, 2, 2, 3))
      }
    }
  }

  public suspend fun testSimpleIntersect(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = TableA.select(TableA.id).all() intersect TableB.select(TableB.id).all()
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" INTERSECT SELECT""" +
            """ "TableB"."_id" FROM "TableB")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(1)
        expect(unionList).toBe(listOf(2))
      }
    }
  }

  public suspend fun testSimpleExcept(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = TableA.select(TableA.id).all() except TableB.select(TableB.id).all()
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" EXCEPT SELECT""" +
            """ "TableB"."_id" FROM "TableB")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(1)
        expect(unionList).toBe(listOf(1))
      }
    }
  }

  public suspend fun testUnionThree(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = (TableA.select(TableA.id).all() union TableB.select(TableB.id).all())
          .union(TableC.select(TableC.id).all())
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION SELECT""" +
            """ "TableB"."_id" FROM "TableB" UNION SELECT "TableC"."_id" FROM "TableC")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(4)
        expect(unionList).toBe(listOf(4, 3, 2, 1))
      }
    }
  }

  public suspend fun testUnionUnionAllThree(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = (TableA.select(TableA.id).all() union TableB.select(TableB.id).all())
          .unionAll(TableC.select(TableC.id).all())
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION SELECT""" +
            """ "TableB"."_id" FROM "TableB" UNION ALL SELECT "TableC"."_id" FROM "TableC")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(5)
        expect(unionList).toBe(listOf(4, 3, 3, 2, 1))
      }
    }
  }

  public suspend fun testUnionIntersectThree(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect = (TableA.select(TableA.id).all() union TableB.select(TableB.id).all())
          .intersect(TableC.select(TableC.id).all())
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION SELECT""" +
            """ "TableB"."_id" FROM "TableB" INTERSECT SELECT "TableC"."_id" FROM "TableC")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(1)
        expect(unionList).toBe(listOf(3))
      }
    }
  }

  public suspend fun testUnionAllExceptThree(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withTestDatabase(appCtx, unionTables, testDispatcher) {
      insertUnionData()

      query {
        val compoundSelect =
          (TableA.select(TableA.id).all() unionAll TableB.select(TableB.id).all())
            .except(TableC.select(TableC.id).all())
        val union = compoundSelect.selectAll()
        val unionString = buildStr { append(union) }
        expect(unionString).toBe(
          """SELECT "_id" FROM (SELECT "TableA"."_id" FROM "TableA" UNION ALL SELECT""" +
            """ "TableB"."_id" FROM "TableB" EXCEPT SELECT "TableC"."_id" FROM "TableC")"""
        )

        val unionList = compoundSelect
          .select(TableA.id)
          .all()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableA.id] }
          .toList()

        expect(unionList).toHaveSize(2)
        expect(unionList).toBe(listOf(2, 1))

        val cAllBExceptA = TableC.select(TableC.id).all()
          .unionAll(TableB.select(TableB.id).all())
          .except(TableA.select(TableA.id).all())
          .select(TableC.id)
          .all()
          .orderBy { id to SortOrder.DESC }
          .sequence { it[TableC.id] }
          .toList()

        expect(cAllBExceptA).toHaveSize(2)
        expect(cAllBExceptA).toBe(listOf(4, 3))
      }
    }
  }

  private fun Transaction.insertMediaData(
    title: String,
    artist: String,
    album: String,
    uri: Uri
  ): Triple<Long, Long, Long> {
    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { artistName eq artist }
      .sequence { it[ArtistTable.id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { albumName eq album }
      .sequence { it[AlbumTable.id] }
      .singleOrNull() ?: AlbumTable.insert {
      it[albumName] = album
      it[artistName] = artist
    }

    ArtistAlbumTable.insert(OnConflict.Ignore) {
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }

    val mediaId = MediaFileTable.insert {
      it[mediaTitle] = title
      it[mediaUri] = uri.toString()
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}

private val unionTables = setOf(TableA, TableB, TableC)

private object TableA : Table("TableA") {
  val id = integer("_id") { primaryKey() }
}

private object TableB : Table("TableB") {
  val id = integer("_id") { primaryKey() }
}

private object TableC : Table("TableC") {
  val id = integer("_id") { primaryKey() }
}

private suspend fun Database.insertUnionData() {
  transaction {
    expect(TableA.exists).toBe(true)
    expect(TableB.exists).toBe(true)

    TableA.insert { it[id] = 1 }
    TableA.insert { it[id] = 2 }

    TableB.insert { it[id] = 2 }
    TableB.insert { it[id] = 3 }

    TableC.insert { it[id] = 3 }
    TableC.insert { it[id] = 4 }
  }
}
