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

package com.ealva.welite.db

/**
 * Result of various calls to [Database]
 */
sealed class WeResult<out R> {

  /** Call was successful and contains the [value] */
  data class Success<R>(val value: R) : WeResult<R>()

  /** Call did not complete successfully and an exception was thrown */
  data class Unsuccessful(val exception: WeLiteException) : WeResult<Nothing>() {
    companion object {
      fun make(message: String, cause: Throwable? = null): Unsuccessful {
        return when (cause) {
          null -> Unsuccessful(WeLiteException(message))
          is WeLiteException -> Unsuccessful(cause)
          else -> Unsuccessful(WeLiteException(message, cause))
        }
      }
    }
  }
}

open class WeLiteException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause)
