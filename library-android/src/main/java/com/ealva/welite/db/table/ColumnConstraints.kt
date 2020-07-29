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

import android.database.sqlite.SQLiteException
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.type.PersistentType

@DslMarker
annotation class WeLiteMarker

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
@WeLiteMarker
interface ColumnConstraints<T> {
  fun primaryKey(): ColumnConstraints<T>
  fun unique(): ColumnConstraints<T>
  fun notNull(): ColumnConstraints<T>
  fun asc(): ColumnConstraints<T>
  fun desc(): ColumnConstraints<T>
  fun autoIncrement(): ColumnConstraints<T>
  fun default(defaultValue: T): ColumnConstraints<T>
  fun defaultExpression(defaultValue: Expression<T>): ColumnConstraints<T>
  fun onConflict(onConflict: OnConflict): ColumnConstraints<T>
  fun collateBinary(): ColumnConstraints<T>
  fun collateNoCase(): ColumnConstraints<T>
  fun collateRTrim(): ColumnConstraints<T>
  fun collate(name: String): ColumnConstraints<T>
  fun index(customIndexName: String? = null): ColumnConstraints<T>
  fun uniqueIndex(customIndexName: String? = null): ColumnConstraints<T>
  fun references(
    ref: Column<T>,
    onDelete: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    onUpdate: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    fkName: String? = null
  ): ColumnConstraints<T>

  fun check(name: String = "", op: (Column<T>) -> Op<Boolean>): ColumnConstraints<T>
}

private fun List<ColumnConstraint>.joinAsString(): String = joinToString(separator = ",")

class ConstraintNotAllowedException(message: String) : SQLiteException(message)

inline fun notAllowed(messageProvider: () -> String) {
  throw ConstraintNotAllowedException(messageProvider())
}

sealed class ColumnConstraint {
  abstract fun mayAppearFirst(persistentType: PersistentType<*>)
  abstract fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>)
}

private const val PRIMARY_KEY = "PRIMARY KEY"

object PrimaryKeyConstraint : ColumnConstraint() {
  override fun toString() = PRIMARY_KEY

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(
    others: List<ColumnConstraint>,
    persistentType: PersistentType<*>
  ) = notAllowed { "$this must be first column constraint. Current=[${others.joinAsString()}]" }

}

private const val ASC = "ASC"
private const val DESC = "DESC"

abstract class AscDescConstraint(val value: String) : ColumnConstraint() {
  override fun toString() = value

  override fun mayAppearFirst(persistentType: PersistentType<*>) {
    error { "$this must follow $PRIMARY_KEY" }
  }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    if (others.find { it is PrimaryKeyConstraint } == null) "$this must follow $PRIMARY_KEY"
  }
}

object AscConstraint : AscDescConstraint(
  ASC
)

object DescConstraint : AscDescConstraint(
  DESC
) {
  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    super.allowedToFollow(others, persistentType)
    if (others.find { it is AutoIncrementConstraint } != null) {
      notAllowed { "$this no allowed with $AutoIncrementConstraint" }
    }
  }

}

/**
 * ```
 * conflict_clause
 * : ( ON CONFLICT ( ROLLBACK
 *                 | ABORT
 *                 | FAIL
 *                 | IGNORE
 *                 | REPLACE
 *                 )
 * )?
 * ```
 */
class ConflictConstraint(private val onConflict: OnConflict) : ColumnConstraint() {
  override fun toString() = onConflict.toString()

  private val mustFollow = "$this must follow $PRIMARY_KEY, $ASC or $DESC, $NOT_NULL, or $UNIQUE."

  override fun mayAppearFirst(persistentType: PersistentType<*>) =
    notAllowed { mustFollow }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    val constraint = others.find { it is ConflictConstraint }
    if (constraint != null) notAllowed { "$constraint already defined, cannot add $this" }
    val hasRequired = others.find {
      when (it) {
        is PrimaryKeyConstraint, is AscDescConstraint, is NotNullConstraint, is UniqueConstraint -> {
          true
        }
        else -> false
      }
    } != null
    if (!hasRequired) notAllowed { "$mustFollow Current=${others.joinAsString()}" }
  }
}

object AutoIncrementConstraint : ColumnConstraint() {
  override fun toString() = "AUTOINCREMENT"

  override fun mayAppearFirst(persistentType: PersistentType<*>) {
    if (!persistentType.isIntegerType) notAllowed { "$this only allowed with INTEGER column" }
    notAllowed { "$this must follow $PRIMARY_KEY" }
  }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    if (!persistentType.isIntegerType) notAllowed { "$this only allowed with INTEGER column" }
    if (others.find { it is PrimaryKeyConstraint } == null) notAllowed { "$this for $PRIMARY_KEY only. Current=${others.joinAsString()}" }
    if (others.find { it is DescConstraint } != null) notAllowed { "$this not allowed with $DESC. Current=${others.joinAsString()}" }
  }

}

private const val NOT_NULL = "NOT NULL"

object NotNullConstraint : ColumnConstraint() {
  override fun toString() = NOT_NULL

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {}
}

private const val UNIQUE = "UNIQUE"

object UniqueConstraint : ColumnConstraint() {
  override fun toString() = UNIQUE

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
//    when (val prev = others.last()) {
//      is NotNullConstraint -> {
//      }
//      else -> notAllowed { "$this may not follow $prev" }
//    }
  }

}

class CollateConstraint(private val collate: Collate) : ColumnConstraint() {
  override fun toString() = collate.sql

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {}
}

interface ConstraintCollection : Iterable<ColumnConstraint> {
  fun add(constraint: ColumnConstraint)
  fun hasPrimaryKey(): Boolean
  fun hasAutoInc(): Boolean
  fun hasNotNull(): Boolean
  fun hasDesc(): Boolean

  /**
   * If a column is INTEGER PRIMARY KEY and is not also DESC, then it is the effective row ID and
   * will be autogenerated (accepts null on insert)
   * [SQLite Row ID](https://www.sqlite.org/rowidtable.html)
   */
  fun isRowId(isIntegerType: Boolean): Boolean

  companion object {
    operator fun invoke(persistentType: PersistentType<*>): ConstraintCollection {
      return ConstraintList(persistentType)
    }
  }
}

class ConstraintList(
  private val persistentType: PersistentType<*>
) : ConstraintCollection, Iterable<ColumnConstraint> {
  private val constraints = mutableListOf<ColumnConstraint>()

  /**
   * @throws ConstraintNotAllowedException if the constraint is not allowed or not allowed in the
   * position within the expression
   */
  override fun add(constraint: ColumnConstraint) {
    if (constraints.isEmpty()) {
      constraint.mayAppearFirst(persistentType)
    } else {
      constraint.allowedToFollow(constraints, persistentType)
    }
    constraints.add(constraint)
  }

  override fun hasPrimaryKey(): Boolean = constraints.contains(PrimaryKeyConstraint)
  override fun hasAutoInc(): Boolean = constraints.contains(AutoIncrementConstraint)
  override fun hasNotNull(): Boolean = constraints.contains(NotNullConstraint)
  override fun hasDesc(): Boolean = constraints.contains(DescConstraint)

  override fun isRowId(isIntegerType: Boolean): Boolean {
    if (hasAutoInc()) return true
    if (isIntegerType && hasPrimaryKey() && !hasDesc()) return true
    return true
  }

  override fun iterator() = constraints.iterator()
}

