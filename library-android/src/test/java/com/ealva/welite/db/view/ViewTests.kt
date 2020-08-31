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

package com.ealva.welite.db.view

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.ealva.welite.test.common.MediaFileTable
import com.ealva.welite.test.common.TestView
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class ViewTests {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test create view`() {
    val view = object : TestView(
      "FullMedia",
      { MediaFileTable.select(MediaFileTable.id).where(MediaFileTable.id eq 1) }
    ) {
      val mediaId = column("mediaFileId", MediaFileTable.id)
    }

    val ddl = view.sqlForTest()
    expect(ddl).toBe(
      """CREATE VIEW IF NOT EXISTS "FullMedia" (mediaFileId) AS SELECT """ +
        """"MediaFile"."_id" FROM "MediaFile" WHERE "MediaFile"."_id" = 1"""
    )
//    val account = object : TestTable(tableName) {
//      val id1: Column<Int> = integer(id1Name)
//      val id2: Column<Int> = integer(id2Name)
//
//      override val primaryKey = PrimaryKey(id1, id2)
//    }
//
//    val ddl = account.ddlForTest()
//    expect(ddl).toHaveSize(1)
//    val create = ddl.first()
//    expect(create).toBe(
//      """CREATE TABLE IF NOT EXISTS """" + tableName + """" (""" +
//        account.columns.joinToString { it.descriptionDdl() } + """, CONSTRAINT "pk_""" +
//        tableName + """" PRIMARY KEY ("""" + id1Name + """", """" + id2Name + """"))"""
//    )
  }
}
