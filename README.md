WeLite
======

WeLite is a Kotlin DSL, and Statement/Cursor Wrapper, for Android SQLite 

### The goals of this library are:
  * Provide a DSL for Android SQLite that fully (eventually) encapsulates the underlying SQL
  * Clients write Kotlin - no SQL, no annotation processing
  * Support Kotlin coroutines via suspend methods and an injected coroutine dispatcher
  * Encapsulate SQLite statements, eg. insert/update/delete, for reuse and simplified binding   
  * Encapsulate Select results and provide for easy row->POJO conversion. Note: we use POJO and data class interchangeably

### Contributions
Contributions welcome. Please fill out the template for the situation and include it in the Issue you open.
  * [Defect Report][bug_report]
  * [Feature Request][feature_request]
  * [Pull Request][pull_request]

## Usage

### Create Database
Create a single Database instance and then inject/locate where needed.
```kotlin
Database(
  context = androidContext(),
  fileName = "FileName",
  tables = listOf(TableA, TableB, TableC),
  version = 1,
  migrations = emptyList(),
  openParams = OpenParams(
    enableWriteAheadLogging = true,
    enableForeignKeyConstraints = true
  )
) {
  onConfigure { configuration -> /* your code */ }
  onCreate { database -> /* your code */ }
  onOpen { database -> /* your code */  }
}
 ``` 
When constructing the Database a DatabaseLifecycle object provides for configuration on 
each step toward fully configured, created, and open. The client optionally provides a lambda to
be called at each step. The interfaces provided to these configuration lambdas are designed based
on when the Android SQLite documentation or code says various functions should be called.     
  
### Define Tables
Tables extend the Table class and define the columns/column attributes.
```kotlin
object MediaFileTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val mediaUri = text("MediaUri") { unique() }
  val artistId = long("ArtistId") { references(ArtistTable.id) }
  val albumId = long("AlbumId") { references(AlbumTable.id) }
}

object ArtistTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val comment = optText("comment")
  val artistName = text("ArtistName") { collateNoCase().uniqueIndex() }
}

object AlbumTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val albumName = text("AlbumName") { collateNoCase().uniqueIndex() }
}

object ArtistAlbumTable : TestTable() {
  val id = long("_id") { primaryKey() }
  val artistId = long("ArtistId") { index().references(ArtistTable.id, ForeignKeyAction.CASCADE) }
  val albumId = long("AlbumId") { index().references(AlbumTable.id, ForeignKeyAction.CASCADE) }
  init {
    uniqueIndex(artistId, albumId)
  }
}
```   
### Insert Data
To insert data, or any database operation, a transaction needs to be opened. The Database.transaction() will execute the given lambda via a CoroutineDispatcher which defaults to Dispatchers.IO but may be provided by the client. 
```kotlin
db.transaction {
  // build an insert statement which contains bindable arguments
  val insertStatement = MediaFileTable.insertValues {
    it[mediaUri] = bindString()
    it[fileName] = bindString()
    it[mediaTitle] = bindString()
  }

  // use the insert statement to insert the data, which is most efficient for bulk inserting
  insertStatement.insert {
    it[0] = "file.mpg"
    it[1] = "/dir/Music/file.mpg"
    it[2] = "A Title"
  }

  // reuse statement and insert another row        
  insertStatement.insert {
    it[0] = "/dir/Music/anotherFile.mpg"
    it[1] = "anotherFile.mpg"
    it[2] = "Another Title"
  }

  // example of single insert (statement not exposed)
  MediaFileTable.insert {
    it[mediaUri] = "/dir/Music/third.mp3"
    it[fileName] = "third"
    it[mediaTitle] = "Third Title"
  }

  // single insert with bind variables
  MediaFileTable.insert({ it[0] = "/dir/Music/fourth.mp3" }) {
    it[mediaUri] = bindString()
    it[fileName] = "fourth"
    it[mediaTitle] = "Fourth Title"
  }

  // txn must be marked successful or will not commit. Can also call Transaction.rollback()
  setSuccessful()
}
```  
### Query Data
Queries are performed on a ColumnSet, such as Table, Join, Alias... The DSL provides for the typical select (columns) where (expression) to generate QueryBuilder object on which the client can call orderBy, groupBy, etc. and then build the Query for reuse.

For example, to get the count of rows in the MediaFileTable where the mediaTitle column has "Hits" somewhere in the string:
```kotlin
val count = query {
  MediaFileTable.select(fileName).where { mediaTitle like "%Hits%" }.count()
}
```  
To check if an Artist has been inserted into the ArtistTable, and if not then insert:
```kotlin
query {
  val artist = "The Beatles"
  val idArtist: Long = ArtistTable.select(ArtistTable.id)
        .where { ArtistTable.artistName eq artist }
        .sequence { it[ArtistTable.id] }
        .singleOrNull() ?: ArtistTable.insert { it[artistName] = artist }
}
```
A select of the artist id on the ArtistTable, where artistName equals the artist in question, is performed and is transformed to a sequence where a single item is expected. If the result of singleOrNull is null, then the data is inserted.

