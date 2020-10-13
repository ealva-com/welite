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

/**
 * The interface presented to the client to bind arguments to the sql statement.
 */
public interface ArgBindings {
  /**
   * Set the argument at [index] to [value]. The index is the position of the '?' bind-parameter in
   * the SQL. The underlying persistent type object handles any necessary conversions of value or
   * throws an exception on error. If a value is not bound at a particular index, that index will
   * contain the equivalent of the null value and ultimately be bound as such.
   */
  public operator fun <T> set(index: Int, value: T?)

  /**
   * Total number of bindable arguments
   */
  public val argCount: Int
}

public val NO_ARGS: (ArgBindings) -> Unit = {}
