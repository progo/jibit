(ns clurator.llm
  "Interfacing with LLMs.
  We have a middle man here, `inqu`, using Python library ecosystem."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            ))


(comment
  (clurator.export/export-resize-photos
   :scheme-key :unsharp1600
   :photos (clurator.model.photo/get-llm-undescribed-photos 15)
   :output-dir "/tmp/dayo"
   :output-namer-fn clurator.export/format-output-file-name-as-uuid)
  )