This snippet from a unit test shows how to refer to a Table alias column via the original table column:
```kotlin
query {
  val personAlias = Person.alias("person_alias")
  expect(
    personAlias.select(personAlias[Person.name], personAlias[Person.cityId])
    .where { personAlias[Person.name] eq "Rick"}
    .groupBy(personAlias[Person.cityId])
    .sequence { it[personAlias[Person.name]] }
    .count()
  ).toBe(1)
}
```

An example from a unit test demonstrates a query on a Join that uses both an expression alias and a query alias.
```kotlin
query {
  val expAlias: SqlTypeExpressionAlias<String> = Person.name.max().alias("pxa")
  // MAX("Person"."name") pxa  

  val personAlias: QueryBuilderAlias = Person.select(Person.cityId, expAlias)
    .all()
    .groupBy(Person.cityId)
    .alias("uqa")

  expect(
    Person.join(personAlias, JoinType.INNER, Person.name, personAlias[expAlias])
      .selectAll()
      .sequence { personAlias[expAlias] }
      .count()
  ).toBe(3)
}
/*
Underlying SQL generated by the Join DSL:

SELECT "Person"."id", "Person"."name", "Person"."place_id", "uqa"."place_id" 
  FROM "Person" 
  INNER JOIN (
    SELECT "Person"."place_id", MAX("Person"."name") pxa 
      FROM "Person" 
      GROUP BY "Person"."place_id"
  ) uqa ON "Person"."name" = uqa.pxa
*/
```
In the above example the Person table is joined to a query alias of itself, and the join further uses an alias expression. The sequence generator yields the Person's name and a count of sequence items is taken. Examining the generated SQL gives some idea of what is happening in the lower layers of the library.
### Modules
  * library-android - contains the core classes of WeLite
  * library-javatime - contains column types for LocalDate and LocalDateTime. Requires dependency on "com.android.tools:desugar_jdk_libs:${Versions.DESUGAR}" and coreLibraryDesugaringEnabled = true in compile options
  * library-test - contains shared test classes, won't be published
  * app - skeleton application demos configuration, Koin injection, simple database creation, table population, query, etc.   
### Why this library?

For WeLite the desire is to push all SQL down into the library and treat it as an implementation detail of interfacing with the persistence layer. Clients should use a Kotlin DSL to describe tables and relationships, and then use the resulting objects for CRUD work, keeping SQL and SQLite API hidden (objects versus SQL string handling). 

The goal of this library to not to be a full-blown ORM. Instead, this library simplifies reading rows and converting them to POJOs efficiently using RDBMS features, primarily supporting simple types, and putting few lines of the Relational-OO mapping in the client code (specifying which columns are needed to construct a POJO).   

Both the [Squash] and [Exposed] libraries influence this library, primarily the DSL code. However, WeLite calls directly into the Android SQLite API and does not go through a JDBC driver, which alleviates the need (and code) to deal with variations in SQL and RDBMS features. Also, the WeLite design philosophy differs from these other libraries in specific areas. Pre 1.0 versions will see significant API changes.       


### The name?

Lite=SQLite and We="without entities". After attempting to port an in-house solution to Room and searching for other libraries, it was decided to build a thin wrapper over the SQLite API with a Kotlin DSL to build the underlying SQL. The goal is to store and retrieve data with as little friction as possible, while providing benefits of a Kotlin interface. That said, the client controls what objects are created from rows in a query. While we envision flows of data classes or simple types, the user is free to construct any type of object(s).

Why "without entities"? We often find a one-to-many relationship between a row in a table to a Kotlin object. So we don't try to enforce a row to entity mapping or load data unnecessarily. A higher level layer providing more ORM type functionality could be built over WeLite.  

### TODO
  * Much more testing
  * Expose more SQLite functionality (need DSL for View and Trigger)
  * Rethink binding parameters. Currently, not enforcing type at compile-time and instead doing conversions at run-time
  

[squash]: https://github.com/orangy/squash
[exposed]: https://github.com/JetBrains/Exposed
[coroutines]: https://kotlinlang.org/docs/reference/coroutines-overview.html
[flow]: https://kotlinlang.org/docs/reference/coroutines/flow.html  
[splitties]: https://github.com/LouisCAD/Splitties
[bug_report]: .github/ISSUE_TEMPLATE/bug_report.md
[feature_request]: .github/ISSUE_TEMPLATE/feature_request.md
[pull_request]: .github/pull_request_template.md
