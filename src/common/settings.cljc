(ns common.settings
  "Transform numerical values into commonly understood human-readable values."

  )


;; Export schemes
(def export-schemes
  ""
  (array-map
   :sharp1600 {:name "1600px sharpened"
               :key :sharp1600
               :method :convert
               :args ["-resize" "1600x1600>"
                      "-quality" "90"
                      "-unsharp" "0x0.75+0.75+0.008"]}
   :unsharp1600 {:name "1600px unsharpened"
                 :key :unsharp1600
                 :method :convert
                 :args ["-resize" "1600x1600>"
                        "-quality" "90"]}
   :whitebox {:name "1920px whitebox unsharpened"
              :key :whitebox
              :method :convert
              :args ["-resize" "1920x1920>"
                     "-quality" "90"
                     "-bordercolor" "white"
                     "-border" "6"]}
   :full {:name "Untouched"
          :key :full
          :method :passthrough}))


