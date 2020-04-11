(ns clurator.view.filtering
  "Process user's requests to filter photos, into safer data structures
  that we use."
  (:require [clojure.edn :as edn]))

(defn read-edn
  [req]
  (-> req
      :body
      slurp
      edn/read-string))

(defn nilify
  "Empty strings into nils."
  [x]
  (cond
    (coll? x) x
    (boolean? x) x
    (empty? x) nil
    :else x))

(defmacro clean-input
  "Check if `kw' as a string is found in a scoped map called `criteria'
  and if so, produce a map of {kw (criteria (name kw))} for merge.

  Use in `handle-filter-criteria' and not much elsewhere.

  Optionally supply a body of code that will deal with captured `input'.
  "
  ([kw]
   `(if-let [val# (nilify (get ~'criteria ~(name kw)))]
      {~kw val#}))
  ([kw & body]
   `(if-let [~'input (nilify (get ~'criteria ~(name kw)))]
      {~kw ~@body}
      )))

(defmacro clean-edn-input
  "Given `kw', parse `(get criteria kw)` as edn value."
  [kw]
  `(if-let [val# (edn/read-string (get ~'criteria ~(name kw)))]
     {~kw val#}))

(defn handle-filter-criteria
  [criteria]
  ;; order-by
  ;; taken-begin  date
  ;; taken-end    date
  ;; camera-make  text
  ;; camera-model text
  ;; tags-union   boolean
  ;; tags         set of IDs
  (merge {}
         (clean-edn-input :tags-union)
         (clean-edn-input :tags)
         (clean-input :order-by
              (case input
                "random" :%random
                "taken" :taken_ts
                :taken_ts))
         (clean-input :camera-make)
         (clean-input :camera-model)
         (clean-input :taken-begin)
         (clean-input :taken-end)))
