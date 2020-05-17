(ns clurator.cli
  "The command-line interface."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            clurator.inbox
            clurator.server
            clurator.settings))

(def cli-options
  [["-v" "--verbose"]
   ["-h" "--help"]
   ["-p" "--port PORT" "Run the server on this port"
    :default 8088
    :parse-fn #(Integer/parseInt %)]])

(defn usage [summary]
  (->> ["JIBIT"
        ""
        "Options:"
        summary
        ""
        "Actions:"
        "  import [DIR [DIR...]]   Import photos from specified DIR(s)"
        (str "                          (default: " clurator.settings/inbox-path ")")
        "  server                  Launch the server to use Jibit in browser"]
       (string/join \newline)))

(defn parse-arguments
  [argv]
  (let [{:keys [options arguments errors summary]} (parse-opts argv cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary)}

      errors
      {:exit-message errors}

      (and (seq arguments)
           (#{"import" "server"} (first arguments)))
      {:action (first arguments)
       :args (rest arguments)
       :options options}

      :else
      {:exit-message (usage summary)})))
