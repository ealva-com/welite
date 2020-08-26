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
import com.ealva.welite.db.ForeignKeyInfo
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.javatime.VisitTime.localDate
import com.ealva.welite.db.javatime.VisitTime.name
import com.ealva.welite.db.javatime.VisitTime.optLocalDate
import com.ealva.welite.db.javatime.VisitTime.other
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.test.common.CoroutineRule
import com.ealva.welite.test.common.TestTable
import com.ealva.welite.test.common.runBlockingTest
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers.isA
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeParseException
import java.util.Date

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class LocalDateTimeColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule var coroutineRule = CoroutineRule()
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test local date time column`() {
    expect(localDate.descriptionDdl()).toBe(""""date" TEXT NOT NULL""")
    expect(optLocalDate.descriptionDdl()).toBe(""""opt_date" TEXT""")
  }

  @Test
  fun `test local date time query`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(VisitTime), coroutineRule.testDispatcher, true) {
      val noon = LocalDateTime.of(LocalDate.now(), LocalTime.NOON)

      transaction {
        val now = LocalDateTime.now()

        VisitTime.insert {
          it[localDate] = now
          it[optLocalDate] = noon.plusYears(1)
          it[name] = "Regency"
          it[other] = "Pool"
        }
        val actInsert = VisitTime.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name].bindArg()
          it[other].bindArg()
        }
        actInsert.insert {
          it[0] = LocalDateTime.now()
          it[1] = noon.plusYears(2)
          it[2] = "Motel 6"
          it[3] = "Vending Machine"
        }
        actInsert.insert {
          it[0] = LocalDateTime.now()
          it[1] = noon.plusYears(3)
          it[2] = "The Greenbrier"
          it[3] = "Conference Room"
        }

        setSuccessful()
      }

      data class AccomVisit(
        val date: LocalDateTime,
        val optDate: LocalDateTime?,
        val name: String,
        val other: String?
      )
      query {
        val results = VisitTime.select()
          .where { optLocalDate greaterEq noon.plusYears(2) }
          .orderBy(optLocalDate)
          .sequence { AccomVisit(it[localDate], it[optLocalDate], it[name], it[other]) }
          .toList()
        expect(results).toHaveSize(2)
        results.forEachIndexed { i, visit ->
          when (i) {
            0 -> {
              expect(visit.name).toBe("Motel 6")
              expect(visit.optDate).toBe(noon.plusYears(2))
            }
            1 -> {
              expect(visit.name).toBe("The Greenbrier")
              expect(visit.optDate).toBe(noon.plusYears(3))
            }
          }
        }
      }
    }
  }

  @Test
  fun `test bind other than LocalDateTime`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(VisitTime), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = VisitTime.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = LocalDate.now()
          it[1] = "2007-12-03T10:15:30"
        }
        setSuccessful()
      }
      query {
        expect(VisitTime.selectAll().count()).toBe(1)
      }
    }
  }

  @Test
  fun `test bind null to non-nullable`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    withTestDatabase(appCtx, listOf(VisitTime), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = VisitTime.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = null
          it[1] = "2007-12-03"
        }
        setSuccessful()
      }
      fail("bind of null should be exceptional")
    }
  }

  @Test
  fun `test bind malformed LocalDateTime string`() = coroutineRule.runBlockingTest {
    thrown.expectCause(isA(DateTimeParseException::class.java))
    withTestDatabase(appCtx, listOf(VisitTime), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = VisitTime.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = LocalDate.now()
          it[1] = "20071203"  // bad date format
        }
        setSuccessful()
      }
      fail("bind of null should be exceptional")
    }
  }

  @Test
  fun `test bind bad type`() = coroutineRule.runBlockingTest {
    thrown.expectCause(isA(DateTimeParseException::class.java))
    withTestDatabase(appCtx, listOf(VisitTime), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = VisitTime.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = LocalDate.now()
          it[1] = Date()  // doesn't accept
        }
        setSuccessful()
      }
      fail("bind of Date should be exceptional")
    }
  }

  @Test
  fun `test opt reference`() = coroutineRule.runBlockingTest {
    expect(HasVisitTimeRef.ref.descriptionDdl()).toBe(""""ref" TEXT""")
    withTestDatabase(
      appCtx,
      listOf(VisitTime, HasVisitTimeRef),
      coroutineRule.testDispatcher,
      true
    ) {
      query {
        HasVisitTimeRef.foreignKeyList.let { list ->
          expect(list).toHaveSize(1)
          expect(list[0]).toBe(
            ForeignKeyInfo(
              id = 0,
              seq = 0,
              table = "VisitTime",
              from = "ref",
              to = "date",
              onUpdate = ForeignKeyAction.NO_ACTION,
              onDelete = ForeignKeyAction.CASCADE
            )
          )
        }
      }
    }
  }
}

private object VisitTime : TestTable() {
  val localDate = localDateTimeText("date")
  val optLocalDate = optLocalDateTimeText("opt_date")
  val name = text("name")
  val other = optText("other")
}

object HasVisitTimeRef : TestTable() {
  val ref: Column<LocalDateTime?> = optReference("ref", localDate, ForeignKeyAction.CASCADE)
}
