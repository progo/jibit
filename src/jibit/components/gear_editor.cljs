(ns jibit.components.gear-editor
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [common.human :as human]
   [taoensso.timbre :as timbre :refer [debug spy]]
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   ))


(def gear-table-columns
  [{:title "Type" :field "gear_type", :width 80}
   {:title "EXIF Make" :field "exif_make" :width 200}
   {:title "EXIF Model" :field "exif_model" :width 200}
   {:title "Label" :field "user_label" :width 200 :editor "input"}])

(def gear-table-column-sort
  [{:column "gear_type" :dir "asc"}])


(defn gear-datalists [id gear-type]
  (let [gear @(re-frame/subscribe [:gear-raw gear-type])]
    [:datalist
     {:id id}
     (for [g gear]
       ^{:key (str "gear-" (:id g))}
       [:option {:data-value (:id g)}
        (human/gear-label g)
        ])]))

;; Let's experiment with form-3 components, now that we need to hack
;; something around component display.
(defn gear-edit-dialog*
  "Inner comp, gets values as props. I'm not sure why it sometimes works
  with this function and sometimes not. But using (reagent/argv cmp)
  seems to work all the time."
  []
  (let [tabulator-instance (atom nil)]
    (reagent/create-class
     {:component-did-mount (fn [cmp]
                             (reset! tabulator-instance
                                     (js/Tabulator.
                                      "#gear-table"
                                      (clj->js {:data []
                                                :initialSort gear-table-column-sort
                                                :columns gear-table-columns}))))
      ;; This update is called whenever we open or close gear dialog.
      :component-did-update (fn [cmp]
                              (let [gear (second (reagent/argv cmp))]
                                (timbre/debugf "Redrawing gear table with %d items!"
                                               (count gear))
                                (.setData @tabulator-instance (clj->js gear))))
      :display-name "gear-edit-dialog"
      :reagent-render
      (fn []
        (let [enabled? (some #{:gear-dialog} @(re-frame/subscribe [:state-stack]))]
          [:div.modal-dialog
           {:class (when enabled? "modal-shown")}
           [:h1 "Gear"]
           [:div
            [:table#gear-table.h-96]]
           [:div.footer
            [:a.button {:on-click (fn []
                                    (let [current-data (-> @tabulator-instance
                                                           .getData
                                                           js->clj)]
                                      (re-frame/dispatch [:save-gear-dlg current-data])))}
             "Save"]
            [:a.button {:on-click #(re-frame/dispatch [:close-gear-dlg])}
             "Close"]
            ]]))})))

(defn gear-edit-dialog []
  (let [gear (re-frame/subscribe [:gear-raw])]
    [gear-edit-dialog* @gear]))
