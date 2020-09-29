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

package com.ealva.welite.test.db.table

import android.content.Context
import com.ealva.welite.db.Database
import com.ealva.welite.db.expr.SortOrder
import com.ealva.welite.db.expr.eq
import com.ealva.welite.db.statements.insertValues
import com.ealva.welite.db.table.Table
import com.ealva.welite.db.table.select
import com.ealva.welite.db.table.where
import com.ealva.welite.test.db.table.Doctors.degree
import com.ealva.welite.test.db.table.Doctors.doctorName
import com.ealva.welite.test.db.table.Specialty.description
import com.ealva.welite.test.db.table.Visits.patientName
import com.ealva.welite.test.db.table.Visits.visitDate
import com.nhaarman.expect.expect
import com.nhaarman.expect.fail
import kotlinx.coroutines.CoroutineDispatcher

object CommonNaturalJoinTests {
  suspend fun testNaturalJoin(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Doctors, Specialty, Visits),
      testDispatcher = testDispatcher
    ) {
      insertData()
      query {
        expect(Doctors.exists).toBe(true)
        expect(Specialty.exists).toBe(true)
        expect(Visits.exists).toBe(true)
        Doctors.naturalJoin(Visits)
          .select(Doctors.doctorId, doctorName, degree, patientName, visitDate)
          .where { degree eq "MD" }
          .orderBy(Doctors.doctorId)
          .sequence {
            DoctorsVisit(
              it[Doctors.doctorId],
              it[doctorName],
              it[degree],
              it[patientName],
              it[visitDate]
            )
          }
          .forEachIndexed { index, visit ->
            when (index) {
              0 -> {
                expect(visit.doctorId).toBe(210)
                expect(visit.doctorName).toBe("Dr. John Linga")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("Julia Nayer")
                expect(visit.date).toBe("2013-10-15")
                expect(visit.specialty).toBeNull()
              }
              1 -> {
                expect(visit.doctorId).toBe(212)
                expect(visit.doctorName).toBe("Dr. Ke Gee")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("James Marlow")
                expect(visit.date).toBe("2013-10-16")
                expect(visit.specialty).toBeNull()
              }
              2 -> {
                expect(visit.doctorId).toBe(212)
                expect(visit.doctorName).toBe("Dr. Ke Gee")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("Jason Mallin")
                expect(visit.date).toBe("2013-10-12")
                expect(visit.specialty).toBeNull()
              }
              else -> fail("Unexpected Doctor visit index=$index")
            }
          }
      }
    }
  }

  suspend fun testNaturalJoinThreeTables(appCtx: Context, testDispatcher: CoroutineDispatcher) {
    withPlaceTestDatabase(
      context = appCtx,
      tables = listOf(Doctors, Specialty, Visits),
      testDispatcher = testDispatcher
    ) {
      insertData()
      query {
        expect(Doctors.exists).toBe(true)
        expect(Specialty.exists).toBe(true)
        expect(Visits.exists).toBe(true)

        (Doctors naturalJoin Specialty naturalJoin Visits)
          .select(Doctors.doctorId, doctorName, degree, description, patientName, visitDate)
          .where { degree eq "MD" }
          .orderBy(Doctors.doctorId, SortOrder.DESC)
          .sequence {
            DoctorsVisit(
              it[Doctors.doctorId],
              it[doctorName],
              it[degree],
              it[patientName],
              it[visitDate],
              it[description]
            )
          }.forEachIndexed { index, visit ->
            when (index) {
              0 -> {
                expect(visit.doctorId).toBe(212)
                expect(visit.doctorName).toBe("Dr. Ke Gee")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("James Marlow")
                expect(visit.date).toBe("2013-10-16")
                expect(visit.specialty).toBe("ARTHO")
              }
              1 -> {
                expect(visit.doctorId).toBe(212)
                expect(visit.doctorName).toBe("Dr. Ke Gee")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("Jason Mallin")
                expect(visit.date).toBe("2013-10-12")
                expect(visit.specialty).toBe("ARTHO")
              }
              2 -> {
                expect(visit.doctorId).toBe(210)
                expect(visit.doctorName).toBe("Dr. John Linga")
                expect(visit.degree).toBe("MD")
                expect(visit.patient).toBe("Julia Nayer")
                expect(visit.date).toBe("2013-10-15")
                expect(visit.specialty).toBe("GYNO")
              }
              else -> fail("Unexpected Doctor visit index=$index")
            }
          }
      }
    }
  }

  private suspend fun Database.insertData() {
    transaction {
      val doctorInsert = Doctors.insertValues {
        it[doctorId].bindArg()
        it[doctorName].bindArg()
        it[degree].bindArg()
      }
      doctorInsert.insert {
        it[0] = 210
        it[1] = "Dr. John Linga"
        it[2] = "MD"
      }
      doctorInsert.insert {
        it[0] = 211
        it[1] = "Dr. Peter Hall"
        it[2] = "MBBS"
      }
      doctorInsert.insert {
        it[0] = 212
        it[1] = "Dr. Ke Gee"
        it[2] = "MD"
      }
      doctorInsert.insert {
        it[0] = 213
        it[1] = "Dr. Pat Fay"
        it[2] = "MD"
      }

      val splInsert = Specialty.insertValues {
        it[splId].bindArg()
        it[description].bindArg()
        it[doctorId].bindArg()
      }
      splInsert.insert {
        it[0] = 1
        it[1] = "CARDIO"
        it[2] = 211
      }
      splInsert.insert {
        it[0] = 2
        it[1] = "NEURO"
        it[2] = 213
      }
      splInsert.insert {
        it[0] = 3
        it[1] = "ARTHO"
        it[2] = 212
      }
      splInsert.insert {
        it[0] = 4
        it[1] = "GYNO"
        it[2] = 210
      }

      val visitInsert = Visits.insertValues {
        it[doctorId].bindArg()
        it[patientName].bindArg()
        it[visitDate].bindArg()
      }
      visitInsert.insert {
        it[0] = 210
        it[1] = "Julia Nayer"
        it[2] = "2013-10-15"
      }
      visitInsert.insert {
        it[0] = 214
        it[1] = "TJ Olson"
        it[2] = "2013-10-14"
      }
      visitInsert.insert {
        it[0] = 215
        it[1] = "John Seo"
        it[2] = "2013-10-15"
      }
      visitInsert.insert {
        it[0] = 212
        it[1] = "James Marlow"
        it[2] = "2013-10-16"
      }
      visitInsert.insert {
        it[0] = 212
        it[1] = "Jason Mallin"
        it[2] = "2013-10-12"
      }

      setSuccessful()
    }
  }
}

object Doctors : Table("doctors") {
  val doctorId = integer("doctor_id") { primaryKey() }
  val doctorName = text("doctor_name")
  val degree = text("degree")
}

object Specialty : Table("specialty") {
  val splId = integer("spl_id") { primaryKey() }
  val description = text("spl_descrip") { unique() }
  val doctorId = integer("doctor_id")
}

object Visits : Table("visits") {
  val doctorId = integer("doctor_id")
  val patientName = text("patient_name")
  val visitDate = text("vdate")
}

data class DoctorsVisit(
  val doctorId: Int,
  val doctorName: String,
  val degree: String,
  val patient: String,
  val date: String,
  val specialty: String? = null
)
