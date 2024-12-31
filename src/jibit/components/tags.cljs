(ns jibit.components.tags
  (:require
   [jibit.utils :as utils]))

;; TODO can we refactor this with `tag-view`?
(defn small-tag-view
  "Render a small RO tag element meant to be used as information."
  [tag]
  (let [tag-id (:id tag)]
    ^{:key tag-id}
    [:li {:style (if-let [color (:computed_color tag)]
                   {:background-color (utils/rgb->rgba color 0.20)
                    :border-color color})
          :title (or (:description tag) "")}
     (:name tag)]))


(defn render-tags-from-ids
  [tags-db tag-ids]
  [:ul.inline-tags
   (doall
    (for [tid tag-ids]
      ^{:key (str "inline-tag-" tid)}
      (small-tag-view (tags-db tid))))])

