(ns clurator.view.filtering
  "Process user's requests to filter photos, into safer data structures
  that we use."
  (:require [clojure.edn :as edn]
            [taoensso.timbre :as timbre :refer [debug spy]]))

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

(defmacro clean-input-as
  "Check if `input-key` found in a scoped map `criteria` and if so,
  produce a map {kw (criteria input-key)}."
  [kw input-key]
  `(if-let [val# (nilify (get ~'criteria ~input-key))]
     {~kw val#}))

(defmacro clean-edn-input
  "Given `kw', parse `(get criteria kw)` as edn value."
  [kw]
  `(if-let [val# (edn/read-string (get ~'criteria ~(name kw)))]
     {~kw val#}))

(defn handle-filter-criteria
  [criteria]
  ;; order-by            enum
  ;; camera-make         text
  ;; camera-model        text
  ;; tags-union?         boolean
  ;; show-only-untitled? boolean
  ;; show-only-untagged? boolean
  ;; show-only-unrated?  boolean
  ;; show-only-uncooked? boolean
  ;; tags                set of IDs
  ;; taken-ts[begin]     date
  ;; taken-ts[end]       date
  ;; imported-ts[begin]  date
  ;; imported-ts[end]    date
  (merge {}
         (clean-edn-input :tags-union?)
         (clean-edn-input :show-only-untitled?)
         (clean-edn-input :show-only-untagged?)
         (clean-edn-input :show-only-unrated?)
         (clean-edn-input :show-only-uncooked?)
         (clean-edn-input :tags)
         (clean-input-as :taken-begin "taken-ts[begin]")
         (clean-input-as :taken-end "taken-ts[end]")
         (clean-input-as :imported-begin "imported-ts[begin]")
         (clean-input-as :imported-end "imported-ts[end]")
         (clean-input :order-by
              (case input
                "rating" :rating
                "taken_ts" :taken_ts
                :taken_ts))
         (clean-input :camera)
         (clean-input :lens)))
