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
import com.ealva.welite.db.table.Column
import com.ealva.welite.db.table.ForeignKeyAction
import com.ealva.welite.db.table.Table
import com.ealva.welite.sharedtest.TestTable
import com.nhaarman.expect.expect
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [LOLLIPOP])
class CreateTableTests {
  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun createTable() {
    val id1Name = "id1"
    val id2Name = "id2"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name)
      val id2: Column<Int> = integer(id2Name)

      override val primaryKey = PrimaryKey(id1, id2)
    }

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(1)
    val create = ddl.first()
    expect(create).toBe(
      """CREATE TABLE IF NOT EXISTS "$tableName" (${account.columns.joinToString { it.descriptionDdl() }}, CONSTRAINT "pk_$tableName" PRIMARY KEY ("$id1Name", "$id2Name"))"""
    )
  }

  @Test
  fun createAutoIncPrimary() {
    val id1Name = "id1"
    val id2Name = "id2"
    val tableName = "Account"
    @Suppress("unused") val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name) { primaryKey().autoIncrement().asc() }
      val id2: Column<Int> = integer(id2Name) { unique() }
    }

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(1)
    val create = ddl.first()
    expect(create).toBe(
      """CREATE TABLE IF NOT EXISTS "$tableName" (${account.columns.joinToString { it.descriptionDdl() }})"""
    )
  }

  @Test
  fun createWithForeignKey() {
    val id1Name = "id1"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name)
    }

    val otherIdName = "otherId"
    val otherTableName = "Other"
    @Suppress("unused") val other = object : TestTable(otherTableName) {
      val id1: Column<Int> = integer(otherIdName) { references(account.id1) }

    }

    expect(other.ddlForTest().first()).toBe(
      """CREATE TABLE IF NOT EXISTS "$otherTableName" (${other.columns.joinToString { it.descriptionDdl() }}, CONSTRAINT "fk_Other_otherId_id1" FOREIGN KEY ("otherId") REFERENCES "Account"("id1"))"""
    )
  }

  @Test
  fun createWithForeignKeyActions() {
    val id1Name = "id1"
    val tableName = "Account"
    val account = object : Table(tableName) {
      val id1: Column<Int> = integer(id1Name)
    }

    val otherIdName = "otherId"
    val otherTableName = "Other"
    @Suppress("unused") val other = object : TestTable(otherTableName) {
      val id1: Column<Int> = integer(otherIdName) {
        references(
          ref = account.id1,
          onDelete = ForeignKeyAction.CASCADE,
          onUpdate = ForeignKeyAction.SET_DEFAULT
        )
      }
    }

    expect(other.ddlForTest().first()).toBe(
      """CREATE TABLE IF NOT EXISTS ${other.identity.value} (${other.columns.joinToString { it.descriptionDdl() }}, CONSTRAINT "fk_Other_otherId_id1" FOREIGN KEY ("otherId") REFERENCES "Account"("id1") ON DELETE CASCADE ON UPDATE SET DEFAULT)"""
    )
  }

  @Test
  fun createTablePrimaryKeyConstraint() {
    val id1Name = "id1"
    val id2Name = "id2"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name)
      val id2: Column<Int> = integer(id2Name)

      override val primaryKey = PrimaryKey(id1, id2)
    }

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(1)
    val create = ddl.first()
    expect(create).toBe(
      """CREATE TABLE IF NOT EXISTS ${account.identity.value} (${account.columns.joinToString { it.descriptionDdl() }}, CONSTRAINT ${account.primaryKey.identity().value} PRIMARY KEY (${account.id1.identity().value}, ${account.id2.identity().value}))"""
    )
  }

  @Test
  fun `create table with indices`() {
    val id1Name = "id1"
    val id2Name = "id2"
    val tableName = "Account"
    val account = object : TestTable(tableName) {
      val id1: Column<Int> = integer(id1Name) { index() }
      val id2: Column<Int> = integer(id2Name) { uniqueIndex() }

      init {
        index(id1, id2)
      }
    }

    val ddl = account.ddlForTest()
    expect(ddl).toHaveSize(4)
    expect(ddl[0]).toBe(
      """CREATE TABLE IF NOT EXISTS "$tableName" (${account.columns.joinToString { it.descriptionDdl() }})"""
    )
    expect(ddl[1]).toBe("""CREATE INDEX IF NOT EXISTS "Account_id1" ON "Account"("id1")""")
    expect(ddl[2]).toBe("""CREATE UNIQUE INDEX IF NOT EXISTS "Account_id2_unique" ON "Account"("id2")""")
    expect(ddl[3]).toBe("""CREATE INDEX IF NOT EXISTS "Account_id1_id2" ON "Account"("id1", "id2")""")
  }
}
