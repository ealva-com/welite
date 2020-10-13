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

@file:Suppress(
  "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING",
  "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package com.ealva.welite.javatime

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.ForeignKeyInfo
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.javatime.OffsetDt.name
import com.ealva.welite.javatime.OffsetDt.offsetDate
import com.ealva.welite.javatime.OffsetDt.optOffsetDate
import com.ealva.welite.javatime.OffsetDt.other
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Column
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
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.Date

private object OffsetDt : Table() {
  val offsetDate = offsetDateTimeText("date")
  val optOffsetDate = optOffsetDateTimeText("opt_date")
  val name = text("name")
  val other = optText("other")
}

public object HasOffsetDtRef : Table() {
  public val ref: Column<OffsetDateTime?> =
    optReference("ref", offsetDate, ForeignKeyAction.CASCADE)
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
public class OffsetDateTimeColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule public var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test local date time column`() {
    expect(offsetDate.descriptionDdl()).toBe(""""date" TEXT NOT NULL""")
    expect(optOffsetDate.descriptionDdl()).toBe(""""opt_date" TEXT""")
  }

  @Test
  fun `test offset date time insert and query`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(OffsetDt), coroutineRule.testDispatcher, true) {
      val noonSomewhere = OffsetDateTime.of(
        LocalDateTime.of(2018, 6, 12, 12, 0),
        ZoneOffset.ofHours(6)
      )

      transaction {
        val actInsert = OffsetDt.insertValues {
          it[offsetDate].bindArg()
          it[optOffsetDate].bindArg()
          it[name].bindArg()
          it[other].bindArg()
        }
        actInsert.insert {
          it[0] = OffsetDateTime.now()
          it[1] = noonSomewhere.plusYears(2)
          it[2] = "Motel 6"
          it[3] = "Vending Machine"
        }
        actInsert.insert {
          it[0] = LocalDateTime.now()
          it[1] = noonSomewhere.plusYears(3)
          it[2] = "The Greenbrier"
          it[3] = "Conference Room"
        }

        setSuccessful()
      }

      data class OffsetVisit(
        val date: OffsetDateTime,
        val optDate: OffsetDateTime?,
        val name: String,
        val other: String?
      )
      query {
        val results = OffsetDt.select()
          .where { offsetDate greaterEq noonSomewhere.plusYears(2) }
          .orderBy(optOffsetDate)
          .sequence { OffsetVisit(it[offsetDate], it[optOffsetDate], it[name], it[other]) }
          .toList()
        expect(results).toHaveSize(2)
        expect(results[0].name).toBe("Motel 6")
        expect(results[0].optDate).toBe(noonSomewhere.plusYears(2))
        expect(results[1].name).toBe("The Greenbrier")
        expect(results[1].optDate).toBe(noonSomewhere.plusYears(3))
      }
    }
  }

  @Test
  fun `test bind other than OffsetDateTime`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, listOf(OffsetDt), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = OffsetDt.insertValues {
          it[offsetDate].bindArg()
          it[optOffsetDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = LocalDateTime.now()
          it[1] = "2007-12-03T10:15:30+01:00"
        }
        setSuccessful()
      }
      query {
        expect(OffsetDt.selectAll().count()).toBe(1)
      }
    }
  }

  @Test
  fun `test bind null to non-nullable`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    withTestDatabase(appCtx, listOf(OffsetDt), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = OffsetDt.insertValues {
          it[offsetDate].bindArg()
          it[optOffsetDate].bindArg()
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
  fun `test bind malformed OffsetDateTime string`() = coroutineRule.runBlockingTest {
    thrown.expectCause(CoreMatchers.isA(DateTimeParseException::class.java))
    withTestDatabase(appCtx, listOf(OffsetDt), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = OffsetDt.insertValues {
          it[offsetDate].bindArg()
          it[optOffsetDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = OffsetDateTime.now()
          it[1] = "20071203"  // bad date format
        }
        setSuccessful()
      }
      fail("bind of null should be exceptional")
    }
  }

  @Test
  fun `test bind bad type`() = coroutineRule.runBlockingTest {
    thrown.expectCause(CoreMatchers.isA(DateTimeParseException::class.java))
    withTestDatabase(appCtx, listOf(OffsetDt), coroutineRule.testDispatcher, true) {
      transaction {
        val visitInsert = OffsetDt.insertValues {
          it[offsetDate].bindArg()
          it[optOffsetDate].bindArg()
          it[name] = "name"
          it[other] = "other"
        }
        visitInsert.insert {
          it[0] = OffsetDateTime.now()
          it[1] = Date()  // doesn't accept
        }
        setSuccessful()
      }
      fail("bind of Date should be exceptional")
    }
  }

  @Test
  fun `test opt reference`() = coroutineRule.runBlockingTest {
    expect(HasOffsetDtRef.ref.descriptionDdl()).toBe(""""ref" TEXT""")
    withTestDatabase(appCtx, listOf(OffsetDt, HasOffsetDtRef), coroutineRule.testDispatcher, true) {
      query {
        HasOffsetDtRef.foreignKeyList.let { list ->
          expect(list).toHaveSize(1)
          expect(list[0]).toBe(
            ForeignKeyInfo(
              id = 0,
              seq = 0,
              table = "OffsetDt",
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
