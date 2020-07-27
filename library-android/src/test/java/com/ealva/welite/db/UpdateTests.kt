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
import com.ealva.welite.db.expr.ExpressionBuilder.eq
import com.ealva.welite.db.table.OnConflict
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
class UpdateTests {
  @get:Rule var coroutineRule = MainCoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test update`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(MediaFileTable, ArtistTable, AlbumTable, ArtistAlbumTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      val badName = "Led Zepelin"
      val goodName = "Led Zeppelin"
      transaction {
        val uri = Uri.fromFile(File("""/Music/Song.mp3"""))
        insertData(badName, "Houses of the Holy", uri)
        setSuccessful()
      }
      transaction {
        ArtistTable.update { it[ArtistTable.artistName] = goodName }
          .where { ArtistTable.artistName eq badName}
          .update()

        setSuccessful()
      }
      query {
        expect(ArtistTable.selectAllWhere(ArtistTable.artistName eq goodName).count()).toBe(1)
      }
    }
  }

  private fun Transaction.insertData(
    artist: String,
    album: String,
    uri: Uri
  ): Triple<Long, Long, Long> {
    var artistId: Long = 0
    ArtistTable.select(ArtistTable.id)
      .where { ArtistTable.artistName eq artist }
      .forEach {
        artistId = it[ArtistTable.id]
      }

    if (artistId == 0L) artistId = ArtistTable.insert { it[ArtistTable.artistName] = artist }

    var albumId: Long = 0
    AlbumTable.select(AlbumTable.id)
      .where { AlbumTable.albumName eq album }
      .forEach {
        albumId = it[AlbumTable.id]
      }

    if (albumId == 0L) albumId = AlbumTable.insert { it[AlbumTable.albumName] = album }

    ArtistAlbumTable.insert(OnConflict.Ignore) {
      it[ArtistAlbumTable.artistId] = artistId
      it[ArtistAlbumTable.albumId] = albumId
    }

    val mediaId = MediaFileTable.insert {
      it[MediaFileTable.mediaUri] = uri.toString()
      it[MediaFileTable.artistId] = artistId
      it[MediaFileTable.albumId] = albumId
    }
    return Triple(artistId, albumId, mediaId)
  }
}
