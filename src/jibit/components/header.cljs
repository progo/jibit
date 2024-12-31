(ns jibit.components.header
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [jibit.components.page-navigator :refer [page-navigator]]
   [jibit.components.activity-indicator :refer [activity-indicator]]
   [jibit.utils :as utils :refer [dissoc-in]]))

(defn filter-button
  []
  (let [toggle-fn #(re-frame/dispatch [:toggle-show-filter-panel])
        ;; panel-showing? (re-frame/subscribe [:show-filter-panel?])
        filters-in-place? (re-frame/subscribe [:active-filters])]
    (fn []
      [:img {:src (if @filters-in-place?
                    "/img/filter-color.svg"
                    "/img/filter-bw.svg")
             :on-click toggle-fn
             }])))


(defn header []
  (let [sc @(re-frame/subscribe [:selected-photos# :just-selection])]
    [:h1#head "Jibit"
     [page-navigator]
     [filter-button]
     (when (pos? sc)
       [:span.selection-count "Selected " sc \space
        [:a {:class "clear"
             :on-click #(re-frame/dispatch [:clear-selection])
             :href "javascript:void(0)"} "Clear"]])

     [:div#right-hand-side
      [activity-indicator]

      [:div#menu
       [:a {:on-click #(re-frame/dispatch [:show-gear-dlg])
            :title "Open gear data editor"
            :href "javascript:void(0)"}
        "Gear"]
       \space

       [:a {:on-click #(re-frame/dispatch [:do-sync-inbox])
            :title "Sync inbox"
            :href "javascript:void(0)"}
        "Inbox"]
       \space

       [:a {:on-click #(re-frame/dispatch [:show-file-import])
            :title "Select or drop photos to import"
            :href "javascript:void(0)"}
        "Import"]
       [:input#file-upload.hidden
        {:name "upload"
         :on-change #(re-frame/dispatch [:import-photos (-> % .-target)])
         :type :file
         :multiple true}]
       \space

       [:span
        [:a {:on-click #(re-frame/dispatch [:export-selected (utils/get-default-export-scheme-key)])
             :title "Export selected photos"
             :href "javascript:void(0)"}
         "Export"]
        [:span.submenu-indicator "▼"
         [:ul.dropdown
          (for [export-scheme (vals common.settings/export-schemes)]
            ^{:key export-scheme}
            [:li {:on-click #(re-frame/dispatch [:export-selected (:key export-scheme)])
                  :href "javascript:void(0)"}
             (:name export-scheme)]
            )
          ]]]
       ]]]))
