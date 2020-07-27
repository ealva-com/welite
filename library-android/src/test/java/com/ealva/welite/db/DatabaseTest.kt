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
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.schema.ColumnMetadata
import com.ealva.welite.db.schema.FieldType.BlobField
import com.ealva.welite.db.schema.FieldType.IntegerField
import com.ealva.welite.db.schema.FieldType.RealField
import com.ealva.welite.db.schema.FieldType.TextField
import com.nhaarman.expect.StringMatcher
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

object SomeMediaTable : TestTable() {
  val id = integer("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { notNull().unique() }
  val fileName = text("MediaFileName") { collateNoCase() }
  val mediaTitle = text("MediaTitle") { notNull().collateNoCase().default("Title") }
  val real = double("Real") { notNull() }
  val blob = blob("Blob")

  init {
    index(fileName, real)
  }
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
class DatabaseTest {
  @get:Rule var coroutineRule = MainCoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test sqlite version`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(SomeMediaTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        // supports "n.nn.nn"  nn is 1 or 2 numbers
        expect(sqliteVersion).matches(Regex("""[3-9]\.[0-9]{1,2}\.[0-9]{1,2}"""))

         expect(sqliteVersion).toBe("3.7.10") // fragile test
      }
    }
  }

  @Test
  fun createTableTest() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(SomeMediaTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        expect(SomeMediaTable.exists).toBe(true)
        val description = SomeMediaTable.description
        expect(description.tableName).toBe(SomeMediaTable.tableName)
        expect(description.columnsMetadata.size).toBe(6)
        expect(description.columnsMetadata[0]).toBe(
          ColumnMetadata(0, SomeMediaTable.id.name, IntegerField, true, "NULL", 1)
        )
        expect(description.columnsMetadata[1]).toBe(
          ColumnMetadata(1, SomeMediaTable.mediaUri.name, TextField, false, "NULL", 0)
        )
        expect(description.columnsMetadata[2]).toBe(
          ColumnMetadata(2, SomeMediaTable.fileName.name, TextField, true, "NULL", 0)
        )
        expect(description.columnsMetadata[3]).toBe(
          ColumnMetadata(3, SomeMediaTable.mediaTitle.name, TextField, false, "'Title'", 0)
        )
        expect(description.columnsMetadata[4]).toBe(
          ColumnMetadata(4, SomeMediaTable.real.name, RealField, false, "NULL", 0)
        )
        expect(description.columnsMetadata[5]).toBe(
          ColumnMetadata(5, SomeMediaTable.blob.name, BlobField, true, "NULL", 0)
        )

        val tableIdentity = SomeMediaTable.identity().value

        val fakeExecutor = FakeExecutor()
        SomeMediaTable.create(fakeExecutor)
        val statements = fakeExecutor.statements
        expect(statements).toHaveSize(2)
        expect(statements[0]).toBe("""CREATE TABLE IF NOT EXISTS $tableIdentity (${SomeMediaTable.columns.joinToString { it.descriptionDdl() }})""")
        expect(statements[1]).toBe("""CREATE INDEX IF NOT EXISTS "SomeMedia_MediaFileName_Real" ON "SomeMedia"("MediaFileName", "Real")""")

        val tableSql = SomeMediaTable.sql
        expect(tableSql.table.first()).toBe("""CREATE TABLE $tableIdentity ("_id" INTEGER PRIMARY KEY, "MediaUri" TEXT NOT NULL UNIQUE, "MediaFileName" TEXT COLLATE NOCASE, "MediaTitle" TEXT NOT NULL COLLATE NOCASE DEFAULT 'Title', "Real" REAL NOT NULL, "Blob" BLOB)""")
        expect(tableSql.indices.first()).toBe("""CREATE INDEX "SomeMedia_MediaFileName_Real" ON "SomeMedia"("MediaFileName", "Real")""")
      }
    }
  }

  @Test
  fun `create tables rearrange order for create`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(ArtistAlbumTable, MediaFileTable, ArtistTable, AlbumTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        expect(MediaFileTable.exists).toBe(true)
        expect(AlbumTable.exists).toBe(true)
        expect(ArtistTable.exists).toBe(true)
        expect(ArtistAlbumTable.exists).toBe(true)

        // This is coupled too tightly to the algorithm
        // We only care about table A before table B relationships, not specific ordering
        val tables = this@withTestDatabase.tables
        expect(tables[0]).toBe(ArtistTable)
        expect(tables[1]).toBe(AlbumTable)
        expect(tables[2]).toBe(ArtistAlbumTable)
        expect(tables[3]).toBe(MediaFileTable)
      }
    }
  }

  @Test
  fun `check integrity sunny day`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(ArtistAlbumTable, MediaFileTable, ArtistTable, AlbumTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        expect(MediaFileTable.exists).toBe(true)
        expect(AlbumTable.exists).toBe(true)
        expect(ArtistTable.exists).toBe(true)
        expect(ArtistAlbumTable.exists).toBe(true)

        val tegridy = tegridyCheck()
        expect(tegridy).toHaveSize(1)
        expect(tegridy[0]).toBe("ok")
      }
    }
  }

  @Test
  fun `create tables with foreign keys`() = coroutineRule.runBlockingTest {
    withTestDatabase(
      context = appCtx,
      tables = listOf(ArtistAlbumTable, MediaFileTable, ArtistTable, AlbumTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      query {
        expect(MediaFileTable.exists).toBe(true)
        expect(AlbumTable.exists).toBe(true)
        expect(ArtistTable.exists).toBe(true)
        expect(ArtistAlbumTable.exists).toBe(true)

        ArtistTable.sql.let { artistSql ->
          expect(artistSql.table).toHaveSize(1)
          expect(artistSql.table[0]).toBe("""CREATE TABLE "Artist" ("_id" INTEGER PRIMARY KEY, "ArtistName" TEXT NOT NULL COLLATE NOCASE)""")
          expect(artistSql.indices).toHaveSize(1)
          expect(artistSql.indices[0]).toBe("""CREATE UNIQUE INDEX "Artist_ArtistName_unique" ON "Artist"("ArtistName")""")
        }

        AlbumTable.sql.let { albumSql ->
          expect(albumSql.table).toHaveSize(1)
          expect(albumSql.table[0]).toBe("""CREATE TABLE "Album" ("_id" INTEGER PRIMARY KEY, "AlbumName" TEXT NOT NULL COLLATE NOCASE)""")
          expect(albumSql.indices).toHaveSize(1)
          expect(albumSql.indices[0]).toBe("""CREATE UNIQUE INDEX "Album_AlbumName_unique" ON "Album"("AlbumName")""")
        }

        ArtistAlbumTable.sql.let { artistAlbum ->
          expect(artistAlbum.table).toHaveSize(1)
          expect(artistAlbum.table[0]).toBe("""CREATE TABLE "ArtistAlbum" ("_id" INTEGER PRIMARY KEY, "ArtistId" INTEGER, "AlbumId" INTEGER, CONSTRAINT "fk_ArtistAlbum_ArtistId__id" FOREIGN KEY ("ArtistId") REFERENCES "Artist"("_id") ON DELETE CASCADE, CONSTRAINT "fk_ArtistAlbum_AlbumId__id" FOREIGN KEY ("AlbumId") REFERENCES "Album"("_id") ON DELETE CASCADE)""")
          expect(artistAlbum.indices).toHaveSize(3)
          expect(artistAlbum.indices[0]).toBe("""CREATE INDEX "ArtistAlbum_ArtistId" ON "ArtistAlbum"("ArtistId")""")
          expect(artistAlbum.indices[1]).toBe("""CREATE INDEX "ArtistAlbum_AlbumId" ON "ArtistAlbum"("AlbumId")""")
          expect(artistAlbum.indices[2]).toBe("""CREATE UNIQUE INDEX "ArtistAlbum_ArtistId_AlbumId_unique" ON "ArtistAlbum"("ArtistId", "AlbumId")""")
        }

        MediaFileTable.sql.let { mediaSql ->
          expect(mediaSql.table).toHaveSize(1)
          expect(mediaSql.table[0]).toBe("""CREATE TABLE "MediaFile" ("_id" INTEGER PRIMARY KEY, "MediaUri" TEXT NOT NULL UNIQUE, "ArtistId" INTEGER, "AlbumId" INTEGER, CONSTRAINT "fk_MediaFile_ArtistId__id" FOREIGN KEY ("ArtistId") REFERENCES "Artist"("_id"), CONSTRAINT "fk_MediaFile_AlbumId__id" FOREIGN KEY ("AlbumId") REFERENCES "Album"("_id"))""")
          expect(mediaSql.indices).toHaveSize(0)
        }
      }
    }
  }
}

private fun StringMatcher.matches(regex: Regex) {
  val value = actual ?: fail("Expected to match value against $regex but was null")
  if (!value.matches(regex)) fail("Expected `$value` to match $regex")
}

