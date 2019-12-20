# loki

Command line utility to query JDBC-like databases with Clojure REPL.

[![asciicast](https://asciinema.org/a/LlFDoQFtDnvDPQnSHLSlgWlJz.svg)](https://asciinema.org/a/LlFDoQFtDnvDPQnSHLSlgWlJz?autoplay=true&loop=true&speed=2&size=big&rows=15)

In a nutshell, it's just
[next-jdbc](https://github.com/seancorfield/next-jdbc) + [rebel readline](https://github.com/bhauman/rebel-readline) + `clojure.pprint`,

so big thanks to developers and maintainers of those tools.

## Goals

This tool can be a quite nice entry-point to Clojure REPL-driven development. The main goal is to keep it simple for the developers who
are only about to enter the joyful world of LISPS.
It's not intended to replace the more complex tools like `DataGrip` or similar, but can be quite handy for something relatively small,
especially when it comes to comparing/merging the results from different database engines. It also allows you to avoid leaving
cosy and warm terminal environment.

## Usage

### Make sure [clojure command-line tools](https://clojure.org/guides/getting_started) are installed

### Build loki jar

```bash
clojure -Adepstar loki.jar
```

```bash
LOKI_DATABASES='{:h2 {:dbtype "h2" :dbname "example"}}' java -cp loki.jar clojure.main -m loki.core

# (<MODE> <DATABASE NAME> <QUERY>)
# WHERE
# MODE = q|q!|qt|qp
# DATABASE NAME = :config-keyword
# QUERY = "STRING" | symbols
```

## Configuration

The configuration is `EDN` string with the list of database configurations you'd like to query. The format should reflect the requirements
listed in [next-jdbc documentation](https://cljdoc.org/d/seancorfield/next.jdbc/1.0.12/api/next.jdbc#get-datasource)
Any database driver that you plan to use should be in the classpath.

### Example
```bash
LOKI_DATABASES='{:postgres {:user "postgres" :password "postgres" :dbtype "postgres"}}' java -cp loki.jar:$HOME/Downloads/postgresql-42.2.9.jar clojure.main -m loki.core
```

## Modes

### 1. Print the output of the SQL query as an ASCII-table

```clojure
(qt :h2 SELECT * FROM users LIMIT 4)

;| :USERS/ID | :USERS/NAME |        :USERS/EMAIL |
;|-----------+-------------+---------------------|
;|         1 |    John Doe | johndoe@example.org |
;|         2 |    John Doe | johndoe@example.org |
;|         3 |    John Doe | johndoe@example.org |
;|         4 |    John Doe | johndoe@example.org |
```

### 2. Output the query results as list of clojure maps

```clojure
(def result (q! :h2 SELECT * FROM users LIMIT 4))
(first result)

#:USERS{:ID 1, :NAME "John Doe", :EMAIL "johndoe@example.org"}
```
This comes handy when you want to do some post-processing on the data:

```clojure
(count result)
4
(->> result
     (map :USERS/ID)
     (reduce +))
; 10 - the sum of all the IDs in the result
(->> result
     (filter (fn [row] (= (get row :USERS/NAME) "John Doe")))
     (map :USERS/ID))
; (1 2 3 4)
```

### 3. Printing and keeping the output

```clojure
(def result (qp :h2 SELECT * FROM users LIMIT 5))
; will print the table and keep the result set in `result`
```

### 4. Querying efficiently and saving larger datasets to CSV

Read more information about reducible results in the [next-jdbc documentation](https://github.com/seancorfield/next-jdbc/blob/master/doc/getting-started.md#plan--reducing-result-sets)

```clojure
(def result (q :h2 SELECT * FROM users))
(into #{} (comp (map :ID) (filter #(< % 5))) result)
#{1 4 3 2}

(->csv (q :h2 SELECT * FROM users) "results.csv")
; will save the results of the query lazily to "results.csv"
```

### 5. Interactive mode
```clojure
(qt :h2 SELECT * FROM users)
(interact!)
;...
;SELECT *
;FROM users
;LIMIT 10;
; -- will print the result as a table
;stop;
; -- will stop the interactive mode
```

## Context
The last configuration key that was used is stored as an [atom](https://clojure.org/reference/atoms).
This way, it can be omitted on the subsequent queries:
```clojure
(qt :h2 SELECT 1)
;...

(qt SELECT 1)
;...
```

## Macro & limitations

All the previously mentioned modes are implemented as a macro.
It makes it a bit harder to construct the queries beforehand.
For those scenarios, there is a function called `query-database!`

```clojure
(def query "SELECT 1")
(query-database! :h2 query)
```

All the macros treat the symbols that go after the configuration as a string. To be 100% sure about your query, it's usually nice
to wrap it into double quotes, because there are certain limitations that I'd try to explain by example.

The following statements are the same:

```clojure
(qt "SELECT 'test'")
(qt SELECT "'test'")
; the following will not work!
(qt SELECT 'test')
; because "'" sign is converted to (quote ...)
(macroexpand '(SELECT 'test'))
(let* [result (query-database! :h2 "SELECT (quote test')")] (clojure.pprint/print-table result) nil)')
;...
(qt "SELECT 1, 2, 3, 4")
(qt SELECT 1 "," 2 "," 3 "," 4)
; the following will not work!
(qt SELECT 1, 2, 3, 4)
; because "," sign is ignored this way:
(macroexpand '(SELECT 1,2,3,4,5))
;(let* [result (query-database! :h2 "SELECT 1 2 3 4 5")] (clojure.pprint/print-table result) nil)
```
*(!)* So please, be careful and don't ruin any production database and don't run any scary INSERT/TRUNCATE/DROP queries using this tool
if you're not sure *(!)*.

Except the standard macro, there are a couple of more available that can make life a bit easier:
```clojure
(SELECT 1)
; equals to (qt <CURRENT CONFIG> SELECT 1)
(SHOW TABLES FROM ...)
; equals to (qt <CURRENT CONFIG> SHOW TABLES FROM ...)
(DESCRIBE ...)
; equals to (qt <CURRENT CONFIG> DESCRIBE ...)
```
