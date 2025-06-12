(ns jibit.components.tags
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
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


(defn tag-view
  "Render a small element that visually represents a clickable tag."
  [tag]
  (let [tag-id (:id tag)
        photos-selected? (pos? @(re-frame/subscribe [:selected-photos#]))
        selections (re-frame/subscribe [:selected-tags])
        selected? (@selections tag-id)]
    ^{:key tag-id}
    [:li {:on-click #(re-frame/dispatch [:toggle-tag-on-selected tag-id])
          :on-context-menu #(utils/dispatch-preventing-default-action % [:toggle-tag-filter tag-id])
          :on-double-click #(re-frame/dispatch [:show-edit-tag-dlg tag-id])
          :class (if selected? "selected" "")
          :style (if-let [color (:computed_color tag)]
                   {:background-color (utils/rgb->rgba color 0.20)
                    :border-color color})
          :title (or (:description tag) "")}
     (:name tag)]))

(defn tags-view []
  [:div.p-2.w-full
   [:div.border.p-2.rounded-xl.border-orange-400
    [:ul.tags-bar-big
     (let [tags @(re-frame/subscribe [:tags])]
       (if (seq tags)
         (doall
          (for [t tags]
            (tag-view t)))
         [:span#no-tags "Tags will appear here."]))
     [:div#create-tag.button
      {:on-click #(re-frame/dispatch [:show-create-tag-dlg])}
      "+"]]]])
