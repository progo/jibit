(ns jibit.components.filters
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [jibit.components.fancydate :refer [data-bound-fancydate]]
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   [jibit.utils :as utils]))

(defn filter-panel []
  (let [get-photos #(re-frame/dispatch [:get-photos])
        show-panel? @(re-frame/subscribe [:show-filter-panel?])]
    [:div
     {:class (str
              "w-full "
              "rounded-lg "
              "p-2 "
              "border-2 border-dashed "
              (if show-panel? "" "hidden"))}
     [:div.filter-row
      [:div.filter-column
       [:h1 "Filter options"]

       [:div
        [data-bound-input [:filter :camera]
         {:type :search
          :list "camera-list"
          :title "Filter by camera make or model"
          :placeholder "Camera"}
         :on-enter get-photos
         :clearable? true]

        [data-bound-input [:filter :lens]
         {:type :search
          :list "lens-list"
          :title "Filter by lens make or model"
          :placeholder "Lens"}
         :on-enter get-photos
         :clearable? true]]

       [:div
        "Taken "
        [data-bound-fancydate [:filter :taken-ts]
                              {:style {:width "50%"}}]]
       [:div
        "Imported "
        [data-bound-fancydate [:filter :imported-ts]
                              {:style {:width "50%"}}]]

       [:h1 "Order options"]
       "Order by "
       [data-bound-select [:filter :order-by]
        [{:name "Taken" :value "taken_ts"}
         {:name "Rating" :value "rating"}]]]

      [:div.filter-column
       [data-bound-toggle-button [:filter :show-only-untitled?] {}]
       " Only untitled"
       [:br]
       [data-bound-toggle-button [:filter :show-only-untagged?] {}]
       " Only untagged"
       [:br]
       [data-bound-toggle-button [:filter :show-only-unrated?] {}]
       " Only unrated"
       [:br]
       [data-bound-toggle-button [:filter :show-only-uncooked?] {}]
       " Only developed"
       [:br]
       [data-bound-toggle-button [:filter :tags-union?]
        {:label-on "ANY"
         :label-off "ALL"
         :class-on "btn-any-tag"
         :class-off "btn-all-tags"}]
       " Filter by selected tags"]]

     [:div.filter-row
      [:a#filter-btn.button
       {:on-click get-photos}
       "Filter"]]]))
