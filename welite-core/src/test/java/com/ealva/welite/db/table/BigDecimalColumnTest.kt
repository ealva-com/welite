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

package com.ealva.welite.db.table

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.db.WeLiteUncaughtException
import com.ealva.welite.db.expr.greaterEq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.test.db.table.withPlaceTestDatabase
import com.ealva.welite.test.shared.CoroutineRule
import com.ealva.welite.test.shared.withTestDatabase
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
import java.math.BigDecimal
import java.math.BigInteger

private const val scale = 5

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class BigDecimalColumnTest {
  @get:Rule
  var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test BigDecimal column`() {
    expect(BigTable.bigD.descriptionDdl()).toBe("bigd INTEGER NOT NULL")
    expect(BigTable.optBig.descriptionDdl()).toBe("opt_big INTEGER")
  }

  @Test
  fun `test BigDecimal insert and query`() = coroutineRule.runBlockingTest {
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(BigTable),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val insertBig = BigTable.insertValues {
          it[name].bindArg()
          it[bigD].bindArg()
          it[optBig].bindArg()
        }
        insertBig.insert {
          it[name] = "Bob"
          it[bigD] = BigDecimal("1234.560")
          it[optBig] = null
        }
        insertBig.insert {
          it[name] = "Mike"
          it[bigD] = BigDecimal("1234.567")
          it[optBig] = BigDecimal("2345.670")
        }
        insertBig.insert {
          it[0] = "Patricia"
          it[1] = BigDecimal("3456.780")
          it[2] = BigDecimal("3456.780")
        }
        setSuccessful()
      }
      query {
        fun bigScaled(value: String) = BigDecimal(value).setScale(scale)

        val result = BigTable.selects()
          .where { optBig greaterEq bigScaled("1234.567") }
          .orderByAsc { optBig }
          .sequence { Triple(it[name], it[bigD], it[optBig]) }
          .toList()

        expect(result).toHaveSize(2)
        expect(result[0]).toBe(Triple("Mike", bigScaled("1234.567"), bigScaled("2345.670")))
        expect(result[1]).toBe(Triple("Patricia", bigScaled("3456.780"), bigScaled("3456.780")))
      }
    }
  }

  @Test
  fun `test bind other than BigDecimal`() = coroutineRule.runBlockingTest {
    withTestDatabase(appCtx, setOf(BigTable), coroutineRule.testDispatcher) {
      transaction {
        val bigTableInsert = BigTable.insertValues {
          it[name] = "name"
          it[bigD].bindArg()
          it[optBig].bindArg()
        }
        bigTableInsert.insert {
          it[0] = BigInteger("2") // during bind converted to string, then BigDecimal
          // can bind same repeatedly without error
          it[1] = 2.0
          it[1] = 2.0F
          it[1] = 2
          it[1] = 2L
          it[1] = "2.0"
        }
        setSuccessful()
      }
      query {
        expect(BigTable.selectAll().count()).toBe(1)
      }
    }
  }

  @Test
  fun `test bind null to non-nullable`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    withTestDatabase(appCtx, setOf(BigTable), coroutineRule.testDispatcher) {
      transaction {
        val bigTableInsert = BigTable.insertValues {
          it[name] = "name"
          it[bigD].bindArg()
          it[optBig].bindArg()
        }
        bigTableInsert.insert {
          it[0] = null
          it[1] = BigDecimal.ONE
        }
        setSuccessful()
      }
      fail("bind of null should be exceptional")
    }
  }

  @Test
  fun `test bind malformed BigDecimal string`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteUncaughtException::class.java)
    thrown.expectCause(isA(NumberFormatException::class.java))
    withTestDatabase(appCtx, setOf(BigTable), coroutineRule.testDispatcher) {
      transaction {
        val bigTableInsert = BigTable.insertValues {
          it[name] = "name"
          it[bigD].bindArg()
          it[optBig].bindArg()
        }
        bigTableInsert.insert {
          it[0] = BigDecimal.ONE
          it[1] = "23GGZ" // bad BigDecimal
        }
      }
      fail("bind of malformed BigDecimal should be exceptional")
    }
  }

  @Test
  fun `test bind bad type`() = coroutineRule.runBlockingTest {
    thrown.expectCause(isA(NumberFormatException::class.java))
    withTestDatabase(appCtx, setOf(BigTable), coroutineRule.testDispatcher) {
      transaction {
        val bigTableInsert = BigTable.insertValues {
          it[name] = "name"
          it[bigD].bindArg()
          it[optBig].bindArg()
        }
        bigTableInsert.insert {
          it[0] = BigDecimal.ONE
          it[1] = true
        }
        setSuccessful()
      }
      fail("bind of Date should be exceptional")
    }
  }

  @Test
  fun `test opt reference`() = coroutineRule.runBlockingTest {
    expect(HasBigTableRef.ref.descriptionDdl()).toBe("ref INTEGER")
    withTestDatabase(appCtx, setOf(BigTable, HasBigTableRef), coroutineRule.testDispatcher) {
      query {
        HasBigTableRef.foreignKeyList.let { list ->
          expect(list).toHaveSize(1)
          list[0].let {
            expect(it.id).toBe(0)
            expect(it.seq).toBe(0)
            expect(it.table).toBe("Big")
            expect(it.from).toBe("ref")
            expect(it.to).toBe("bigd")
            expect(it.onUpdate).toBe(ForeignKeyAction.NO_ACTION)
            expect(it.onDelete).toBe(ForeignKeyAction.CASCADE)
          }
        }
      }
    }
  }
}

private object BigTable : Table() {
  val name = text("name")
  val bigD = bigDecimal("bigd", scale)
  val optBig = optBigDecimal("opt_big", scale)
}

private object HasBigTableRef : Table() {
  val ref = optReference("ref", BigTable.bigD, ForeignKeyAction.CASCADE)
}
