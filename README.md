WeLite
======

WeLite is a Kotlin DSL, and Statement/Cursor Wrapper, for Android SQLite 

### The goals of this library are:
  * Provide a DSL for Android SQLite that fully (eventually) encapsulates the underlying SQL
  * Clients write Kotlin - no SQL, no annotation processing
  * Support Kotlin coroutines via suspend methods and an injected coroutine dispatcher
  * Encapsulate SQLite statements, eg. insert/update/delete, for reuse and simplified binding   
  * Encapsulate Select results and provide for easy row->POJO conversion

  
### Why this library?

After using a mature, Java, in-house wrapper for years and then evaluating Room, each solution had extensive SQL throughout the code. For WeLite the desire is to push all SQL down into the library and treat it as an implementation detail of interfacing with the persistence layer. Clients should use a Kotlin DSL to describe tables and relationships, and then use the resulting objects for CRUD work, keeping SQL and SQLite API hidden (objects versus SQL string handling). 

There is no goal for this to be an ORM of any type and it only wraps SQLite as shipped in Android. Because of the impedance mismatch between OO and RDBMS, ORMs are hard and, we would argue, often not needed. Instead, this library simplifies reading rows and converting them to POJOs efficiently using RDBMS features, primarily supporting simple types, and putting few lines of the Relational-OO mapping in the client code (specifying which columns are needed to construct a POJO).   

Both the [Squash] and [Exposed] libraries influence this library, primarily the DSL code. However, WeLite calls directly into the Android SQLite API and does not go through a JDBC driver, which alleviates the need (and code) to deal with variations in SQL and RDBMS features. Also, the WeLite design philosophy differs from these other libraries in specific areas. Pre 1.0 versions may see significant API changes.       


### The name?

Lite=SQLite and We="without entities". After attempting to port an in-house solution to Room and searching for other libraries, it was decided to build a thin wrapper over the SQLite API with a Kotlin DSL to build the underlying SQL. 




[squash]: https://github.com/orangy/squash
[exposed]: https://github.com/JetBrains/Exposed
[coroutines]: https://kotlinlang.org/docs/reference/coroutines-overview.html
[flow]: https://kotlinlang.org/docs/reference/coroutines/flow.html  
[splitties]: https://github.com/LouisCAD/Splitties
