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

import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Configuration items for the Database. It's expected this will be implemented in such as way,
 * by the Database implementation, so that if the dispatcher gets replaced (not currently possible)
 * [dispatcher] will be updated for clients of DbConfig.
 *
 * TODO consider implementing this as a data class if the dispatcher given to the Database
 * during construction will never change.
 */
internal interface DbConfig {
  val dispatcher: CoroutineDispatcher
  val db: SQLiteDatabase
}
