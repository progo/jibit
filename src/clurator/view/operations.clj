(ns clurator.view.operations
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            clurator.utils
            clurator.inbox
            clurator.export
            clurator.settings
            clurator.model.photo
            [clurator.view.filtering :as filtering]))

(defn sync-inbox
  "Going to sync inbox in synchronously here. We don't need any
  arguments from request."
  [req]
  {:resp (clurator.inbox/process-inbox! clurator.settings/inbox-path)})


(defn export-photos
  "Export selected photos into a dir specified by settings. Resize for
  web consumption, currently hardcoded behavior."
  [req]
  (let [parsed-edn (-> req filtering/read-edn)
        photos (clurator.model.photo/get-photos-by-id
                (:photos parsed-edn))]
    (clurator.export/export-resize-photos
     :scheme-key (:template parsed-edn)
     :photos photos
     :output-dir clurator.settings/outbox-path
     :output-namer-fn clurator.export/format-output-file-name-as-date)
    {:resp true}))
