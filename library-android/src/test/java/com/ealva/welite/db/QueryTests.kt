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

package com.ealva.welite.db

import android.content.Context
import android.net.Uri
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.test.common.AlbumTable
import com.ealva.welite.test.common.AlbumTable.albumName
import com.ealva.welite.test.common.ArtistAlbumTable
import com.ealva.welite.test.common.ArtistTable
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.MediaFileTable
import com.ealva.welite.test.common.runBlockingTest
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
class QueryTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test simple query`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = true
    ) {
      val uri = "/Music/Song.mp3"
      val (_, _, mediaId) = transaction {
        insertData("Led Zeppelin", "Houses of the Holy", uri).also { setSuccessful() }
      }
      query {
        val query = MediaFileTable.select(MediaFileTable.id, MediaFileTable.mediaUri)
          .where { MediaFileTable.id greater 0L }
          .build()

        expect(query.sql).toBe(
          """SELECT "MediaFile"."_id", "MediaFile"."MediaUri" FROM""" +
            """ "MediaFile" WHERE "MediaFile"."_id" > 0"""
        )

        var count = 0
        query.forEach { cursor ->
          count++
          val id = cursor[MediaFileTable.id]
          expect(id).toBe(mediaId)
          expect(cursor[MediaFileTable.mediaUri]).toBe(uri)
        }
        expect(count).toBe(1)
      }
    }
  }

  @Suppress("UNUSED_VARIABLE")
  @Test
  fun `test query id greater than zero`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = true
    ) {
      transaction {
        val song1Path = "/Music/Song1.mp3"
        val song2Path = "/Music/Song2.mp3"
        val song3Path = "/Music/Song3.mp3"
        val ids1 = insertData("Led Zeppelin", "Houses of the Holy", song1Path)
        val ids2 = insertData("Led Zeppelin", "Physical Graffiti", song2Path)
        val ids3 = insertData("The Beatles", "Revolver", song3Path)

        expect(ids1.first).toBe(ids2.first) // same artist ID, different albums

        val query = MediaFileTable.select(MediaFileTable.id, MediaFileTable.mediaUri)
          .where { MediaFileTable.id greater 0L }
          .build()

        expect(query.sql).toBe(
          """SELECT "MediaFile"."_id", "MediaFile"."MediaUri" FROM""" +
            """ "MediaFile" WHERE "MediaFile"."_id" > 0"""
        )

        val results = mutableListOf<String>()
        query.forEach { cursor ->
          results.add(cursor[MediaFileTable.mediaUri])
        }

        expect(results.size).toBe(3)
        expect(results[0]).toBe(song1Path)
        expect(results[1]).toBe(song2Path)
        expect(results[2]).toBe(song3Path)

        expect(
          ArtistAlbumTable.select(ArtistAlbumTable.id)
            .where { ArtistAlbumTable.artistId eq ids1.first }
            .count()
        )
          .toBe(2)
        setSuccessful()
      }
    }
  }

  @Suppress("UNUSED_VARIABLE")
  @Test
  fun `test count`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher,
      enableForeignKeyConstraints = true
    ) {
      transaction {
        val song1Path = "/Music/Song1.mp3"
        val song2Path = "/Music/Song2.mp3"
        val song3Path = "/Music/Song3.mp3"
        val uri3 = Uri.fromFile(File(song3Path))
        val ids1 = insertData("Led Zeppelin", "Houses of the Holy", song1Path)
        val ids2 = insertData("Led Zeppelin", "Physical Graffiti", song2Path)
        val ids3 = insertData("The Beatles", "Revolver", song3Path)

        expect(
          ArtistAlbumTable.select(ArtistAlbumTable.id)
            .where { ArtistAlbumTable.artistId eq ids1.first }
            .count()
        ).toBe(2)
        setSuccessful()
      }
    }
  }

  private fun Transaction.insertData(
    artist: String,
    album: String,
    uri: String
  ): Triple<Long, Long, Long> {
    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .sequence { cursor -> cursor[ArtistTable.id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { albumName eq albumName.bindArg() and (AlbumTable.artistName eq artist) }
      .sequence({ it[0] = album }) { it[AlbumTable.id] }
      .singleOrNull() ?: AlbumTable.insert {
      it[albumName] = album
      it[artistName] = artist
    }

    ArtistAlbumTable.insert(OnConflict.Ignore) {
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }

    val mediaId = MediaFileTable.insert {
      it[mediaUri] = uri
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}
