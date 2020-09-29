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
import com.ealva.welite.db.javatime.Visit.localDate
import com.ealva.welite.db.javatime.Visit.name
import com.ealva.welite.db.javatime.Visit.optLocalDate
import com.ealva.welite.db.javatime.Visit.other
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.table.where
import com.ealva.welite.test.common.CoroutineRule
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
import java.time.format.DateTimeParseException
import java.util.Date

private object Visit : Table() {
  val localDate = localDate("date")
  val optLocalDate = optLocalDate("opt_date")
  val name = text("name")
  val other = optText("other")
}

object HasVisitRef : Table() {
  val ref = optReference("ref", localDate, ForeignKeyAction.CASCADE)
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class LocalDateColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

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
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher, true) {
      transaction {
        Visit.insert {
          it[localDate] = LocalDate.now()
          it[optLocalDate] = LocalDate.now().plusYears(1)
          it[name] = "Regency"
          it[other] = "Pool"
        }
        val visitInsert = Visit.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name].bindArg()
          it[other].bindArg()
        }
        visitInsert.insert {
          it[0] = LocalDate.now()
          it[1] = LocalDate.now().plusYears(2)
          it[2] = "Motel 6"
          it[3] = "Vending Machine"
        }
        visitInsert.insert {
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
        val result = Visit.select()
          .where { optLocalDate greaterEq LocalDate.now().plusYears(2) }
          .orderBy(optLocalDate)
          .sequence { Accom(it[localDate], it[optLocalDate], it[name], it[other]) }
          .toList()

        expect(result).toHaveSize(2)
        result.forEachIndexed { i, visit ->
          when (i) {
            0 -> {
              expect(visit.name).toBe("Motel 6")
              expect(visit.optDate).toBe(LocalDate.now().plusYears(2))
            }
            1 -> {
              expect(visit.name).toBe("The Greenbrier")
              expect(visit.optDate).toBe(LocalDate.now().plusYears(3))
            }
          }
        }
      }
    }
  }

  @Test
  fun `test bind other than LocalDate`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = Visit.insertValues {
          it[localDate].bindArg()
          it[optLocalDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = LocalDateTime.now()
          it[1] = "2007-12-03"
        }
        setSuccessful()
      }
      query {
        expect(Visit.selectAll().count()).toBe(1)
      }
    }
  }

  @Test
  fun `test bind null to non-nullable`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = Visit.insertValues {
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
  fun `test bind malformed LocalDate string`() = coroutineRule.runBlockingTest {
    thrown.expectCause(isA(DateTimeParseException::class.java))
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = Visit.insertValues {
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
    withTestDatabase(appCtx, listOf(Visit), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = Visit.insertValues {
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
      fail("bind of date should be exceptional")
    }
  }

  @Test
  fun `test opt reference`() = coroutineRule.runBlockingTest {
    expect(HasVisitRef.ref.descriptionDdl()).toBe(""""ref" TEXT""")
    withTestDatabase(appCtx, listOf(Visit, HasVisitRef), coroutineRule.testDispatcher, true) {
      query {
        HasVisitRef.foreignKeyList.let { list ->
          expect(list).toHaveSize(1)
          expect(list[0]).toBe(
            ForeignKeyInfo(
              id = 0,
              seq = 0,
              table = "Visit",
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
