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

package com.ealva.welite.db.type

import android.os.Build
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class SqlBuilderCacheTest {
  @Before
  fun setup() {
    setSqlBuilderCacheCapacity(4)  // in case the default changes
    SqlBuilder.resetCache()
  }

  @Test
  fun `test buildStr`() {
    val result = buildStr {
      append("Some text ")
      append(5)
      append(" more")
    }
    expect(result).toBe("Some text 5 more")
  }

  @Test
  fun `test set capacity low`() {
    setSqlBuilderCapacity(1)
    expect(SqlBuilder.getCacheStats().maxBuilderCapacity).toBe(SqlBuilder.minimumBuilderCapacity)
  }

  @Test
  fun `test exceed entries`() {
    // nonsensical, don't do this
    buildStr {
      buildStr {
        buildStr {
          buildStr {
            buildStr {
            }
          }
        }
      }
    }
    val stats = SqlBuilder.getCacheStats()
    expect(stats.gets - stats.puts).toBe(1)
  }

  @Test
  fun `test exceed capacity`() {
    val filler = "".padStart(SqlBuilder.getCacheStats().maxBuilderCapacity + 100)
    buildStr {
      append(filler)
    }
    SqlBuilder.getCacheStats().let { after ->
      expect(after.exceededCapacity).toBe(1)
      buildStr {
        expect(length).toBe(0)
        expect(capacity).toBe(after.maxBuilderCapacity)
      }
    }
  }

  @Test
  fun `test exceed both`() {
    val filler = "".padStart(SqlBuilder.getCacheStats().maxBuilderCapacity + 100)
    buildStr {
      append(filler)
      buildStr {
        append(filler)
        buildStr {
          append(filler)
          buildStr {
            append(filler)
            buildStr {
              append(filler)
            }
          }
        }
      }
    }
    SqlBuilder.getCacheStats().let { after ->
      expect(after.exceededCapacity).toBe(4)
      expect(after.gets - after.puts).toBe(1)
      buildStr {
        expect(length).toBe(0)
        expect(capacity).toBe(after.maxBuilderCapacity)
      }
    }
  }
}
