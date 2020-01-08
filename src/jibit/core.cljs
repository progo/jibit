(ns ^:figwheel-hooks jibit.core
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]))

;;; events and handlers -- update db

;; OBS: Variations
;; reg-event-fx
;; reg-event-db

(re-frame/reg-event-db
 :initialize
 (fn [db _]
   (if (:init-done db)
     db
     {:hello "world"
      :images-query []
      :init-done true})))

(re-frame/reg-event-db
 :hello
 (fn [db _]
   (update db :hello str \s)))

;;; queries from db

(re-frame/reg-sub
 :hello
 (fn [db _]
   (-> db :hello)))

(re-frame/reg-sub
 :everything
 (fn [db _] db))

;;; views and components

;; Components are simply functions returning reagent hiccup. Calling
;; other components requires wrapping in an extra vector. But now you
;; can also pass arguments and shit. Of course you can subscribe to
;; other sources, and by making real components like this the entirety
;; of reagent/react power is well utilised.
(defn db-debug-textarea [message]
  (let [world (re-frame/subscribe [:everything])]
    [:textarea {:style {:background-color "#abc"
                        :color "#def"
                        :width "67em"
                        :height "20em"}
                :value (str message \space @world)}]))

(defn ui []
  (let [value (re-frame/subscribe [:hello])]
    [:div
     [:h1
      {:on-click #(re-frame/dispatch [:hello])}
      "Hello "
      @value]
     [db-debug-textarea " xx"]]))

(defn get-app-element []
  (gdom/getElement "app"))

(defn mount [el]
  (reagent/render-component [ui] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (re-frame/dispatch-sync [:initialize])
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(mount-app-element)

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  ;; (re-frame/clear-subscription-cache!)
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
