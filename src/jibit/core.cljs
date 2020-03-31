(ns ^:figwheel-hooks jibit.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   ajax.edn
   [taoensso.timbre :as timbre :refer [debug spy]]
   [common.human :as human]
   ))

;;; events and handlers -- update db

;; OBS: Variations
;; reg-event-fx
;; reg-event-db

;; This is something we don't need in production. It can be left empty
;; there. This could also be something we don't need in figwheel
;; rounds if we can fix things in some end, but I don't know.

;; Remember to specify protocol. And no trailing slash.
(def server-uri "http://localhost:8088")

;;;; Defining client/server conversations

(re-frame/reg-event-fx
 :http-get
 (fn [{:keys [db]} [_ uri usecase params]]
   {:http-xhrio {:method :get
                 :uri (str server-uri uri)
                 :params params
                 :format (ajax.edn/edn-request-format)
                 :response-format (ajax.edn/edn-response-format)
                 :on-success [:good-http-get usecase]
                 :on-failure [:bad-http-get usecase]}}))

;; Generic success handler for HTTP gets
;; probably a multimethod can handle this?
(re-frame/reg-event-db
 :good-http-get
 (fn [db [_ usecase result]]
   (assoc db (case usecase
               :query-photos :photos
               :query-tags   :tags
               :http-result-good)
          result)))

;; Generic failure handler for HTTP gets
(re-frame/reg-event-db
 :bad-http-get
 (fn [db [_ usecase result]]
   (assoc db :http-result-fail result)))

;;;;; This is done.

(re-frame/reg-event-db
 :initialize
 (fn [db _]
   (let [db' (if (:init-done db)
               db
               {:photos []
                :tags []
                :init-done true})]
     db')))

;;;;; Tools

(defn query [q]
  (js/document.querySelector q))

;;; Form utils

(defn read-form [form-elt]
  (into {}
        (for [x (array-seq (.-elements form-elt))]
          [(.-name x)
           (.-value x)])))

(defn read-form-id [form-id]
  (let [f (js/document.getElementById form-id)]
    (read-form f)))

;;;

(defn get-photos
  []
  (let [form (query "#filter form")
        filter-criteria (read-form form)]
    (debug "Filtering by" filter-criteria)
    (re-frame/dispatch [:http-get "/photos" :query-photos filter-criteria])))

;;; queries from db

(re-frame/reg-sub
 :tags
 (fn [db _]
   (-> db :tags)))

(re-frame/reg-sub
 :photos
 (fn [db _]
   (-> db :photos)))

(re-frame/reg-sub
 :photos-count
 (fn [db _]
   (count (-> db :photos))))

;;; Views and components

(defn slide [image]
  [:div.slide-wrapper
   [:div.slide
    [:img {:src (str server-uri "/thumbnail/" (:photo/uuid image))}]
    [:ul.info
     [:li (:photo/original_file image)]
     [:li (:camera/exif_make image)]
     [:li (:lens/exif_model image)]
     [:li (human/focal-length (:photo/focal_length_35 image)) " mm"]
     [:li (human/aperture (:photo/aperture image))]
     [:li (human/shutter-speed (:photo/shutter_speed image)) " s"]
     (when-not (zero? (:photo/exposure_comp image))
       [:li (human/exp-comp (:photo/exposure_comp image)) " EV"])
     [:li "ISO " (:photo/iso image)]
     ]]])

(defn filter-panel []
  [:div#filter
   [:form
    [:h1 "Filter photos"]
    [:div
     "Make"
     [:input {:type "text" :name "camera-make"}]
     "Model"
     [:input {:type "text" :name "camera-model"}]]
    [:div
     "Taken"
     [:input {:type "date" :name "taken-begin"}]
     [:input {:type "date" :name "taken-end"}]]
    [:h1 "Order"]
    [:div
     "Order by"
     [:select {:name "order-by"}
      [:option {:value "taken"} "Taken"]
      [:option {:value "random"} "Random"]]]
    [:a#filter-btn.button
     {:on-click get-photos}
     "Filter"]
    ]])

(defn tags-view []
  [:ul#tags
   (for [t @(re-frame/subscribe [:tags])]
     ^{:key t} [:li t])])

(defn lighttable-bare []
  [:div.lighttable
    (for [image @(re-frame/subscribe [:photos])]
      ^{:key (:photo/uuid image)} [slide image])])

(defn user-interface []
  [:div
   [:h1 "Photos"
    [:span.photos-count \#
     @(re-frame/subscribe [:photos-count])]]
   [filter-panel]
   [tags-view]
   [lighttable-bare]])

;;; re-frame boilerplate below

(defn get-app-element []
  (js/document.getElementById "app"))

(defn mount [el]
  (reagent/render-component [user-interface] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (re-frame/dispatch-sync [:initialize])
    ;; (get-photos)
    (re-frame/dispatch [:http-get "/tags" :query-tags {}])
    (mount el)))

;; conditionally start your application based on the presence of an "app" element
;; this is particularly helpful for testing this ns without launching the app
(defonce startup
  (do
    (mount-app-element)
    true))

;; specify reload hook with ^;after-load metadata
(defn ^:after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
