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

import android.content.Context
import android.os.Build.VERSION_CODES.LOLLIPOP
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.expr.BaseSqlTypeExpression
import com.ealva.welite.db.expr.SqlBuilder
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.expr.greater
import com.ealva.welite.db.expr.invoke
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.type.NullableIntegerPersistentType
import com.ealva.welite.test.common.TestTable
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ```
 * column_constraint
 * : ( CONSTRAINT name )?
 * ( PRIMARY KEY ( ASC | DESC )? conflict_clause AUTOINCREMENT?
 * | NOT? NULL conflict_clause
 * | UNIQUE conflict_clause
 * | CHECK '(' expr ')'
 * | DEFAULT (signed_number | literal_value | '(' expr ')')
 * | COLLATE collation_name
 * | foreign_key_clause
 * )
 * ```
 * [Column constraints](https://sqlite.org/syntax/column-constraint.html)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
class ColumnTests {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `integer column`() {
    val id1 = "id1"
    val col2 = "col2"
    val col3 = "col3"
    val tableName = "Account"
    val account = object : Table(tableName) {
      val id = integer(id1)
      val col2: Column<Int> = integer(col2) { primaryKey() }
      val col3 = integer(col3) { unique() }
    }

    expect(account.id.descriptionDdl()).toBe(""""$id1" INTEGER NOT NULL""")
    expect(account.col2.descriptionDdl()).toBe(""""$col2" INTEGER NOT NULL PRIMARY KEY""")
    expect(account.col3.descriptionDdl()).toBe(""""$col3" INTEGER NOT NULL UNIQUE""")
  }

  @Test
  fun `integer column auto increment and defaults`() {
    val id1 = "id1"
    val col2 = "col2"
    val col3 = "col3"
    fun abs(value: Int) = object : BaseSqlTypeExpression<Int>() {
      override fun appendTo(sqlBuilder: SqlBuilder): SqlBuilder =
        sqlBuilder { append("ABS($value)") }

      override val persistentType = NullableIntegerPersistentType<Int>()
    }

    val tableName2 = "TableName2"
    val account2 = object : Table(tableName2) {
      val id = integer(id1) { primaryKey().autoIncrement() }
      val col2 = integer(col2) { default(4) }
      val col3 = integer(col3) { defaultExpression(abs(-100)) }
    }
    expect(account2.id.descriptionDdl()).toBe("\"$id1\" INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT")
    expect(account2.col2.descriptionDdl()).toBe(""""$col2" INTEGER NOT NULL DEFAULT 4""")
    expect(account2.col3.descriptionDdl()).toBe(""""$col3" INTEGER NOT NULL DEFAULT (ABS(-100))""")
  }

  @Test
  fun `integer column desc`() {
    val id1 = "id1"
    val tableName3 = "TableName3"
    val account3 = object : Table(tableName3) {
      val id = integer(id1) { primaryKey().desc() }
    }
    expect(account3.id.descriptionDdl()).toBe(""""$id1" INTEGER NOT NULL PRIMARY KEY DESC""")
  }

  @Test
  fun `text column key and collate`() {
    val id1 = "id1"
    val col2 = "col2"
    val col3 = "col3"
    val col4 = "col4"
    val col5 = "col5"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id = text(id1)
      val col2: Column<String> = text(col2) { primaryKey() }
      val col3 = text(col3) { unique().collateNoCase() }
      val col4 = text(col4) { collateRTrim() }
      val col5 = text(col5) { default("blah").check { it eq "blah" } }
    }

    expect(account.id.descriptionDdl()).toBe(""""$id1" TEXT NOT NULL""")
    expect(account.col2.descriptionDdl()).toBe(""""$col2" TEXT NOT NULL PRIMARY KEY""")
    expect(account.col3.descriptionDdl()).toBe(""""$col3" TEXT NOT NULL UNIQUE COLLATE NOCASE""")
    expect(account.col4.descriptionDdl()).toBe(""""$col4" TEXT NOT NULL COLLATE RTRIM""")
    expect(account.col5.descriptionDdl()).toBe(""""$col5" TEXT NOT NULL DEFAULT 'blah'""")

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(1)
    expect(ddl[0]).toBe(
      """CREATE TABLE IF NOT EXISTS """" + tableName + """" (""" +
        account.columns.joinToString { it.descriptionDdl() } +
        """, CONSTRAINT "check_Account_0" CHECK ("col5" = 'blah'))"""
    )
  }

  @Test
  fun `text column with check constraint`() {
    val id1Name = "id1"
    val id2Name = "id2"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name)
      val id2: Column<Int> = integer(id2Name) { check { it greater 10 } }

      init {
        uniqueIndex(id1, id2)
      }
    }

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(2)
    expect(ddl[0]).toBe(
      """CREATE TABLE IF NOT EXISTS """" + tableName + """" (""" +
        account.columns.joinToString { it.descriptionDdl() } +
        """, CONSTRAINT "check_Account_0" CHECK ("""" + id2Name + """" > 10))"""
    )
    expect(ddl[1]).toBe(
      "CREATE UNIQUE INDEX IF NOT EXISTS \"Account_id1_id2_unique\" " +
        "ON \"Account\"(\"id1\", \"id2\")"
    )
  }
}
