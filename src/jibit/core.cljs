(ns ^:figwheel-hooks jibit.core
  (:require
   [goog.dom :as gdom]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   [ajax.edn :as ajax-edn]
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
                 :format (ajax-edn/edn-request-format)
                 :response-format (ajax-edn/edn-response-format)
                 :on-success [:good-http-get usecase]
                 :on-failure [:bad-http-get usecase]}}))

;; Generic success handler for HTTP gets
(re-frame/reg-event-db
 :good-http-get
 (fn [db [_ usecase result]]
   (assoc db (case usecase
               :query-images :images
               :http-result-good)
          result)))

;; Generic failure handler for HTTP gets
(re-frame/reg-event-db
 :bad-http-get
 (fn [db [_ usecase result]]
   (assoc db :http-result-fail result)))

;;;;; This is done.

(re-frame/reg-event-fx
 :initialize
 (fn [{:keys [db]} _]
   (let [db' (if (:init-done db)
               db
               {:images-query []
                :images []
                :init-done true})]
     {:db db'
      :dispatch [:http-get "/photos" :query-images {}]
      })))

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

(defn filter-photos
  []
  (let [form (query "#filter form")
        filter-criteria (read-form form)]
    (debug "Filtering by" filter-criteria)
    (re-frame/dispatch [:http-get "/photos" :query-images filter-criteria])))

;;; queries from db

(re-frame/reg-sub
 :images
 (fn [db _]
   (-> db :images)))

(re-frame/reg-sub
 :images-count
 (fn [db _]
   (count (-> db :images))))

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
     "Taken"
     [:input {:type "date" :name "taken-begin"}]
     [:input {:type "date" :name "taken-end"}]]
    [:a#filter-btn.button
     {:on-click filter-photos}
     "Filter"]
    ]])

(defn lighttable-bare []
  [:div.lighttable
   (doall
    (for [image @(re-frame/subscribe [:images])]
      ^{:key (:photo/uuid image)} [slide image]))])

(defn ui []
  (let []
    [:div
     [:h1 "Photos"
      [:span.files-count \#
       @(re-frame/subscribe [:images-count])]]
     [filter-panel]
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
