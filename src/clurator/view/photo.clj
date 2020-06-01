(ns clurator.view.photo
  (:require
   [ring.util.codec :refer [form-decode]]
   [clurator.model.photo :as model.photo]
   [clurator.view.filtering :as view.filtering]
   [clurator.settings]
   [taoensso.timbre :as timbre :refer [debug spy]]

   ;; TODO move the system into inbox?
   clurator.inbox
   [me.raynes.fs :as fs]
   ))

(defn list-photos
  [req]
  {:resp (model.photo/filter-photos
          (view.filtering/handle-filter-criteria
           (form-decode (:query-string req))))})

(defn upload-photos
  [req]
  (let [payload (-> req :multipart-params (get "upload"))
        files (seq
               ;; Just one file
               (if (map? payload) [payload] payload))]
    (if files
      (do
        (debug "\nFiles..." files)

        ;; We create a temporary structure, copy the input into their
        ;; orig filenames into the structure, and then issue the
        ;; import process on that temporary structure.

        (let [temp* (fs/temp-dir "jibit-upload-")
              temp (str temp* "/uploaded")]
          (fs/mkdir temp)
          (debug "Temp dir" temp)
          (doseq [f files]
            (fs/copy (:tempfile f)
                     (str temp "/" (:filename f))))
          (let [in (clurator.inbox/process-inbox! temp)]
            {:resp in})))

      ;; no files
      {:resp []})))

(defn serve-thumbnail-by-uuid
  [uuid]
  (java.io.File. (str clurator.settings/thumbnail-dir "/" uuid ".jpeg")))

(defn serve-full-by-uuid
  [uuid]
  (let [storage (-> uuid clurator.model.photo/get-by-uuid :storage_filename)]
    (java.io.File. (str clurator.settings/storage-directory "/" storage))))
