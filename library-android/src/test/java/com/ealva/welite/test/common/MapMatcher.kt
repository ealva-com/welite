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

@file:Suppress("unused")

package com.ealva.welite.test.common

import com.nhaarman.expect.Matcher
import com.nhaarman.expect.fail

fun <K, V> expect(actual: Map<K, V>?): MapMatcher<K, V> {
  return MapMatcher(actual)
}

class MapMatcher<K, V>(override val actual: Map<K, V>?) : Matcher<Map<K, V>>(actual) {
  fun toBeEmpty(message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to be empty, but the actual value was null.", message)
    }

    if (actual.isNotEmpty()) {
      fail("Expected $actual to be empty.", message)
    }
  }

  fun toHaveSize(size: Int, message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to have size $size, but the actual value was null.", message)
    }

    if (actual.size != size) {
      fail("Expected $actual to have size $size, but the actual size was ${actual.size}.", message)
    }
  }

  fun toContainKey(expected: K, message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to contain key $expected but was null", message)
    }

    if (!actual.contains(expected)) {
      fail("Expected $actual to contain key $expected", message)
    }
  }

  fun toContain(expected: Pair<K, V>, message: (() -> Any?)? = null) {
    if (actual == null) {
      fail("Expected value to contain $expected, but the actual value was null.", message)
    }

    if (!actual.contains(expected.first)) {
      fail("Expected $actual to contain key ${expected.first}", message)
    }

    if (actual[expected.first] != expected.second) {
      fail("Expected $actual to contain key ${expected.first} with value ${expected.second}")
    }
  }
}
