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

package com.ealva.welite.db.android.trigger

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ealva.welite.db.WeLiteException
import com.ealva.welite.test.shared.CoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import com.ealva.welite.test.db.trigger.CommonTriggerTests as Common

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
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
  fun testCreateTrigger() = coroutineRule.runBlockingTest {
    Common.testCreateTrigger()
  }

  @Test
  fun testExecDeleteArtistTrigger() = coroutineRule.runBlockingTest {
    Common.testExecDeleteArtistTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testExecDeleteAlbumTrigger() = coroutineRule.runBlockingTest {
    Common.testExecDeleteAlbumTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testInsertTriggerValidUri() = coroutineRule.runBlockingTest {
    Common.testInsertTriggerValidUri(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testInsertTriggerInvalidUriCausesAbort() = coroutineRule.runBlockingTest {
    thrown.expect(WeLiteException::class.java)
    thrown.expectCause(CoreMatchers.isA(SQLiteConstraintException::class.java))
    Common.testInsertTriggerInvalidUriCausesAbor(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testDeleteMediaTrigger() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun testDeleteMediaAndTriggersCascade() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaAndTriggersCascade(appCtx, coroutineRule.testDispatcher)
  }
}
