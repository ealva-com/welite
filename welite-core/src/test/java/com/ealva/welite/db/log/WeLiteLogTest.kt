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

package com.ealva.welite.db.log

import com.ealva.ealvalog.FilterResult
import com.ealva.ealvalog.LogLevel
import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.MarkerFactory
import com.ealva.ealvalog.Markers
import com.ealva.ealvalog.core.BasicMarkerFactory
import com.nhaarman.expect.expect
import org.junit.Test

public class WeLiteLogTest {
  @Suppress("ThrowsCount")
  @Test
  public fun `test configure logging`() {
    var orphanCalled = false
    var orphanName: String? = null
    val factory = object : MarkerFactory {
      override fun makeOrphan(name: String): Marker {
        orphanCalled = true
        orphanName = name
        return BasicMarkerFactory().makeOrphan(name)
      }

      override fun exists(name: String) = throw NotImplementedError()
      override fun get(name: String) = throw NotImplementedError()
      override fun orphan(name: String) = throw NotImplementedError()
    }
    WeLiteLog.configureLogging(factory)
    val name = "test"
    Markers.orphan(name)
    expect(orphanCalled).toBe(true)
    expect(orphanName).toBe(name)
  }

  @Test
  public fun `test configure logging default`() {
    WeLiteLog.logQueryPlans = true
    WeLiteLog.logSql = true
    WeLiteLog.configureLogging()
    val name = "name"
    val marker = Markers.orphan(name)
    expect(marker.name).toBe(name)
    expect(WeLiteLog.marker.name).toBe(WeLiteLog.markerName)
    expect(WeLiteLog.markerFilter.isLoggable("", LogLevel.ALL, WeLiteLog.marker, null))
      .toBe(FilterResult.NEUTRAL)
    expect(WeLiteLog.markerFilter.isLoggable("", LogLevel.ALL, null, null))
      .toBe(FilterResult.DENY)
    expect(WeLiteLog.logQueryPlans).toBe(true)
    expect(WeLiteLog.logSql).toBe(true)
  }
}
