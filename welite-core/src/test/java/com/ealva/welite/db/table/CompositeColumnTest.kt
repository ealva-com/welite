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
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.expr.and
import com.ealva.welite.db.expr.eq
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class CompositeColumnTest {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test composite column`() {
    val account = object : Table("AccountTable") {
      val instant = instant("created_instant")
    }
    val descriptions = account.instant.columns.map { it.descriptionDdl() }
    expect(descriptions).toHaveSize(2)
    descriptions.forEachIndexed { index, col ->
      when (index) {
        0 -> expect(col).toBe("""created_instant_epoch INTEGER NOT NULL""")
        1 -> expect(col).toBe("""created_instant_nanos INTEGER NOT NULL""")
        else -> fail("Too many ddl strings returned")
      }
    }
  }
}

fun Table.instant(name: String): CompositeColumn<Instant> = makeComposite { columnFactory ->
  InstantComposite(columnFactory.long(name + "_epoch"), columnFactory.integer(name + "_nanos"))
}

class InstantComposite(
  val epochSeconds: Column<Long>,
  val nanos: Column<Int>
) : BaseCompositeColumn<Instant>() {
  override val columns: List<Column<*>> = listOf(epochSeconds, nanos)

  override fun eq(t: Instant): Op<Boolean> {
    return epochSeconds eq t.epochSecond and (nanos eq t.nano)
  }
}
