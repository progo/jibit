(ns clurator.view.tag
  (:require
   [clurator.model.tag :as model.tag]
   [clurator.view.filtering :as view.filtering]))

(defn list-tags [req]
  {:resp (model.tag/query-tags)})

(defn tag-photos [req]
  {:resp (let [{tag-id :tag
                photo-ids :photos} (view.filtering/read-edn req)]
           (model.tag/set-tag-for-photos tag-id photo-ids :toggle))})

(defn create-update-new-tag [req]
  (let [{use-color? :tag-color? :as tag} (view.filtering/read-edn req)

        ;; remove any color info if user wants not to reset it
        tag (if use-color?
              tag
              (dissoc tag :tag/style_color))

        status (if (model.tag/create-edit-tag tag)
                 :ok
                 :fail)]
    {:resp nil
     :status status}))
