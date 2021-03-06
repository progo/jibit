(ns clurator.view.tag
  (:require
   [clurator.model.tag :as model.tag]
   [clurator.view.filtering :as view.filtering]))

(defn list-tags [req]
  {:resp (model.tag/query-tags)})

(defn tag-photos-match-tags
  "On a selection of photos, sync up the tags so that every photo will
  have the same tags. We will probably adjust and add more behavior to
  this later on."
  [req]
  (let [{photo-ids :photos} (view.filtering/read-edn req)]
    {:resp (model.tag/match-tags-between-photos photo-ids)}))

(defn tag-photos [req]
  {:resp (let [{tag-id :tag
                photo-ids :photos} (view.filtering/read-edn req)]
           (model.tag/set-tag-for-photos tag-id photo-ids :toggle))})

(defn create-update-new-tag [req]
  (let [{use-color? :tag-color? :as tag} (view.filtering/read-edn req)

        ;; remove any color info if user wants not to reset it
        tag (if use-color?
              tag
              (dissoc tag :style_color))

        status (if (model.tag/create-edit-tag tag)
                 :ok
                 :fail)]
    {:resp nil
     :status status}))

(defn delete-tag
  "Delete a tag denoted by tag-id in the request but we'll check against
  constraints. The checks may result in a fail. The second round
  around pass an argument `:bypass? true` to proceed."
  [req]
  (let [{tag-id :tag-id
         bypass? :bypass?} (view.filtering/read-edn req)]
    (if-not bypass?
      (let [children (model.tag/tag-children tag-id)
            tagged-photos# (model.tag/tagged-photos# tag-id)]
        (if (zero? (+ (count children) tagged-photos#))

          ;; No problemos so we can go ahead with the deletion
          {:status :ok
           :resp (model.tag/delete-tag* tag-id)}

          ;; There are some things wrong...
          {:status :user
           :resp {:tag-id tag-id
                  :problems {:photos# (when (pos? tagged-photos#)
                                        tagged-photos#)
                             :children (when (seq children)
                                         (map :name children))}}}))
      {:status :ok
       :resp (model.tag/delete-tag* tag-id)})))
