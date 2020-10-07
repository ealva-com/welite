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

package com.ealva.welite.db

/**
 * Used internally in [Database.transaction] and [Database.query] as return values
 * from coroutine calls.
 */
internal sealed class WeLiteResult<out R> {

  /** Call was successful and contains the [value] */
  data class Success<R>(val value: R) : WeLiteResult<R>()

  /** Call did not complete successfully and an exception was thrown */
  data class Unsuccessful(val exception: Exception) : WeLiteResult<Nothing>() {
    companion object {
      fun makeUncaught(message: String, cause: Throwable): Unsuccessful {
        return Unsuccessful(WeLiteUncaughtException(message, cause))
      }
    }
  }
}

open class WeLiteException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause)

class WeLiteUncaughtException(message: String, cause: Throwable? = null) :
  WeLiteException(message, cause)
