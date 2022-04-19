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

package com.ealva.welite.db.table

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.table.Visit.duration
import com.ealva.welite.db.table.Visit.optDuration
import com.ealva.welite.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.DurationUnit

private object Visit : Table() {
  val duration = duration("dur", DurationUnit.MILLISECONDS)
  val optDuration = optDuration("optDur", DurationUnit.MILLISECONDS)
  val name = text("name")
  val other = optText("other")
}

object HasVisitRef : Table() {
  val ref = optReference("ref", duration, ForeignKeyAction.CASCADE)
}

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class DurationColumnTest {
  @ExperimentalCoroutinesApi
  @get:Rule
  internal var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule
  var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test duration column`() {
    expect(duration.descriptionDdl()).toBe("""dur INTEGER NOT NULL""")
    expect(optDuration.descriptionDdl()).toBe("""optDur INTEGER""")
  }
}
