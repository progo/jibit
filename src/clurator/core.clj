(ns clurator.core
  (:require [clurator.cli :as cli]
            [clurator.inbox :as inbox]
            [clurator.server :as server]
            [taoensso.timbre :as timbre :refer [debug spy]])
  (:gen-class))

(defn -main [& argv]
  (let [{:keys [exit-message action args options]} (cli/parse-arguments argv)]
    (cond
      exit-message
      (println exit-message)

      (= action "server")
      (let [port (:port options)]
        (when (seq args)
          (timbre/warn "Ignoring extraneous crap:" args))
        (timbre/info "Running server at port" port)
        (server/run-server port))

      (= action "import")
      (do
        (doseq [dir (or (seq args) [clurator.settings/inbox-path])]
          (println (format "Importing from %s... " dir))
          (println (format "*** %d imported."
                           (-> (inbox/process-inbox! dir)
                               :total-files))))
        (shutdown-agents)))))
