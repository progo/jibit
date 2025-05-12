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
              (if show-panel? "" "hidden"))}
     [:div
      [:div
       [:h1.bg-black.mt-0.p-1
        "Filter options"]

       [data-bound-input [:filter :text]
        {:type :input
         :placeholder "Keyword search"}
        :on-enter get-photos
        :clearable? true]

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

       "Order by "
       [data-bound-select [:filter :order-by]
        [{:name "Oldest" :value "oldest_1st"}
         {:name "Newest" :value "newest_1st"}
         {:name "Random" :value "random"}
         {:name "Rating" :value "rating"}]]]

      [:div
       [data-bound-toggle-button [:filter :show-only-untitled?] {}]
       " Only untitled"
       [data-bound-toggle-button [:filter :show-only-untagged?] {}]
       " Only untagged"
       [data-bound-toggle-button [:filter :show-only-unrated?] {}]
       " Only unrated"
       [data-bound-toggle-button [:filter :show-only-uncooked?] {}]
       " Only developed"
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
