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

package com.ealva.welite.db.table

import android.database.sqlite.SQLiteException
import com.ealva.welite.db.expr.Expression
import com.ealva.welite.db.expr.Op
import com.ealva.welite.db.type.PersistentType

@DslMarker
public annotation class WeLiteMarker

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
public interface ColumnConstraints<T> {
  public fun primaryKey(): ColumnConstraints<T>
  public fun asc(): ColumnConstraints<T>
  public fun desc(): ColumnConstraints<T>
  public fun onConflict(onConflict: OnConflict): ColumnConstraints<T>
  public fun autoIncrement(): ColumnConstraints<T>
  public fun unique(): ColumnConstraints<T>
  public fun check(name: String = "", op: (Column<T>) -> Op<Boolean>): ColumnConstraints<T>
  public fun default(defaultValue: T): ColumnConstraints<T>
  public fun defaultExpression(defaultValue: Expression<T>): ColumnConstraints<T>
  public fun collateBinary(): ColumnConstraints<T>
  public fun collateNoCase(): ColumnConstraints<T>
  public fun collateRTrim(): ColumnConstraints<T>
  public fun collate(name: String): ColumnConstraints<T>
  public fun index(customIndexName: String? = null): ColumnConstraints<T>
  public fun uniqueIndex(customIndexName: String? = null): ColumnConstraints<T>
  public fun <S : T> references(
    ref: Column<S>,
    onDelete: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    onUpdate: ForeignKeyAction = ForeignKeyAction.NO_ACTION,
    fkName: String? = null
  ): ColumnConstraints<T>
}

private fun List<ColumnConstraint>.joinAsString(): String = joinToString(separator = ",")

public class ConstraintNotAllowedException(message: String) : SQLiteException(message)

public inline fun notAllowed(messageProvider: () -> String) {
  throw ConstraintNotAllowedException(messageProvider())
}

public sealed class ColumnConstraint {
  public abstract fun mayAppearFirst(persistentType: PersistentType<*>)
  public abstract fun allowedToFollow(
    others: List<ColumnConstraint>,
    persistentType: PersistentType<*>
  )
}

private const val PRIMARY_KEY = "PRIMARY KEY"

public object PrimaryKeyConstraint : ColumnConstraint() {
  override fun toString(): String = PRIMARY_KEY

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(
    others: List<ColumnConstraint>,
    persistentType: PersistentType<*>
  ) { }
}

private const val ASC = "ASC"
private const val DESC = "DESC"

public abstract class AscDescConstraint(public val value: String) : ColumnConstraint() {
  override fun toString(): String = value

  override fun mayAppearFirst(persistentType: PersistentType<*>) {
    error { "$this must follow $PRIMARY_KEY" }
  }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    if (others.find { it is PrimaryKeyConstraint } == null) "$this must follow $PRIMARY_KEY"
  }
}

public object AscConstraint : AscDescConstraint(ASC)

public object DescConstraint : AscDescConstraint(DESC) {
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
public class ConflictConstraint(private val onConflict: OnConflict) : ColumnConstraint() {
  override fun toString(): String = onConflict.toString()

  private val mustFollow = "$this must follow $PRIMARY_KEY, $ASC or $DESC, $NOT_NULL, or $UNIQUE."

  override fun mayAppearFirst(persistentType: PersistentType<*>): Unit = notAllowed { mustFollow }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    others.find { it is ConflictConstraint }?.let { constraint ->
      notAllowed { "$constraint already defined, cannot add $this" }
    }
    val hasRequired = others.find {
      when (it) {
        is PrimaryKeyConstraint,
        is AscDescConstraint,
        is NotNullConstraint,
        is UniqueConstraint -> true
        else -> false
      }
    } != null
    if (!hasRequired) notAllowed { "$mustFollow Current=${others.joinAsString()}" }
  }
}

public object AutoIncrementConstraint : ColumnConstraint() {
  override fun toString(): String = "AUTOINCREMENT"

  override fun mayAppearFirst(persistentType: PersistentType<*>) {
    if (!persistentType.isIntegerType) notAllowed { "$this only allowed with INTEGER column" }
    notAllowed { "$this must follow $PRIMARY_KEY" }
  }

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
    if (!persistentType.isIntegerType) notAllowed { "$this only allowed with INTEGER column" }
    if (others.find { it is PrimaryKeyConstraint } == null) notAllowed {
      "$this for $PRIMARY_KEY only. Current=${others.joinAsString()}"
    }
    if (others.find { it is DescConstraint } != null) notAllowed {
      "$this not allowed with $DESC. Current=${others.joinAsString()}"
    }
  }
}

private const val NOT_NULL = "NOT NULL"

internal object NotNullConstraint : ColumnConstraint() {
  override fun toString() = NOT_NULL

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {}
}

private const val UNIQUE = "UNIQUE"

public object UniqueConstraint : ColumnConstraint() {
  override fun toString(): String = UNIQUE

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {
  }
}

public class CollateConstraint(private val collate: Collate) : ColumnConstraint() {
  override fun toString(): String = collate.sql

  override fun mayAppearFirst(persistentType: PersistentType<*>) {}

  override fun allowedToFollow(others: List<ColumnConstraint>, persistentType: PersistentType<*>) {}
}

public interface ConstraintCollection : Iterable<ColumnConstraint> {
  public fun add(constraint: ColumnConstraint)
  public fun hasPrimaryKey(): Boolean
  public fun hasAutoInc(): Boolean
  public fun hasNotNull(): Boolean
  public fun hasDesc(): Boolean

  /**
   * If a column is INTEGER PRIMARY KEY and is not also DESC, then it is the effective row ID and
   * will be autogenerated (accepts null on insert)
   * [SQLite Row ID](https://www.sqlite.org/rowidtable.html)
   */
  public fun isRowId(isIntegerType: Boolean): Boolean

  public companion object {
    /**
     * Make a ConstraintCollection with an initial [persistentType]
     */
    public operator fun invoke(persistentType: PersistentType<*>): ConstraintCollection {
      return ConstraintList(persistentType)
    }
  }
}

private class ConstraintList(
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
    return hasAutoInc() || (isIntegerType && hasPrimaryKey() && !hasDesc())
  }

  override fun iterator() = constraints.iterator()
}
