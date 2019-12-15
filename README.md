# loki

Command line utility to query JDBC-like databases with Clojure REPL.

[![asciicast](https://asciinema.org/a/VJSgJNCN8irlaWuDDVnFa8Qjf.svg)](https://asciinema.org/a/VJSgJNCN8irlaWuDDVnFa8Qjf?autoplay=true&loop=true&speed=2&size=big&rows=15)

In a nutshell, it's just
[next-jdbc](https://github.com/seancorfield/next-jdbc) + [rebel readline](https://github.com/bhauman/rebel-readline) + `clojure.pprint`,

so big thanks to developers and maintainers of those tools


## Usage

```
clojure -Adepstar loki.jar
java -cp loki.jar clojure.main -m loki.core
```
