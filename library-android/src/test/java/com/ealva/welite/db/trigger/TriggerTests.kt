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

package com.ealva.welite.db.trigger

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers.isA
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ealva.welite.test.db.trigger.CommonTriggerTests as Common

public typealias IdTriple = Triple<Long, Long, Long>

public val IdTriple.artistId: Long
  get() = first

public val IdTriple.albumId: Long
  get() = second

@Suppress("unused")
public val IdTriple.mediaId: Long
  get() = third

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class TriggerTests {
  @get:Rule var coroutineRule = CoroutineRule()

  @Suppress("DEPRECATION")
  @get:Rule var thrown: ExpectedException = ExpectedException.none()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test create trigger`() = coroutineRule.runBlockingTest {
    Common.testCreateTrigger()
  }

  @Test
  fun `test exec delete artist trigger`() = coroutineRule.runBlockingTest {
    Common.testExecDeleteArtistTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test exec delete album trigger`() = coroutineRule.runBlockingTest {
    Common.testExecDeleteAlbumTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test insert trigger valid Uri`() = coroutineRule.runBlockingTest {
    Common.testInsertTriggerValidUri(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test insert trigger invalid uri causes abort`() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    thrown.expectCause(isA(SQLiteConstraintException::class.java))
    Common.testInsertTriggerInvalidUriCausesAbor(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test delete media trigger`() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test delete media and triggers cascade`() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaAndTriggersCascade(appCtx, coroutineRule.testDispatcher)
  }
}
