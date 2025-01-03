(ns jibit.components.tag-edit
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   ))

(defn tag-edit-dialog []
  (let [enabled? (some #{:tag-dialog} @(re-frame/subscribe [:state-stack]))
        tag @(re-frame/subscribe [:input [:tag]])
        new? (-> tag :id nil?)
        tag-sub-level (fn [tag]
                        (-> tag :nested_label count dec))
        tags (->> @(re-frame/subscribe [:tags])
                  (map (juxt :id :name tag-sub-level)))
        incomplete-form? (-> tag :name empty?)
        parent-id-invalid? (and (-> tag :id)
                                (= (-> tag :id)
                                   (-> tag :parent_id)))
        prevent-saving? (or incomplete-form? parent-id-invalid?)
        do-save #(when-not prevent-saving?
                   (re-frame/dispatch [:create-new-tag]))]
    [:div.modal-dialog
     {:class (if enabled? "modal-shown" "")}
     [:h1 (if new?
            "Create tag"
            "Modify tag")
      \space
      [:span.tag-edit
       (:name tag)]]

     [:div.dialog-row
      [:div.dialog-column
       [:label {:for "tag-name"} "Name"] [:br]
       [data-bound-input [:tag :name]
        {:type :text
         :placeholder "Name"
         :auto-complete "off"
         :name "tag-name"}
        :on-enter do-save]
       (when incomplete-form?
         " * required")
       [:br]
       [:label {:for "tag-description"} "Description"] [:br]
       [data-bound-input [:tag :description]
        {:type :text
         :placeholder "Description"
         :name "tag-description"}
        :textarea? true]]

      [:div.dialog-column
       [:label "Parent tag"] [:br]
       [data-bound-select [:tag :parent_id]
        (concat [{:name "--" :value "nil"}]
                (for [[tag-id tag-name sub-level] tags]
                  {:name (str (apply str (repeat sub-level "—")) " " tag-name)
                   :value tag-id}))]
       (when parent-id-invalid?
         " !!")
       [:br]
       [:label "Tag color"] [:br]
       [data-bound-input [:tag :style_color]
        {:type :color
         :on-click #(re-frame/dispatch [:activate-tag-use-color])}]
       "  "
       [data-bound-input [:tag :tag-color?]
        {:type :checkbox
         :on-click #(re-frame/dispatch [:clear-tag-color])
         :value "yes"}]
       "Use color"]]

     [:div.footer
      [:a.button {:on-click do-save
                  :class (when prevent-saving? "btn-disabled")}
       (if new? "Create" "Save")]
      [:a.button {:on-click #(re-frame/dispatch [:close-and-clear-tag-dlg])}
       "Close"]
      (when-not new?
        [:a.button.red.right
         {:on-click #(re-frame/dispatch [:show-prompt
                                         {:title (str "Confirm deletion of " (:name tag))
                                          :label-yes "Delete!"
                                          :callback-yes [:delete-tag (-> tag :id)]}])}
         "Delete..."])]]))

