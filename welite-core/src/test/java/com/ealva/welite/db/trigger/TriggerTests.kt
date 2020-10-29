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
import com.nhaarman.expect.expect
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
  fun `test BeforeAfter value`() {
    expect(Trigger.BeforeAfter.BEFORE.value).toBe(" BEFORE")
    expect(Trigger.BeforeAfter.AFTER.value).toBe(" AFTER")
  }

  @Test
  fun `test Trigger Event accepts new or old`() {
    expect(Event.INSERT.value).toBe(" INSERT")
    expect(Event.INSERT.acceptsNewColumnRef).toBe(true)
    expect(Event.INSERT.acceptsOldColumnRef).toBe(false)
    expect(Event.UPDATE.value).toBe(" UPDATE")
    expect(Event.UPDATE.acceptsNewColumnRef).toBe(true)
    expect(Event.UPDATE.acceptsOldColumnRef).toBe(true)
    expect(Event.DELETE.value).toBe(" DELETE")
    expect(Event.DELETE.acceptsNewColumnRef).toBe(false)
    expect(Event.DELETE.acceptsOldColumnRef).toBe(true)
  }

  @Test
  fun `test create trigger`() = coroutineRule.runBlockingTest {
    Common.testCreateTrigger()
  }

  @Test
  fun `test drop trigger`() = coroutineRule.runBlockingTest {
    Common.testDropTrigger()
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
    Common.testInsertTriggerInvalidUriCausesAbort(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test delete media trigger`() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaTrigger(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test delete media and triggers cascade`() = coroutineRule.runBlockingTest {
    Common.testDeleteMediaAndTriggersCascade(appCtx, coroutineRule.testDispatcher)
  }

  @Test
  fun `test new column for delete event is invalid`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testNewColumnForDeleteEventIsInvalid()
  }

  @Test
  fun `test old column for insert event is invalid`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testOldColumnForInsertEventIsInvalid()
  }

  @Test
  fun `test new rejects column with wrong table`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testNewRejectsColumnWithWrongTable()
  }

  @Test
  fun `test old rejects column with wrong table`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testOldRejectsColumnWithWrongTable()
  }

  @Test
  fun `test insert on insert event`() {
    Common.testInsertOnInsertEvent()
  }

  @Test
  fun `test update on insert trigger`() {
    Common.testUpdateOnInsertTrigger()
  }

  @Test
  fun `test identity of old and new column`() {
    Common.testOldAndNewColumnIdentity()
  }

  @Test
  fun `test temp trigger`() {
    Common.testTempTrigger()
  }

  @Test
  fun `test update trigger when condition`() {
    Common.testUpdateTriggerWhenCondition()
  }

  @Test
  fun `test insert trigger when condition`() {
    Common.testInsertTriggerWhenCondition()
  }

  @Test
  fun `test delete trigger when condition`() {
    Common.testDeleteTriggerWhenCondition()
  }

  @Test
  fun `test trigger condition new rejects column with wrong table`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testTriggerConditionNewRejectsColumnWithWrongTable()
  }

  @Test
  fun `test trigger condition old rejects column with wrong table`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testTriggerConditionOldRejectsColumnWithWrongTable()
  }

  @Test
  fun `test when condition new column for delete event is invalid`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testWhenConditionNewColumnForDeleteEventIsInvalid()
  }

  @Test
  fun `test when condition old column for insert event is invalid`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testWhenConditionOldColumnForInsertEventIsInvalid()
  }

  @Test
  fun `test binding arg in trigger rejected`() {
    thrown.expect(IllegalStateException::class.java)
    Common.testBindingArgInTriggerRejected()
  }

  @Test
  fun `test update columns trigger`() {
    Common.testUpdateColumnsTrigger()
  }
}
