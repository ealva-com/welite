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

package com.ealva.welite.db.table

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class MasterTypeTests {
  @get:Rule var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test MasterType from String`() = coroutineRule.runBlockingTest {
    val types = listOf(
      "Table" to MasterType.Table,
      "Index" to MasterType.Index,
      "View" to MasterType.View,
      "Trigger" to MasterType.Trigger
    )

    fun checkType(type: String, masterType: MasterType) {
      expect(type.asMasterType()).toBe(masterType)
      expect(type.toUpperCase().asMasterType()).toBe(masterType)
      expect(type.toLowerCase().asMasterType()).toBe(masterType)
    }

    types.forEach { checkType(it.first, it.second) }

    val badName = "tabled"
    val unknownType = badName.asMasterType()
    expect(unknownType).toBeInstanceOf<MasterType.Unknown>()
    expect(unknownType.value).toBe(badName)
    val nullUnknown = null.asMasterType()
    expect(nullUnknown).toBeInstanceOf<MasterType.Unknown>()
    expect(nullUnknown.value).toBe("NULL")
  }
}
