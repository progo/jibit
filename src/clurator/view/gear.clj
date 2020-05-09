(ns clurator.view.gear
  (:require [clurator.model.gear :as model.gear]
            [taoensso.timbre :as timbre :refer [debug spy]]
            clurator.utils
            [clurator.view.filtering :as filtering]))

(defn list-gear
  [req]
  {:resp {:gear (model.gear/list-all-gear)}})

(defn update-gear
  [req]
  (let [new-gear (for [item (:gear-data (filtering/read-edn req))]
                   (clurator.utils/keywordify-map (select-keys item ["id" "user_label"])))]
    (model.gear/update-gear! new-gear)
    {:resp :ok}))
