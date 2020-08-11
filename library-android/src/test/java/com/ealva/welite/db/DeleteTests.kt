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
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.table.OnConflict
import com.ealva.welite.test.common.AlbumTable
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
class DeleteTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test delete`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      val (_, _, mediaId) = transaction {
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData("Led Zeppelin", "Houses of the Holy", uri).also { setSuccessful() }
      }

      transaction {
        expect(MediaFileTable.selectWhere(MediaFileTable.id eq mediaId).count()).toBe(1)
        expect(MediaFileTable.deleteWhere { MediaFileTable.id eq mediaId }.delete()).toBe(1)
        setSuccessful()
      }

      query { expect(MediaFileTable.selectWhere(MediaFileTable.id eq mediaId).count()).toBe(0) }
    }
  }

  private fun Transaction.insertData(
    artist: String,
    album: String,
    uri: Uri
  ): Triple<Long, Long, Long> {
    val idArtist: Long = ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .sequence { it[ArtistTable.id] }
      .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }

    println("artistId:$idArtist ${Thread.currentThread()}")

    val idAlbum: Long = AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq album }
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
      it[mediaUri] = uri.toString()
      it[artistId] = idArtist
      it[albumId] = idAlbum
    }
    return Triple(idArtist, idAlbum, mediaId)
  }
}
