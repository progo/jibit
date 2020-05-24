(ns clurator.exiftool
  "Minimal exiftool wrapper"
  (:require [jsonista.core :as json]
            popen))

(def exiftool-args
  ["exiftool"
   "-stay_open" "True"                  ; Persistent process
   "-@" "-"                             ; Read from stdin
   ])

;; TODO Wrap in an agent or something? Ideally we want multiprocess in
;; the future, restartability.

(let [ep (popen/popen exiftool-args)]
  (def exiftool-process ep)
  (def exiftool-input (popen/stdin ep))
  (def exiftool-output (popen/stdout ep)))

(defn send-message!
  [msg-lines]
  (doto exiftool-input
    (.write (clojure.string/join \newline msg-lines))
    (.write "\n")
    (.flush)))

;; Smoke test, we'll know that the process is receiving messages if it
;; dies from (shutdown!)

(defn shutdown! []
  (send-message! ["-stay_open" "False"]))

(defn read-response! []
  (clojure.string/join
   "\n"
   (for [line (line-seq exiftool-output)
         :while (not= line "{ready}")]
     line)))

(defn get-exif-json
  "Send a file parse request to the persistent exiftool process,
  blockingly read its response and convert it into a stringly keyed
  map."
  [file-path]
  (send-message! ["-j" file-path "-execute"])
  (json/read-value (read-response!)))

(defn extract-preview-files
  [file directory]
  (send-message!
   ["-a" "-b" "-W"
    (str directory "/%t%-c.%s")
    "-preview:all"
    (str file)
    "-execute"])
  ;; We'll ignore the response (exiftool would report how many images
  ;; it was able to extract from the input) but it has to be read.
  (read-response!))

(comment
  (get-exif-json "/home/progo/panasonic-test-material/2020-05-16-15-50-P1020617.jpg")
  )

