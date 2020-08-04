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
import com.ealva.welite.db.expr.bindString
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.sharedtest.AlbumTable
import com.ealva.welite.sharedtest.ArtistAlbumTable
import com.ealva.welite.sharedtest.ArtistTable
import com.ealva.welite.sharedtest.CoroutineRule
import com.ealva.welite.sharedtest.MediaFileTable
import com.ealva.welite.sharedtest.runBlockingTest
import com.ealva.welite.sharedtest.withTestDatabase
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
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
      testDispatcher = coroutineRule.testDispatcher
    ) {
      val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
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
          expect(cursor[MediaFileTable.mediaUri]).toBe(uri.toString())
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
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val song1Path =
          """/Music/Song1.mp3"""
        val uri1 = Uri.fromFile(File(song1Path))
        val song2Path =
          """/Music/Song2.mp3"""
        val uri2 = Uri.fromFile(File(song2Path))
        val song3Path =
          """/Music/Song3.mp3"""
        val uri3 = Uri.fromFile(File(song3Path))
        val ids1 = insertData("Led Zeppelin", "Houses of the Holy", uri1)
        val ids2 = insertData("Led Zeppelin", "Physical Graffiti", uri2)
        val ids3 = insertData("The Beatles", "Revolver", uri3)

        expect(ids1.first).toBe(ids2.first) // same artist ID, different albums

        val query = MediaFileTable.select(MediaFileTable.id, MediaFileTable.mediaUri)
          .where { MediaFileTable.id greater 0L }
          .build()

        expect(query.sql).toBe(
          """SELECT "MediaFile"."_id", "MediaFile"."MediaUri" FROM""" +
            """ "MediaFile" WHERE "MediaFile"."_id" > 0"""
        )

        var haveQueryResults = false
        query.forEach { cursor ->
          haveQueryResults = true
          expect(cursor.count).toBe(3)
          when (cursor.position) {
            0 -> {
              expect(cursor[MediaFileTable.mediaUri]).toBe("file://$song1Path")
            }
            1 -> {
              expect(cursor[MediaFileTable.mediaUri]).toBe("file://$song2Path")
            }
            2 -> {
              expect(cursor[MediaFileTable.mediaUri]).toBe("file://$song3Path")
            }
            else -> fail("Unexpected count=${cursor.count}")
          }
        }
        expect(haveQueryResults).toBe(true)

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
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val song1Path =
          """/Music/Song1.mp3"""
        val uri1 = Uri.fromFile(File(song1Path))
        val song2Path =
          """/Music/Song2.mp3"""
        val uri2 = Uri.fromFile(File(song2Path))
        val song3Path =
          """/Music/Song3.mp3"""
        val uri3 = Uri.fromFile(File(song3Path))
        val ids1 = insertData("Led Zeppelin", "Houses of the Holy", uri1)
        val ids2 = insertData("Led Zeppelin", "Physical Graffiti", uri2)
        val ids3 = insertData("The Beatles", "Revolver", uri3)

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
    uri: Uri
  ): Triple<Long, Long, Long> {

    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .sequence { cursor -> cursor[ArtistTable.id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq bindString() and (AlbumTable.artistName eq artist) }
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
      it[mediaUri] = uri.toString()
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}