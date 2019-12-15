(ns loki.core
  (:gen-class)
  (:require [rebel-readline.main :as rrl]
            [clojure.string :as string]
            [next.jdbc :as jdbc]
            [clojure.spec.alpha :as s]
            [cyrus-config.core :as cfg]
            [cyrus-config.coerce :as cfg-coerce]
            [taoensso.timbre :as log]
            [clojure.pprint :refer [pprint]]
            [clojure.repl :as clj-repl]
            [clojure.data.csv :as data-csv]
            [clojure.java.io :as io]
            [rebel-readline.core :as rebel-core]
            [rebel-readline.clojure.main :as rebel-clj-main]))

(defonce current-config
  (atom nil))

(defn -handle-sigint-form
  []
  `(let [thread# (Thread/currentThread)]
     (clj-repl/set-break-handler! (fn [_signal#] (.stop thread#)))))

(cfg/def loki-log-level "logging level"
  {:spec keyword?
   :default :debug})

(cfg/def loki-databases "map of the database configurations"
  {:spec (cfg-coerce/from-edn (s/map-of keyword? any?))
   :required false
   :default {:h2 {:dbtype "h2"
                  :dbname "example"}}})

(def loki-datasources
  (into {} (map (juxt key #(jdbc/get-datasource (val %)))) loki-databases))

(defn query-database [config query]
  (reset! current-config config)
  (jdbc/plan (get loki-datasources config) [query]))

(defn query-database! [config query]
  (reset! current-config config)
  (jdbc/execute! (get loki-datasources config) [query]))

(defn ->csv [result-set filename]
  (with-open [writer (io/writer filename)]
    (let [header (keys (into {} result-set))]
      (data-csv/write-csv
       writer
       (conj
         (into '() (map (apply juxt header)) result-set) (map name header))))))

(defmacro q [config & body]
  (if (keyword? config)
    (list 'query-database
          config
          (apply str (interpose " " (map str body))))
    (list 'query-database
          @current-config
          (apply str config " " (interpose " " (map str body))))))

(defmacro q! [config & body]
  (if (keyword? config)
    (list 'query-database!
          config
          (apply str (interpose " " (map str body))))
    (list 'query-database!
          @current-config
          (apply str config " " (interpose " " (map str body))))))

(defmacro qp [config & body]
  (list 'let ['result (if (keyword? config)
                        (list 'query-database!
                              config
                              (apply str (interpose " " (map str body))))
                        (list 'query-database!
                              @current-config
                              (apply str config " " (interpose " " (map str body)))))]
        (list 'clojure.pprint/print-table 'result)
        'result))

(defmacro qt [config & body]
  (list 'let ['result (if (keyword? config)
                        (list 'query-database!
                              config
                              (apply str (interpose " " (map str body))))
                        (list 'query-database!
                              @current-config
                              (apply str config " " (interpose " " (map str body)))))]
        (list 'clojure.pprint/print-table 'result)
        'nil))

(defmacro SELECT [& body]
  (list 'let ['result (list 'query-database!
                            @current-config
                            (apply str "SELECT " (interpose " " (map str body))))]
        (list 'clojure.pprint/print-table 'result)
        'nil))

(defmacro SHOW [& body]
  (list 'let ['result (list 'query-database!
                            @current-config
                            (apply str "SHOW " (interpose " " (map str body))))]
        (list 'clojure.pprint/print-table 'result)
        'nil))

(defmacro DESCRIBE [& body]
  (list 'let ['result (list 'query-database!
                            @current-config
                            (apply str "DESCRIBE " (interpose " " (map str body))))]
        (list 'clojure.pprint/print-table 'result)
        'nil))

(defn get-docstring []
  (let [any-db (first (keys loki-databases))]
    (format 
     "

 ██▓     ▒█████   ██ ▄█▀ ██▓
▓██▒    ▒██▒  ██▒ ██▄█▒ ▓██▒
▒██░    ▒██░  ██▒▓███▄░ ▒██▒
▒██░    ▒██   ██░▓██ █▄ ░██░
░██████▒░ ████▓▒░▒██▒ █▄░██░
░ ▒░▓  ░░ ▒░▒░▒░ ▒ ▒▒ ▓▒░▓  
░ ░ ▒  ░  ░ ▒ ▒░ ░ ░▒ ▒░ ▒ ░
  ░ ░   ░ ░ ░ ▒  ░ ░░ ░  ▒ ░
    ░  ░    ░ ░  ░  ░    ░  
                            
To query the data, use the following pattern:
(qp <DATABASE-NAME> \"<QUERY>\")
DATABASE-NAME can be (\":\" colon is obligatory):
 - %s

The result of the query would be displayed on the screen and you'd also get it as a response value you can work with using awesome clojure functions!
(first (q! %s \"SELECT 1\"))

To avoid any output values and only show table - use `qt`
(qt %s \"SELECT 12\")

You can also write CSV, even if the query returns HUUUUGE result:
(->csv (q %s SELECT * FROM schema.enormous_data_set) \"result.csv\")

Once you query the database for the second time, you can run `qt` macro without specifying ANYTHING - it's context is saved/modified each time you run `qt`/`qp`/`q`!

(SELECT * FROM some.table LIMIT 10)
  " (string/join "\n - " (keys loki-databases)) any-db any-db any-db any-db)))

(defn -main [& args]
  (cfg/validate!)
  (log/merge-config! {:level (keyword loki-log-level)})
  (rebel-core/ensure-terminal
   (rebel-clj-main/repl*
    {:init (fn []
             (require 'loki.core)
             (in-ns 'loki.core)
             (println (get-docstring)))
     :eval (fn [form]
             (eval `(do ~(-handle-sigint-form) ~form)))})))
