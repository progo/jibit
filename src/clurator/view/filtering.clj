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

(defn clean-input-as
  [criteria kw input-key]
  (if-let [value (get criteria input-key)]
    {kw value}))

(defn clean-edn*
  "Shallowly clean value denoted by keyword `kw`. Any non-strings we
  pass forward without touching."
  [criteria kw]
  (let [rawv (get criteria (name kw))
        value (if (string? rawv)
                (edn/read-string rawv)
                rawv)]
    (when value
      {kw value})))

(defn handle-filter-criteria
  [c]
  ;; order-by            enum
  ;; offset              num
  ;; camera              text
  ;; lens                text
  ;; text                text
  ;; camera-id           num
  ;; lens-id             num
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
  (merge
   {}
   (clean-edn* c :tags-union?)
   (clean-edn* c :show-only-untitled?)
   (clean-edn* c :show-only-untagged?)
   (clean-edn* c :show-only-unrated?)
   (clean-edn* c :show-only-uncooked?)
   (clean-edn* c :tags)
   (clean-edn* c :offset)
   (clean-input-as c :taken-begin "taken-ts[begin]")
   (clean-input-as c :taken-end "taken-ts[end]")
   (clean-input-as c :imported-begin "imported-ts[begin]")
   (clean-input-as c :imported-end "imported-ts[end]")
   (clean-input-as c :text "text")
   (clean-input-as c :camera "camera")
   (clean-input-as c :lens "lens")
   (clean-edn* c :camera-id)
   (clean-edn* c :lens-id)
   {:order-by (case (get c "order-by")
                "rating" [:rating :orig_rating]
                "oldest_1st" :taken_ts
                "newest_1st" [:taken_ts :desc]
                "random" :%random
                :taken_ts)}))

