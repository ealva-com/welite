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

package com.ealva.welite.db.statements

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.orderByAsc
import com.ealva.welite.db.table.selectAll
import com.ealva.welite.db.type.Blob
import com.ealva.welite.test.db.table.withPlaceTestDatabase
import com.ealva.welite.test.shared.CoroutineRule
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.LOLLIPOP])
class BindParameterTests {
  @get:Rule
  internal var coroutineRule = CoroutineRule()

  private lateinit var appCtx: Context

  @Before
  fun setup() {
    appCtx = ApplicationProvider.getApplicationContext()
  }

  @Test
  fun `test insert and query quoted string`() = coroutineRule.runBlockingTest {
    val table = object : Table("foo") {
      val id = long("_id") { primaryKey() }
      val name = text("name")
      val optName = optText("opt_name")
    }
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(table),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val hasQuotes = "\"First\" 'Second' `Third` [TABLE] 'INDEX' \\ \\\\  "
        val optNameValue = "Optional Name"
        val insertStatement = table.insertValues {
          it[name].bindArg()
          it[optName].bindArg()
        }
        insertStatement.insert { it[0] = hasQuotes }
        insertStatement.insert {
          it[name] = hasQuotes
          it[optName] = optNameValue
        }
        val list = table
          .selectAll()
          .orderByAsc { id }
          .sequence { Pair(it[name], it[optName, "NULL"]) }
          .toList()
        expect(list).toHaveSize(2)
        expect(list).toBe(listOf(Pair(hasQuotes, "NULL"), Pair(hasQuotes, optNameValue)))
      }
    }
  }

  @Test
  fun `test bind numbers and opt numbers plus all conversions`() = coroutineRule.runBlockingTest {
    val table = object : Table("foo") {
      val reqByte = byte("req_byte")
      val optByte = optByte("opt_byte")
      val reqShort = short("req_short")
      val optShort = optShort("opt_short")
      val reqInt = integer("req_integer")
      val optInt = optInteger("opt_integer")
      val reqLong = long("req_long")
      val optLong = optLong("opt_long")
      val reqFloat = float("req_float")
      val optFloat = optFloat("opt_float")
      val reqDouble = double("req_double")
      val optDouble = optDouble("opt_double")
      val reqUByte = uByte("req_ubyte")
      val optUByte = optUByte("opt_ubyte")
      val reqUShort = uShort("req_ushort")
      val optUShort = optUShort("opt_ushort")
      val reqUInt = uInteger("req_uinteger")
      val optUInt = optUInteger("opt_uinteger")
      val reqULong = uLong("req_ulong")
      val optULong = optULong("opt_ulong")
    }
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(table),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val insertStatement = table.insertValues {
          it[reqByte].bindArg()
          it[optByte].bindArg()
          it[reqShort].bindArg()
          it[optShort].bindArg()
          it[reqInt].bindArg()
          it[optInt].bindArg()
          it[reqLong].bindArg()
          it[optLong].bindArg()
          it[reqFloat].bindArg()
          it[optFloat].bindArg()
          it[reqDouble].bindArg()
          it[optDouble].bindArg()
          it[reqUByte].bindArg()
          it[optUByte].bindArg()
          it[reqUShort].bindArg()
          it[optUShort].bindArg()
          it[reqUInt].bindArg()
          it[optUInt].bindArg()
          it[reqULong].bindArg()
          it[optULong].bindArg()
        }
        insertStatement.insert {
          it[0] = 0.toByte()
          it[1] = 0.toByte()
          it[2] = 0.toShort()
          it[3] = 0.toShort()
          it[4] = 0
          it[5] = 0
          it[6] = 0.toLong()
          it[7] = 0.toLong()
          it[8] = 0.toFloat()
          it[9] = 0.toFloat()
          it[10] = 0.toDouble()
          it[11] = 0.toDouble()
          it[12] = 0.toUByte()
          it[13] = 0.toUByte()
          it[14] = 0.toUShort()
          it[15] = 0.toUShort()
          it[16] = 0.toUInt()
          it[17] = 0.toUInt()
          it[18] = 0.toULong()
          it[19] = 0.toULong()
        }
        insertStatement.insert {
          it[0] = 1L
          it[1] = 1L
          it[2] = 1L
          it[3] = 1L
          it[4] = 1L
          it[5] = 1L
          it[6] = 1.toByte()
          it[7] = 1.toByte()
          it[8] = 1L
          it[9] = 1L
          it[10] = 1L
          it[11] = 1L
          it[12] = 1L
          it[13] = 1L
          it[14] = 1L
          it[15] = 1L
          it[16] = 1L
          it[17] = 1L
          it[18] = 1L
          it[19] = 1L
        }
        insertStatement.insert {
          it[0] = "2"
          it[1] = "2"
          it[2] = "2"
          it[3] = "2"
          it[4] = "2"
          it[5] = "2"
          it[6] = "2"
          it[7] = "2"
          it[8] = "2.0"
          it[9] = "2.0"
          it[10] = "2.0"
          it[11] = "2.0"
          it[12] = "2"
          it[13] = "2"
          it[14] = "2"
          it[15] = "2"
          it[16] = "2"
          it[17] = "2"
          it[18] = "2"
          it[19] = "2"
        }
        insertStatement.insert {
          it[0] = 3.toByte()
          it[1] = null
          it[2] = 3.toShort()
          it[3] = null
          it[4] = 3
          it[5] = null
          it[6] = 3.toLong()
          it[7] = null
          it[8] = 3.toFloat()
          it[9] = null
          it[10] = 3.toDouble()
          it[11] = null
          it[12] = 3.toUByte()
          it[13] = null
          it[14] = 3.toUShort()
          it[15] = null
          it[16] = 3.toUInt()
          it[17] = null
          it[18] = 3.toULong()
          it[19] = null
        }
        insertStatement.insert {
          it[reqByte] = 4.toByte()
          it[reqShort] = 4.toShort()
          it[reqInt] = 4
          it[reqLong] = 4.toLong()
          it[reqFloat] = 4.toFloat()
          it[reqDouble] = 4.toDouble()
          it[reqUByte] = 4.toUByte()
          it[reqUShort] = 4.toUShort()
          it[reqUInt] = 4.toUInt()
          it[reqULong] = 4.toULong()
        }
      }
      query {
        val fEpsilon = 0.0000000000001F
        val dEpsilon = 0.000000000000000000001
        expect(table.selectAll().count()).toBe(5)
        table
          .selectAll()
          .orderByAsc { table.reqByte }
          .forEach { cursor ->
            val pos = cursor.position
            val floatPos = pos.toFloat()
            val floatRange = (floatPos - fEpsilon)..(floatPos + fEpsilon)
            val doublePos = pos.toDouble()
            val doubleRange = (doublePos - dEpsilon)..(doublePos + dEpsilon)
            when (pos) {
              0, 1, 2 -> {
                expect(cursor[reqByte]).toBe(pos.toByte())
                expect(cursor[optByte, 100]).toBe(pos.toByte())
                expect(cursor[reqShort]).toBe(pos.toShort())
                expect(cursor[optShort, 100]).toBe(pos.toShort())
                expect(cursor[reqInt]).toBe(pos)
                expect(cursor[optInt, 100]).toBe(pos)
                expect(cursor[reqLong]).toBe(pos.toLong())
                expect(cursor[optLong, 100]).toBe(pos.toLong())
                expect(cursor[reqFloat]).toBeIn(floatRange)
                expect(cursor[optFloat, 100F]).toBeIn(floatRange)
                expect(cursor[reqDouble]).toBeIn(doubleRange)
                expect(cursor[optDouble, 100.0]).toBeIn(doubleRange)
                expect(cursor[reqUByte]).toBe(pos.toUByte())
                expect(cursor[optUByte, 100.toUByte()]).toBe(pos.toUByte())
                expect(cursor[reqUShort]).toBe(pos.toUShort())
                expect(cursor[optUShort, 100.toUShort()]).toBe(pos.toUShort())
                expect(cursor[reqUInt]).toBe(pos.toUInt())
                expect(cursor[optUInt, 100.toUInt()]).toBe(pos.toUInt())
                expect(cursor[reqULong]).toBe(pos.toULong())
                expect(cursor[optULong, 100.toULong()]).toBe(pos.toULong())
              }
              3, 4 -> {
                expect(cursor[reqByte]).toBe(pos.toByte())
                expect(cursor[optByte, Byte.MIN_VALUE]).toBe(Byte.MIN_VALUE)
                expect(cursor[reqShort]).toBe(pos.toShort())
                expect(cursor[optShort, Short.MIN_VALUE]).toBe(Short.MIN_VALUE)
                expect(cursor[reqInt]).toBe(pos)
                expect(cursor[optInt, Int.MIN_VALUE]).toBe(Int.MIN_VALUE)
                expect(cursor[reqLong]).toBe(pos.toLong())
                expect(cursor[optLong, Long.MIN_VALUE]).toBe(Long.MIN_VALUE)
                expect(cursor[reqFloat]).toBeIn(floatRange)
                expect(cursor[optFloat, Float.MIN_VALUE]).toBe(Float.MIN_VALUE)
                expect(cursor[reqDouble]).toBeIn(doubleRange)
                expect(cursor[optDouble, Double.MIN_VALUE]).toBe(Double.MIN_VALUE)
                expect(cursor[reqUByte]).toBe(pos.toUByte())
                expect(cursor[optUByte, UByte.MAX_VALUE]).toBe(UByte.MAX_VALUE)
                expect(cursor[reqUShort]).toBe(pos.toUShort())
                expect(cursor[optUShort, UShort.MAX_VALUE]).toBe(UShort.MAX_VALUE)
                expect(cursor[reqUInt]).toBe(pos.toUInt())
                expect(cursor[optUInt, UInt.MAX_VALUE]).toBe(UInt.MAX_VALUE)
                expect(cursor[reqULong]).toBe(pos.toULong())
                expect(cursor[optULong, ULong.MAX_VALUE]).toBe(ULong.MAX_VALUE)
              }
              else -> fail("unexpected row position=$pos")
            }
          }
      }
    }
  }

  enum class HasTwo {
    First,
    Second
  }

  @Test
  fun `test bind blob bool and enums`() = coroutineRule.runBlockingTest {
    val table = object : Table("foo") {
      val id = long("_id") { primaryKey() }
      val reqBlob = blob("req_blob")
      val optBlob = optBlob("opt_blob")
      val reqBool = bool("req_bool")
      val optBool = optBool("opt_bool")
      val reqEnum = enumeration("req_enum", HasTwo::class)
      val reqEnumByName = enumByName("req_enum_name", HasTwo::class)
      // val optEnum = NO nullable enum - use an enum member to signal absence and don't store
      // 3rd party enums - map them
    }

    val aBlobContents = "A Req Blob"
    val bBlobContents = "B Blob also Req"
    val aArray = aBlobContents.toByteArray()
    val bArray = bBlobContents.toByteArray()
    withPlaceTestDatabase(
      context = appCtx,
      tables = setOf(table),
      testDispatcher = coroutineRule.testDispatcher
    ) {
      transaction {
        val insStmt = table.insertValues {
          it[reqBlob].bindArg()
          it[optBlob].bindArg()
          it[reqBool].bindArg()
          it[optBool].bindArg()
          it[reqEnum].bindArg()
          it[reqEnumByName].bindArg()
        }
        insStmt.insert {
          it[reqBlob] = Blob(aArray)
          it[optBlob] = Blob(bArray)
          it[reqBool] = true
          it[optBool] = false
          it[reqEnum] = HasTwo.First
          it[reqEnumByName] = HasTwo.Second
        }
        insStmt.insert {
          it[reqBlob] = Blob(aArray)
          it[optBlob] = null
          it[reqBool] = "true".toBoolean()
          it[optBool] = null
          it[reqEnum] = HasTwo.First
          it[reqEnumByName] = HasTwo.Second
        }
        insStmt.insert {
          it[reqBlob] = Blob(aArray)
          it[reqBool] = true
          it[reqEnum] = HasTwo.First
          it[reqEnumByName] = HasTwo.Second
        }
      }
      query {
        table
          .selectAll()
          .orderByAsc { id }
          .forEach { cursor ->
            when (val pos = cursor.position) {
              0 -> {
                expect(cursor[reqBlob]).toBe(Blob(aArray))
                expect(cursor[optBlob]).toBe(Blob(bArray))
                expect(cursor[reqBool]).toBe(true)
                expect(cursor[optBool]).toBe(false)
                expect(cursor[reqEnum]).toBe(HasTwo.First)
                expect(cursor[reqEnumByName]).toBe(HasTwo.Second)
              }
              1 -> {
                expect(cursor[reqBlob]).toBe(Blob(aArray))
                expect(cursor.getOptional(optBlob)).toBeNull()
                expect(cursor[reqBool]).toBe(true)
                expect(cursor.getOptional(optBool)).toBeNull()
                expect(cursor[reqEnum]).toBe(HasTwo.First)
                expect(cursor[reqEnumByName]).toBe(HasTwo.Second)
              }
              2 -> {
                expect(cursor[reqBlob]).toBe(Blob(aArray))
                expect(cursor.getOptional(optBlob)).toBeNull()
                expect(cursor[reqBool]).toBe(true)
                expect(cursor.getOptional(optBool)).toBeNull()
                expect(cursor[reqEnum]).toBe(HasTwo.First)
                expect(cursor[reqEnumByName]).toBe(HasTwo.Second)
              }
              else -> fail("unexpected row position=$pos")
            }
          }
      }
    }
  }
}
