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
 (fn [{db :db} [_ uri usecase params]]
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
                :selected-tags #{}
                :tags-union true
                :init-done true})]
     db')))

(re-frame/reg-event-db
 :toggle
 (fn [db [_ item]]
   (update db item not)))

;; Toggle tag from query

(re-frame/reg-event-fx
 :toggle-tag
 (fn [{db :db} [_ tag-id]]
   (let [tags-selection (:selected-tags db)
         tags-selection' (if (tags-selection tag-id)
                           (clojure.set/difference tags-selection #{tag-id})
                           (clojure.set/union      tags-selection #{tag-id}))]
     {:db (assoc-in db [:selected-tags] tags-selection')
      :dispatch [:get-photos]
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

(re-frame/reg-event-fx
 :get-photos
 (fn [{db :db} _]
   ;; TODO forms probably should be maintained in `db'
   (let [form (query "#filter form")
         filter-criteria (read-form form)
         filter-criteria (assoc filter-criteria :tags (:selected-tags db))
         filter-criteria (assoc filter-criteria :tags-union (:tags-union db))]
     (debug "Filtering by" filter-criteria)
     {:dispatch [:http-get "/photos" :query-photos filter-criteria]})))

;;; queries from db

(re-frame/reg-sub
 :tags
 (fn [db _]
   (-> db :tags)))

(re-frame/reg-sub
 :tags-union
 (fn [db _]
   (or false (-> db :tags-union))))

;; TODO we can parametrize to particular tag-id as well
(re-frame/reg-sub
 :selected-tags
 (fn [db _]
   (-> db :selected-tags)))

(re-frame/reg-sub
 :photos
 (fn [db _]
   (-> db :photos)))

(re-frame/reg-sub
 :photos-count
 (fn [db _]
   (count (-> db :photos))))


;;; Tags

(defn tag-menu
  [evt tag]
  (. evt preventDefault)
  (debug "tagmenu" evt tag))

;;; Views and components

(defn toggle-button
  [data-id text-on text-off class-on class-off]
  (let [data-bind @(re-frame/subscribe [data-id])
        class (if data-bind
                (or class-on "button-toggle-on")
                (or class-off "button-toggle-off"))
        text (if data-bind
                (or text-on "On")
                (or text-off "Off"))]
    [:a.button {:class class
                :on-click #(re-frame/dispatch [:toggle data-id])}
     text]))

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
     {:on-click #(re-frame/dispatch [:get-photos])}
     "Filter"]
    [toggle-button :tags-union "Any tag" "All tags" "btn-any-tag" "btn-all-tags"]
    ]])

(defn tag-view
  "Render a small element that visually represents a clickable tag."
  [tag]
  (let [tag-id (:tag/id tag)
        selections (re-frame/subscribe [:selected-tags])
        selected? (@selections tag-id)]
    ^{:key tag-id}
    [:li {:on-click #(re-frame/dispatch [:toggle-tag tag-id])
          :on-context-menu #(tag-menu % tag-id)
          :class (when selected? "selected")
          :title (or (:tag/description tag) "")}
     (:tag/name tag)]))

(defn tags-view []
  [:ul#tags
   (doall
    (for [t @(re-frame/subscribe [:tags])]
      (tag-view t)))])

(defn lighttable-bare []
  [:div.lighttable
   (for [photo @(re-frame/subscribe [:photos])]
     ^{:key (:photo/uuid photo)} [slide photo])])

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
