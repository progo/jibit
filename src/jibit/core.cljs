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
      :all-negatives (range 10)
      :init-done true})))

;;; queries from db

(re-frame/reg-sub
 :all-negatives
 (fn [db _]
   (-> db :all-negatives)))

;;; views and components

(defn slide [image-id]
  [:div.slide
   (str "œ" image-id)])

(defn lighttable-bare []
  [:div.lighttable
   (doall
    (for [a @(re-frame/subscribe [:all-negatives])]
      ^{:key a} [slide a]))])

(defn ui []
  (let []
    [:div
     [:h1 "Gallery"]
     [lighttable-bare]]))

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
