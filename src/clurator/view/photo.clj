(ns clurator.view.photo
  (:require
   [ring.util.codec :refer [form-decode]]
   [clurator.model.photo :as model.photo]
   [clurator.view.filtering :as view.filtering]))

(defn list-photos
  [req]
  {:resp (model.photo/filter-photos
          (view.filtering/handle-filter-criteria
           (form-decode (:query-string req))))})
