(ns clurator.llm
  "Interfacing with LLMs.
  We have a middle man here, `inqu`, using Python library ecosystem."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [clojure.java.shell :as shell]
            [me.raynes.fs :as fs]
            [jsonista.core :as json]))


;; Finding the project root is not possible. fs/*cwd* is not
;; necessarily reliable although it is probably the thing that we're
;; going to be using.
(def inqu "/home/progo/koodi/jibit/inqu.sh")



(comment
  (shell/sh inqu)

  (clurator.export/export-resize-photos
   :scheme-key :unsharp1600
   :photos (clurator.model.photo/get-llm-undescribed-photos 15)
   :output-dir "/tmp/dayo"
   :output-namer-fn clurator.export/format-output-file-name-as-uuid)
  )
