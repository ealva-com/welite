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

@file:Suppress("unused")

package com.ealva.welite.db.log

import com.ealva.ealvalog.Marker
import com.ealva.ealvalog.MarkerFactory
import com.ealva.ealvalog.Markers
import com.ealva.ealvalog.core.BasicMarkerFactory
import com.ealva.ealvalog.filter.MarkerFilter

public object WeLiteLog {
  /**
   * If true all query plans will be logged as informational logs. This includes the SQL, the
   * query args (parameter binding), and the results of "EXPLAIN QUERY PLAN" from SQLite.
   * Other SQL logging is controlled via [logSql]
   */
  public var logQueryPlans: Boolean = false

  public const val QUERY_PLAN_TAG: String = "WeLite-QueryPlan"

  /**
   * If this is true all non-query sql is logged. Query logging is controlled via [logQueryPlans].
   */
  public var logSql: Boolean = false

  public const val LOG_SQL_TAG: String = "WeLite-SQL"

  /** Call this function to ensure a MarkerFactory is set, typically  */
  public fun configureLogging(factory: MarkerFactory = BasicMarkerFactory()) {
    Markers.setFactory(factory)
  }

  public const val markerName: String = "WeLite"

  /**
   * Loggers in WeLite use this [Marker] so all WeLite logging can be filtered.
   */
  public val marker: Marker by lazy { Markers[markerName] }

  /**
   * Clients can use this [MarkerFilter] in a logger handler to direct associated logging where
   * desired (file, Android log, ...). See [eAlvaLog](https://github.com/ealva-com/ealvalog) for
   * information on configuring logging.
   */
  public val markerFilter: MarkerFilter by lazy { MarkerFilter(marker) }
}
