(ns clurator.view.operations
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            clurator.utils
            clurator.inbox
            clurator.settings
            [clurator.view.filtering :as filtering]))

(defn sync-inbox
  "Going to sync inbox in synchronously here. We don't need any
  arguments from request."
  [req]
  {:resp (clurator.inbox/process-inbox! clurator.settings/inbox-path)})
