;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; We shouldn't go for this route if we do controlled versions. We
;; know what the schema contains!

;; Altering a table in SQLite with quiet duplicate handling

(defn alter-schema-quiet-dupes!
  "Run a raw DDL query `alter-query` against `db` and try to silence
  common duplicate issues that are not harmful in a casual dev
  migration context.

  Reads and naively understands SQLite errors, currently handles:

  - creating an index
  - creating a table
  - adding new column to a table

  Return :ok when the query ran without hitch. Returns :dupe when the
  name collision (ie likely duplicate) was detected. Raises the
  exceptions in other events.
  "
  [db alter-query]
  (try
    (jdbc/execute! db [alter-query])
    :ok
    (catch org.sqlite.SQLiteException e
      (let [err-msg (.getMessage e)]
        (prn err-msg)                   ; TODO proper log would be nice
        (cond
          (re-find #"duplicate column name: " err-msg) :dupe
          (re-find #"index .* already exists" err-msg) :dupe
          (re-find #"table .* already exists" err-msg) :dupe
          :else (throw e))))))


(comment
(alter-schema-quiet-dupes! db "alter table photos add rating int")
(alter-schema-quiet-dupes! db "create index ix_photo_rating on photos(rating)")
(alter-schema-quiet-dupes! db "create table photos (id integer primary key)")
)
