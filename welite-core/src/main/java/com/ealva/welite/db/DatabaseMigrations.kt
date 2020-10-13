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

package com.ealva.welite.db

import com.ealva.ealvalog.invoke
import com.ealva.ealvalog.lazyLogger
import com.ealva.ealvalog.w
import com.ealva.welite.db.log.WeLiteLog
import java.util.ArrayList
import java.util.HashMap
import java.util.TreeMap

public interface Migration {
  public val startVersion: Int
  public val endVersion: Int
  public fun execute(db: Database)
}

public abstract class BaseMigration(
  override val startVersion: Int,
  override val endVersion: Int
) : Migration {
  override fun toString(): String = "Migration from $startVersion to $endVersion"
}

private typealias EndMigrationMap = MutableMap<Int, Migration>
private typealias EndMigrationMapImpl = TreeMap<Int, Migration>
private typealias StartToEndMigrationMap = MutableMap<Int, EndMigrationMap>
private typealias StartToEndMigrationMapImpl = HashMap<Int, EndMigrationMap>

@Suppress("FunctionName") // detekt
private fun EndMigrationMap(): EndMigrationMap = EndMigrationMapImpl()

@Suppress("FunctionName") // detekt
private fun StartToEndMigrationMap(): StartToEndMigrationMap = StartToEndMigrationMapImpl()

private val LOG by lazyLogger(Migration::class, WeLiteLog.marker)

/**
 * Finds the list of migrations that should be run to move from [startVersion] to [endVersion]
 * @param startVersion current database version
 * @param endVersion target database version
 * @return An ordered list of [Migration]s that should be executed to migrate the database or null
 * if a migration path cannot be found.
 */
@Suppress("ReturnCount") // detekt
public fun List<Migration>.findMigrationPath(startVersion: Int, endVersion: Int): List<Migration>? {
  val migrationMap: StartToEndMigrationMap = StartToEndMigrationMap().apply {
    this@findMigrationPath.forEach { migration ->
      val targetMap: EndMigrationMap = getOrPut(migration.startVersion) { EndMigrationMap() }
      targetMap[migration.endVersion]?.let { existing ->
        LOG.w { it("Replacing migration %s with %s", existing, migration) }
      }
      targetMap[migration.endVersion] = migration
    }
  }

  if (startVersion == endVersion) {
    return emptyList()
  }
  val isUpgrade = endVersion > startVersion
  val result = ArrayList<Migration>(migrationMap.size)
  var start = startVersion
  var found = false
  while (if (isUpgrade) start < endVersion else start > endVersion) {
    val targetNodes: EndMigrationMap = migrationMap[start] ?: return null
    val keySet = if (isUpgrade) {
      (targetNodes as EndMigrationMapImpl).descendingKeySet()
    } else {
      targetNodes.keys
    }
    for (targetVersion in keySet) {
      val shouldAddToPath: Boolean = if (isUpgrade) {
        targetVersion in (start + 1)..endVersion
      } else {
        targetVersion in endVersion until start
      }
      if (shouldAddToPath) {
        result.add(checkNotNull(targetNodes[targetVersion]))
        start = targetVersion
        found = true
        break
      }
    }
    if (!found) break
  }
  return if (found) result else null
}
