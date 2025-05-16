(ns clurator.llm
  "Interfacing with LLMs.
  We have a middle man here, `inqu`, using Python library ecosystem."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [clojure.java.shell :as shell]
            [me.raynes.fs :as fs]
            [jsonista.core :as json]
            [clurator.model.photo :as photo]
            ))


;; Finding the project root is not possible. fs/*cwd* is not
;; necessarily reliable although it is probably the thing that we're
;; going to be using.
(def inqu "/home/progo/koodi/jibit/inqu.sh")


;; We should think about strategies too (or maybe it doesn't matter
;; besides initial population. At first, now, we do random feeding and
;; when everything is fed, probably oldest-first approach is good.
;; Or newest-first too, hmm.

(defn massage-descs
  [descs]
  (for [desc descs]
    {:id (-> desc
             (get "image_path")
             fs/base-name
             fs/split-ext
             first)
     :description (get desc "description")}))


(defn describe-random-photos [batch-size]
  (let [tmpdir (fs/temp-dir "inqu-")]

    (clurator.export/export-resize-photos
     :scheme-key :unsharp1600
     :photos (clurator.model.photo/get-llm-undescribed-photos batch-size)
     :output-dir (str tmpdir)
     :output-namer-fn clurator.export/format-output-file-name-as-uuid)

    (cond
      (nil? (fs/list-dir tmpdir))
      {:msg "All files described (tmpdir empty)"}

      :else
      (let [inqu-response (shell/sh inqu (str tmpdir))]
        (cond
          ;; handle nonzeros
          (not= 0 (:exit inqu-response))
          {:error true
           :msg "nonzero exit from inqu"
           :details inqu-response}

          ;; handle error messaging
          (not (empty? (:err inqu-response)))
          {:error true
           :msg "stderr"
           :details inqu-response}

          :else
          (do
            (let [raw-descs (json/read-value (:out inqu-response))
                  descs (massage-descs raw-descs)]
              ;; And we send the seq  over to a function inside model.photo ns.
              (photo/fill-llm-descs-in-db descs)

              (fs/delete-dir (str tmpdir))
              descs)))))))




