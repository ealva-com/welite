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

package com.ealva.welite.db.javatime

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.javatime.Visit.localDate
import com.ealva.welite.db.javatime.Visit.name
import com.ealva.welite.db.javatime.Visit.optLocalDate
import com.ealva.welite.db.javatime.Visit.other
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.TestTable
import com.ealva.welite.test.common.runBlockingTest
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class LocalDateColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test local data column`() {
    expect(localDate.descriptionDdl()).toBe(""""date" TEXT NOT NULL""")
    expect(optLocalDate.descriptionDdl()).toBe(""""opt_date" TEXT""")
  }

  @Test
  fun `test local date query`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher) {
      transaction {
        Visit.insert {
          it[localDate] = LocalDate.now()
          it[optLocalDate] = LocalDate.now().plusYears(1)
          it[name] = "Regency"
          it[other] = "Pool"
        }
        val actInsert = Visit.insertValues {
          it[localDate].bindParam()
          it[optLocalDate].bindParam()
          it[name].bindParam()
          it[other].bindParam()
        }
        actInsert.insert {
          it[0] = LocalDate.now()
          it[1] = LocalDate.now().plusYears(2)
          it[2] = "Motel 6"
          it[3] = "Vending Machine"
        }
        actInsert.insert {
          it[0] = LocalDate.now()
          it[1] = LocalDate.now().plusYears(3)
          it[2] = "The Greenbrier"
          it[3] = "Conference Room"
        }

        setSuccessful()
      }

      data class Accom(
        val date: LocalDate,
        val optDate: LocalDate?,
        val name: String,
        val other: String?
      )
      query {
        Visit.select()
          .where { optLocalDate greaterEq LocalDate.now().plusYears(2) }
          .orderBy(optLocalDate)
          .sequence { Accom(it[localDate], it[optLocalDate], it[name], it[other]) }
          .take(2)
          .forEachIndexed { i, visit ->
            when (i) {
              0 -> {
                expect(visit.name).toBe("Motel 6")
                expect(visit.optDate).toBe(LocalDate.now().plusYears(2))
              }
              1 -> {
                expect(visit.name).toBe("The Greenbrier")
                expect(visit.optDate).toBe(LocalDate.now().plusYears(3))
              }
              else -> fail("Too many results")
            }
          }
      }
    }
  }
}

private object Visit : TestTable() {
  val localDate = localDate("date")
  val optLocalDate = optLocalDate("opt_date")
  val name = text("name")
  val other = optText("other")
}
