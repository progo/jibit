(ns clurator.exiftool
  "Minimal Exiftool wrapper. The function you want is `get-exif-map`
  which returns a full map of information that Exiftool would report
  on a command-line invocation.

  To extract thumbnails using Exiftool there's `extract-preview-files`."
  (:require [jsonista.core :as json]
            clurator.utils
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

;; NB: would work easily with multiple files, or a directoryful at a
;; time. Exiftool is returning us an array of objects [{}, {}, ...].
;; Since we currently only go one at a time, we call `first` to get
;; into the map.
(defn get-exif-map
  "Send a file parse request to the persistent exiftool process,
  blockingly read its response and convert it into a stringly keyed
  map."
  [file-path]
  (send-message! ["-j" file-path "-execute"])
  (first (json/read-value (read-response!))))

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
  (get-exif-map "/home/progo/panasonic-test-material/2020-05-16-15-50-P1020617.jpg")
  )

