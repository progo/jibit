(ns common.human
  "Transform numerical values into commonly understood human-readable values."

  (:require [#?(:clj clojure.pprint
                :cljs cljs.pprint)
             :refer [cl-format]]

            #?(:cljs jibit.datetime)

            [taoensso.timbre :as timbre :refer [debug spy]]))

(defn focal-length
  "Focal lengths in mm in short and sharp format. Focal lengths under 10
  mm are shown as 0.0 and focal lengths over 10 mm are 00 mm."
  [fl]
  (if (< fl 9.95)
    (cl-format nil "~,1f" fl)
    (cl-format nil "~0d" (Math/round fl))))

(defn aperture
  "Aperture values in concise human-readable format. 1/2 and 1/3 stops
  are plenty accurate. (Round others into these increments.)"
  [f]
  (cl-format nil "ƒ/~,1f" f))

(defn shutter-speed
  [ss]
  (cond
    (>= ss 1) (str ss)
    (< ss 1) (cl-format nil "1/~D" (Math/round (/ ss)))))

(defn exp-comp
  [ec]
  (if (zero? ec)
    "0"
    (cl-format nil "~,2@f" ec)))

(defn gear-label
  [gear]
  (cond
    (:user_label gear) (:user_label gear)
    :else (str (:exif_make gear) \space (:exif_model gear))
    ))

#?(:cljs
   (do
     (defn datestamp
       "Format a datetime as a date."
       [dt]
       (jibit.datetime/format "MMM D YYYY" dt))))
