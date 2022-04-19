/*
 * Copyright 2022 eAlva.com
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

package com.ealva.welite.ktime

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.test.common.MainCoroutineRule
import com.ealva.welite.test.common.withTestDatabase
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private object HasInstant : Table() {
  val instant = instantEpochMillis("instant")
  val optInstant = optInstantEpochMillis("opt_instant")
  val name = text("name")
}

// private object HasInstantRef : Table() {
//  val ref = optReference("ref", HasInstant.instant, onDelete = ForeignKeyAction.CASCADE)
// }

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M])
class InstantColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule
  internal var coroutineRule = MainCoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test instant query`() = runTest {
    val firstName = "First Name"
    val firstTime = Clock.System.now()
      .roundToMillis()
    val firstOptTime = Clock.System.now()
      .plus(5.toDuration(DurationUnit.DAYS))
      .roundToMillis()

    val secondName = "Second Name"
    val secondTime = Clock.System.now()
      .roundToMillis()
    val secondOptTime = Clock.System.now()
      .plus(1.toDuration(DurationUnit.DAYS))
      .roundToMillis()

    withTestDatabase(appCtx, setOf(HasInstant), coroutineRule.testDispatcher, true) {
      transaction {
        HasInstant.insert {
          it[instant] = firstTime
          it[optInstant] = firstOptTime
          it[name] = firstName
        }
        HasInstant.insert {
          it[instant] = secondTime
          it[optInstant] = secondOptTime
          it[name] = secondName
        }
      }
      query {
        data class HasInstantValues(
          val instant: Instant,
          val opt: Instant?,
          val name: String
        )

        val result = HasInstant
          .selectAll()
          .orderByAsc { optInstant }
          .sequence { cursor ->
            HasInstantValues(
              instant = cursor[instant],
              opt = cursor[optInstant],
              name = cursor[name]
            )
          }
          .toList()

        expect(result).toHaveSize(2)
        expect(result[0].name).toBe(secondName)
        expect(result[0].instant).toBe(secondTime)
        expect(result[0].opt).toBe(secondOptTime)
        expect(result[1].name).toBe(firstName)
        expect(result[1].instant).toBe(firstTime)
        expect(result[1].opt).toBe(firstOptTime)
      }
    }
  }
}

private fun Instant.roundToMillis(): Instant =
  Instant.fromEpochMilliseconds(toEpochMilliseconds())
