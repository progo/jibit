(ns clurator.model.gear
  (:require [clurator.db :as db]
            [taoensso.timbre :as timbre :refer [debug spy]]))

;; We read from composite view VW_GEAR...

(defn list-all-gear
  []
  (db/query! {:select [:*] :from [:vw_gear]}))


(defn destructure-key
  "key-str is of form `cam-#' or `lens-#' where # is an integer pointing
  to either CAMERA or LENS table column ID. Split this input in half
  and return the table name (keyword) and ID (string) as 2-tuple."
  [^String key-str]
  (let [[type id] (.split key-str "-")]
    [(case type
       "cam" :camera
       "lens" :lens)
     id]))

(defn update-gear!
  "We are getting a seq of maps {:id :user_label} that denote new user
  labels. The ids are compositions of two different tables plus their
  ids so we destructure those."
  [gear-maps]
  (doseq [{:keys [:id :user_label]} gear-maps]
    (let [[table id] (destructure-key id)
          user-label (if (empty? user_label) nil user_label)]
      (db/query! {:update table
                  :set {:user_label user-label}
                  :where [:= :id id]}))))
