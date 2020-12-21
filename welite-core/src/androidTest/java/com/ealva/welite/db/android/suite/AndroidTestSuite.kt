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

@file:Suppress(
  "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING",
  "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package com.ealva.welite.db.android.suite

import com.ealva.welite.db.android.DatabaseTests
import com.ealva.welite.db.android.DeleteTests
import com.ealva.welite.db.android.ForeignKeySchemaTests
import com.ealva.welite.db.android.expr.ConditionsTests
import com.ealva.welite.db.android.expr.FunctionTests
import com.ealva.welite.db.android.compound.CompoundSelectTests
import com.ealva.welite.db.android.table.CountTests
import com.ealva.welite.db.android.table.ExistsTests
import com.ealva.welite.db.android.table.GroupByTests
import com.ealva.welite.db.android.table.JoinTests
import com.ealva.welite.db.android.table.NaturalJoinTests
import com.ealva.welite.db.android.table.OrderByTests
import com.ealva.welite.db.android.trigger.TriggerTests
import com.ealva.welite.db.android.view.ViewTests
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.junit.runners.Suite

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(Suite::class)
@Suite.SuiteClasses(
  DatabaseTests::class,
  DeleteTests::class,
  ForeignKeySchemaTests::class,
  ConditionsTests::class,
  FunctionTests::class,
  CountTests::class,
  ExistsTests::class,
  GroupByTests::class,
  JoinTests::class,
  NaturalJoinTests::class,
  OrderByTests::class,
  ViewTests::class,
  TriggerTests::class,
  CompoundSelectTests::class
) class AndroidTestSuite
