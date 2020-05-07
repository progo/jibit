(ns clurator.view.gear
  (:require [clurator.model.gear :as model.gear]
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.view.filtering :as filtering]))

(defn keywordify-map
  "Map `m` that has string keys, make them keyworded"
  [m]
  (into {} (map (fn [[k v]] [(keyword k) v]) m)))

(defn list-gear
  [req]
  {:resp {:gear (model.gear/list-all-gear)}})

(defn update-gear
  [req]
  (let [new-gear (for [item (:gear-data (filtering/read-edn req))]
                   (keywordify-map (select-keys item ["id" "user_label"])))]
    (model.gear/update-gear! new-gear)
    {:resp :ok}))
