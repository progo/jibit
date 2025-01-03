(ns jibit.components.quick-label
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [jibit.components.inputs :refer [data-bound-input]]
   ))

(defn activate-button
  "When a buttonlike `a` is in focus and user presses SPC or RET, we
  want to activate it as if it was clicked on."
  [evt]
  (.preventDefault evt)
  (.stopPropagation evt)
  (when (#{" " "Enter"} (.-key evt))
    (doto (.-target evt) (.click))))

(defn quick-label-edit
  "Small two-part editor for titles and notes."
  []
  (let [enabled? (= :quick-label-edit @(re-frame/subscribe [:current-state]))
        save-fn #(re-frame/dispatch [:save-close-label-popup])]
    [:div#quick-label-editor
     {:class (if enabled? "" "hidden")}

     [data-bound-input [:label-edit :title]
      {:type :text
       :placeholder "Title"
       :auto-complete "off"
       :id "quick-label-edit-title"
       :tab-index 0
       :name "photo-title"}
      :on-enter save-fn]
     [:br]

     [data-bound-input [:label-edit :notes]
      {:rows 7
       :placeholder "Notes..."
       :tab-index 0
       :name "photo-notes"}
      :textarea? true]

     [:br]
     [:a.button
      {:on-click save-fn
       :on-key-down activate-button
       :tab-index 0}
      "Close"]]))
