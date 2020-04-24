(ns clurator.view.photo
  (:require
   [ring.util.codec :refer [form-decode]]
   [clurator.model.photo :as model.photo]
   [clurator.view.filtering :as view.filtering]
   [clurator.settings]
   ))

(defn list-photos
  [req]
  {:resp (model.photo/filter-photos
          (view.filtering/handle-filter-criteria
           (form-decode (:query-string req))))})

(defn serve-thumbnail-by-uuid
  [uuid]
  (java.io.File. (str clurator.settings/thumbnail-dir "/" uuid ".jpeg")))

(defn serve-full-by-uuid
  [uuid]
  (let [storage (-> (clurator.model.photo/get-by-uuid uuid)
                    :photo/storage_filename)]
    (java.io.File. (str clurator.settings/storage-directory "/" storage))))
