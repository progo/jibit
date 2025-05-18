(ns clurator.llm
  "Interfacing with LLMs.
  We have a middle man here, `inqu`, using Python library ecosystem."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [me.raynes.fs :as fs]
            [jsonista.core :as json]
            [clurator.model.photo :as photo]
            [juxt.dirwatch :as dirwatch]
            popen))


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



(defn insert-description
  "Read and parse and insert a description which is a JSON string at
  this point."
  [desc]
  (let [parsed-desc (json/read-value desc)
        desc-slip (massage-descs [parsed-desc])]
    (photo/fill-llm-descs-in-db desc-slip)))

(defn rename-descfile-as-done
  [descfile]
  (fs/rename descfile (str descfile ".proc")))

;;;;;;;;
;; concurrent variant:
;; - mkdir tmp
;; - launch inqu on it watching
;; - do the export batch
;; - watch the dir for `descfiles'
;; - add any new descfiles to db as they appear
;; - close dir watching when inqu process terminates
;;
;; So there'll be 2 threads. One launches inqu and dirwatching, the
;; other does the export.

(defn -do-export [batch-size tmpdir]
  (debug "exporting " batch-size " photos into " tmpdir)
  (clurator.export/export-resize-photos
   :scheme-key :unsharp1600
   :photos (clurator.model.photo/get-llm-undescribed-photos batch-size)
   :output-dir (str tmpdir)
   :output-namer-fn clurator.export/format-output-file-name-as-uuid))

;; OBS!!! Each descfile seems to get one :create and one :modify event.
;; We will just have to rely on the file being written all at once. If
;; JSON parsing fails, we can try the file again after the next event.
(defn -on-dir
  [args]
  (let [modify? (= :modify (-> args :action))
        file (-> args :file)
        filename (fs/base-name file)
        descfile? (string/ends-with? filename ".desc.json")]
    (when (and descfile? modify?)
      (debug "dir-event: processing a new descfile" filename)
      (insert-description (slurp file))
      (rename-descfile-as-done file))))

(defn -launch-and-monitor-inqu [tmpdir]
  (debug "Launching inqu on " tmpdir)
  (let [inquproc (popen/popen [inqu (str tmpdir)])]
    (debug "Launching dirwatching on " tmpdir)
    (let [watchproc (dirwatch/watch-dir -on-dir tmpdir)]
      (popen/join inquproc)
      (debug "inqu is finished, stopping watching " tmpdir)
      (dirwatch/close-watcher watchproc)
      (debug "all done, removing temps " tmpdir)
      (fs/delete-dir tmpdir))))

(defn describe-random-photos [batch-size verbose?]
  (let [tmpdir (fs/temp-dir "inqu-")
        export-thread (Thread. (fn [] (-do-export batch-size tmpdir)))
        monitor-thread (Thread. (fn [] (-launch-and-monitor-inqu tmpdir)))]
    (.start export-thread)
    (.start monitor-thread)
    (.join export-thread)
    (.join monitor-thread)))



;; this linear single thread version can't report progress all that well.
(defn describe-random-photos*** [batch-size]
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
