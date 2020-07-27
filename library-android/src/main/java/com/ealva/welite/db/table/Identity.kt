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

package com.ealva.welite.db.table

import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.table.Identity.Companion.quoteAll
import com.ealva.welite.db.table.Identity.Companion.quoteChar

/**
 * Identity is used as an identifier. If needed as a literal use [unquoted] and add
 * appropriate quoting. It is suggested to use the defaults of the [quoteChar] and [quoteAll]
 * which puts double quotes around all identifiers.
 *
 * The SQL standard specifies a large number of keywords which may not be used as the names of
 * tables, indices, columns, databases, user-defined functions, collations, virtual table modules,
 * or any other named object. The list of keywords is so long that few people can remember them all.
 * For most SQL code, your safest bet is to never use any English language word as the name of a
 * user-defined object.
 *
 * If you want to use a keyword as a name, you need to quote it.
 * * 'keyword'    A keyword in single quotes is a string literal.
 * * "keyword"    A keyword in double-quotes is an identifier.
 *
 * [SQLite Keywords](https://www.sqlite.org/lang_keywords.html)
 */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class Identity(val value: String) {
  /**
   * For use where quotes aren't needed or are incorrect - such as an string literal, which uses
   * single quotes.
   */
  val unquoted: String
    get() = value.trim(quoteChar)

  companion object {
    @Suppress("MemberVisibilityCanBePrivate")
    const val DEFAULT_QUOTE_CHAR = '"'

    @Suppress("MemberVisibilityCanBePrivate")
    const val DEFAULT_QUOTE_ALL = true

    var quoteChar: Char =
      DEFAULT_QUOTE_CHAR
    var quoteAll: Boolean =
      DEFAULT_QUOTE_ALL

    fun make(identity: String, forceQuote: Boolean = false): Identity {
      return if (quoteAll || forceQuote) {
        Identity(buildString {
          append(quoteChar)
          append(identity)
          append(quoteChar)
        })
      } else {
        Identity(identity)
      }
    }
  }
}

fun StringBuilder.appendIdentity(identity: Identity) = append(identity.value)

@Suppress("NOTHING_TO_INLINE")
inline fun String.asIdentity(forceQuote: Boolean = false) =
  Identity.make(this, forceQuote)

fun SqlBuilder.append(identity: Identity): SqlBuilder = append(identity.value)

