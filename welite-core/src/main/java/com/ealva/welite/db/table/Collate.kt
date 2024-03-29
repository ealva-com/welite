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

import com.ealva.welite.db.type.Identity

/**
 * Column collate clause
 */
public sealed class Collate(public val sql: String) {
  /** The keyword "TEXT" followed by the COLLATE statement */
  public open val textAndCollate: String
    get() = "TEXT $sql"

  override fun toString(): String = sql

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Collate

    if (sql != other.sql) return false

    return true
  }

  override fun hashCode(): Int = sql.hashCode()
}

public object CollateBinary : Collate("COLLATE BINARY")
public object CollateNoCase : Collate("COLLATE NOCASE")
public object CollateRTrim : Collate("COLLATE RTRIM")
public object UnspecifiedCollate : Collate("") {
  override val textAndCollate: String = "TEXT"
}

/**
 * Android doesn't expose ability to create a collating function but...
 */
public class CollateUser(
  name: String,
  forceQuoteName: Boolean = false
) : Collate("COLLATE ${Identity.make(name, forceQuoteName).value}")
