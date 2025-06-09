(ns jibit.datetime
  (:require ["moment" :as moment]))

(defn today
  []
  (moment))

(defn format
  [fmt dt]
  (if (not (or (= "" dt) (nil? dt)))
    (-> dt moment (.format fmt))
    ""))

(defn iso-format
  [dt]
  (format "YYYY-MM-DD" dt))

(defn org-format
  [dt]
  (format "ddd D MMM YYYY" dt))

(defn nilify-invalid
  [dt]
  (if (.isValid dt)
    dt
    nil))

(defn ->dt
  [s]
  (if (seq s)
    (moment s)))

(defn dt->ymd
  [dt]
  (if (nil? dt)
    [nil nil nil]
    (let [o (.toObject dt)]
      [(.-years o)
       (inc (.-months o))
       (.-date o)])))

(defn ymd->dt
  [[y m d]]
  (moment (into-array
           [y (dec m) d])))

(defn- ->num
  [x]
  (cond
    (nil? x) nil
    (number? x) x
    (string? x) (parse-long x)))

(defn- numerify-ymd
  [ymd]
  (mapv ->num ymd))

(defn- empty->nil
  [x]
  (if (empty? x)
    nil
    x))

(defn ymd->iso
  [ymd]
  (-> ymd
      numerify-ymd
      ymd->dt
      nilify-invalid
      iso-format))
