(ns clurator.human
  "Turn floating-point values into human-readable strings.")

;; TODO this is probably an ideal candidate for CLJC action. We're
;; going to need this in JS. Then again, we are probably not going to
;; need this in CLJ side so there's that.

(defn focal-length
  "Focal lengths in mm in short and sharp format. Focal lengths under 10
  mm are shown as 0.0 and focal lengths over 10 mm are 00 mm."
  [^Double fl]
  (format (if (>= fl 9.95)
            "%.0f"
            "%.1f")
          (double fl)))


(defn aperture
  "Aperture values in concise human-readable format. 1/2 and 1/3 stops
  are plenty to support. Round others into these increments."
  [f]
  (format "ƒ/%.1f" f))


(defn exp-comp
  [ec]
  (if (zero? ec)
    "0"
    (format "%+.2f" ec)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  (map (juxt identity human/aperture)
       (into #{} (map :photo/aperture
                      (-> (sql/format {:select [:aperture]
                                       :from [:photo]})
                          db/query!))))

  (into #{}
        (apply concat
               (for [k [:focal_length_35 :focal_length]]
                 (map (keyword "photo" (name k)) (db/query! (sql/format {:select [k] :from [:photo]}))))))
)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(comment
  ;; test material for FL

([35.0 "35"]
 [70.0 "70"]
 [40.0 "40"]
 [5.8 "5.8"]
 [9.9 "9.9"]
 [10.0 "10"]
 [12.0 "12"]
 [200.0 "200"]
 [102.0 "102"]
 [28.0 "28"]
 [58.0 "58"]
 [93.0 "93"]
 [97.0 "97"]
 [127.0 "127"]
 [39.0375475273773 "39"]
 [68.3157081729103 "68"]
 [56.6044439146971 "57"]
 [99.5457461948122 "100"]
 [52.9999999999999 "53"]
 [123.944213399423 "124"]
 [50.0000000000001 "50"]
 [34.1578540864552 "34"]
 [195.187737636887 "195"]
 [24.0370085030933 "24"]
 [94.66605275389 "95"]
 [35.1843153279888 "35"]
 [90.7622980011523 "91"])


;; test material for apertures

([2.0 "ƒ/2.0"]
 [4.0 "ƒ/4.0"]
 [8.0 "ƒ/8.0"]
 [5.6 "ƒ/5.6"]
 [1.4 "ƒ/1.4"]
 [4.5 "ƒ/4.5"]
 [18.0 "ƒ/18.0"]
 [5.0 "ƒ/5.0"]
 [1.7 "ƒ/1.7"]
 [6.3 "ƒ/6.3"]
 [3.5 "ƒ/3.5"]
 [14.0 "ƒ/14.0"]
 [2.6 "ƒ/2.6"]
 [3.2 "ƒ/3.2"]
 [5.57897466540162 "ƒ/5.6"])
)
