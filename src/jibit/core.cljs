(ns ^:figwheel-hooks jibit.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   ajax.edn
   [taoensso.timbre :as timbre :refer [debug spy]]
   [common.human :as human]
   [jibit.utils :as utils :refer [dissoc-in]]))

;;; events and handlers -- update db

;; OBS: Variations
;; reg-event-fx
;; reg-event-db

;; This is something we don't need in production. It can be left empty
;; there. This could also be something we don't need in figwheel
;; rounds if we can fix things in some end, but I don't know.

;; Remember to specify protocol. And no trailing slash.
(def server-uri "http://localhost:8088")

(def selection-hold-time-msecs 320)

;;;; Defining client/server conversations

;; TODO wonder if this is a sensible refactor in contrast to our
;; current system.
(defn build-http-request
  [&{method :method
     uri :uri
     params :params
     usecase :usecase}]
  {:method method
   :uri (str server-uri uri)
   :params params
   :format (ajax.edn/edn-request-format)
   :response-format (ajax.edn/edn-response-format)
   :on-success [:good-http-get usecase]
   :on-failure [:bad-http-get usecase]
   })


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
                :selected #{}
                :tags-union true
                :init-done true})]
     db')))

(re-frame/reg-event-db
 :change-input
 (fn [db [_ data-id new-value]]
   (assoc-in db [:input data-id] new-value)))

(re-frame/reg-event-fx
 :toggle-tags-union
 (fn [{db :db} [_ item]]
   (let [any-tags-selected? (-> db :selected-tags seq)]
     (merge
      {:db (update db item not)}
      (when any-tags-selected?
        {:dispatch [:get-photos]})))))

(re-frame/reg-event-fx
 :slide-mouse-down
 (fn [{db :db} [_ photo-id ts]]
   ;; TODO wonder if it is useful to wrap this as an effect
   (js/setTimeout #(re-frame/dispatch [:slide-mouse-up photo-id nil])
                  selection-hold-time-msecs)
   {:db (assoc-in db [:mouse-events :down]
                  {:photo-id photo-id
                   :timestamp ts})}))

(re-frame/reg-event-db
 :select-photo
 (fn [db [_ photo-id mode]]
   (debug "Selecting " photo-id)
   (update db :selected conj photo-id)))

