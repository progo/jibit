(ns clurator.model.gear
  (:require [clurator.db :as db]
            [taoensso.timbre :as timbre :refer [debug spy]]))

(defn list-all-gear
  []
  (db/query! {:select [:*]
              :from [:gear]
              :order-by [:user_label :exif_make :exif_model]}))

(defn update-gear!
  "We are getting a seq of maps {:id :user_label} that denote new user
  labels."
  [gear-maps]
  (doseq [{:keys [:id :user_label]} gear-maps]
    (let [user-label (if (empty? user_label) nil user_label)]
      (db/query! {:update :gear
                  :set {:user_label user-label}
                  :where [:= :id id]}))))
