(ns common.human
  "Transform numerical values into commonly understood human-readable values."

  (:require [#?(:clj clojure.pprint
                :cljs cljs.pprint)
             :refer [cl-format]]

            #?(:cljs jibit.datetime)

            clojure.string
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
  ;; ditch ƒ/ for now
  (cl-format nil "~,1f" f))

(defn shutter-speed
  "Format shutter speeds in commonly accepted forms.

  Values like '0.01' seconds are formed as '1/100',
  near-second values like '0.666667' are formed as '0.7'''
  and round seconds are 1''
  "
  [ss]
  (cond
    (<= ss 0.5) (cl-format nil "1/~D" (Math/round (/ ss)))
    (> ss 0.5) (if (integer? ss)
                 (cl-format nil "~D\"" ss)
                 (cl-format nil "~,1F\"" ss))))

(defn exp-comp
  [ec]
  (if (zero? ec)
    "0"
    (cl-format nil "~,1@f" ec)))

(defn iso
  [iso]
  (Math/round iso))

(defn gear-label
  [gear]
  (clojure.string/trim
   (cond
     (:user_label gear) (:user_label gear)
     :else (str (:exif_make gear) \space (:exif_model gear)))))

#?(:cljs
   (do
     (defn datestamp
       "Format a datetime as a date."
       [dt]
       (jibit.datetime/format "MMM D YYYY" dt))))