(re-frame/reg-event-fx
 :slide-mouse-up
 (fn [{db :db} [_ photo-id ts]]
   ;; User released M-btn1 at moment `ts`. The timestamp can be nil
   ;; particularly when we have automatically done something and
   ;; reacted to the mousehold.
   (if-let [down (-> db :mouse-events :down)]
     (let [hold-begin (:timestamp down)
           held-ms (- (js/Date.) hold-begin)
           held-long-enough (>= held-ms selection-hold-time-msecs)
           ;; Remove the record now that we've done it.
           db' (dissoc-in db [:mouse-events :down])]
       (if held-long-enough
         (do
           ;; (debug "User released button:" held-ms "ms")
           {:db db'
            :dispatch [:select-photo photo-id :toggle]})
         ;; User really just clicked and we handle that elsewhere.
         {:db db'}))
     {:db db})))

;; Toggle tag from query

(defn toggle-set-membership
  [x s]
  (if (s x)
    (clojure.set/difference s #{x})
    (clojure.set/union      s #{x})))

(re-frame/reg-event-fx
 :toggle-tag
 (fn [{db :db} [_ tag-id]]
   (let [tags-selection (toggle-set-membership tag-id (:selected-tags db))]
     {:db (assoc-in db [:selected-tags] tags-selection)
      :dispatch [:get-photos]
      })))

(re-frame/reg-event-fx
 :get-photos
 (fn [{db :db} _]
   (let [filter-criteria (-> (:input db)
                             (assoc :tags (:selected-tags db))
                             (assoc :tags-union (:tags-union db)))]
     (debug "Retrieving photos using criteria" filter-criteria)
     {:dispatch [:http-get "/photos" :query-photos filter-criteria]})))

;;; queries from db

(re-frame/reg-sub
 :tags
 (fn [db _]
   (-> db :tags)))

;; Tags but in a map of (tag-id -> tag)
(re-frame/reg-sub
 :tags-map
 (fn [db _]
   (into {} (map (juxt :tag/id identity) (-> db :tags)))))

(re-frame/reg-sub
 :tags-union
 (fn [db _]
   (or false (-> db :tags-union))))

(re-frame/reg-sub
 :selected
 (fn [db _]
   (-> db :selected)))

(re-frame/reg-sub
 :input
 (fn [db [_ data-id]]
   (-> db :input data-id)))

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

(defn render-tags-from-ids
  [tags-db tags]
  [:div.inline-tags
   (for [t tags]
     ^{:key (str "small-tag-" t)}
     [:span (-> t tags-db :tag/name)]
     )])

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
                :on-click #(re-frame/dispatch [:toggle-tags-union data-id])}
     text]))

(defn slide [image]
  [:div.slide-wrapper
   [:div.slide
    {:on-mouse-down #(re-frame/dispatch [:slide-mouse-down (:photo/id image) (js/Date.)])
     :on-mouse-up #(re-frame/dispatch [:slide-mouse-up (:photo/id image) (js/Date.)])
     ;; :on-click #(debug "mousie click" image)
     :class (let [sels @(re-frame/subscribe [:selected])]
              (if (sels (:photo/id image))
                "selected-slide"
                ""))
     }
    [:img {:src (str server-uri "/thumbnail/" (:photo/uuid image))}]
    [:ul.info
     [:li (:camera/exif_model image)]
     [:li (:lens/exif_model image)]
     [:li (human/focal-length (:photo/focal_length_35 image)) " mm"]
     [:li (human/aperture (:photo/aperture image))]
     [:li (human/shutter-speed (:photo/shutter_speed image)) " s"]
     (when-not (zero? (:photo/exposure_comp image))
       [:li (human/exp-comp (:photo/exposure_comp image)) " EV"])
     [:li "ISO " (:photo/iso image)]
     (let [tag-db (re-frame/subscribe [:tags-map])]
       (when-let [tags (seq (:tagged/ids image))]
         [:li (render-tags-from-ids @tag-db tags)]))
     ]]])

(defn data-bound-input
  "Build an input element that binds into `data-id`. Props is a map that
  goes into creating the element."
  [data-id props]
  (let [bound @(re-frame/subscribe [:input data-id])
        change-fn #(re-frame/dispatch [:change-input
                                       data-id
                                       (-> % .-target .-value)])
        props (assoc props :on-change change-fn)
        props (assoc props :value bound)
        ]
    [:input props]))

(defn data-bound-select
  "Build a select element that binds into `data-id`. Options is a seq of
  maps with keys [:name, :value]."
  [data-id options]
  (let [bound @(re-frame/subscribe [:input data-id])
        change-fn #(re-frame/dispatch [:change-input
                                       data-id
                                       (-> % .-target .-value)])
        props {:on-change change-fn
               :value (or bound "")}]
    [:select props
     (doall
      (for [{name :name value :value} options]
        ^{:key value} [:option {:value value} name]))]))

(defn filter-panel []
  [:div#filter
   [:form
    [:h1 "Filter photos"]
    [:div
     [data-bound-input :camera-make
      {:type "text" :placeholder "Camera make"}]
     [data-bound-input :camera-model
      {:type "text" :placeholder "Camera model"}]]
    [:div
     "Taken between "
     [data-bound-input :taken-begin
      {:type "date"}]
     [data-bound-input :taken-end
      {:type "date"}]]
    [:h1 "Order"]
    [:div
     "Order by "
     [data-bound-select :order-by
      [{:name "Taken" :value "taken_ts"}
       {:name "Random" :value "random"}]]]
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
