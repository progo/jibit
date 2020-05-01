(ns jibit.datetime
  (:require moment))

(defn moment [& arg] (apply js/moment arg))

(defn format
  [fmt dt]
  (-> dt moment (.format fmt)))

;; moment.js's own .toISOString is cool and performant also, it is TZ
;; aware though. Disable that with passing argument `true`.

(defn iso-format
  [dt]
  (format "YYYY-MM-DD" dt))

(defn org-format
  [dt]
  (format "ddd D MMM YYYY" dt))
