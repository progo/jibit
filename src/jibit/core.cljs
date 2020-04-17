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

(defn i
  "{debug} Inspect DB."
  [& ks]
  (let [db @re-frame.db/app-db]
    (cond
      (nil? ks) (sort (keys db))
      :t (get-in db ks))))

(defn toggle-set-membership
  [x s]
  (if (s x)
    (clojure.set/difference s #{x})
    (clojure.set/union      s #{x})))

(defn dispatch-preventing-default-action
  [evt event]
  (. evt preventDefault)
  (re-frame/dispatch event))

;; Defining client/server conversations

(defn build-edn-request
  "Build a request building map that can be passed to :http-xhrio."
  [&{method :method
     uri :uri
     params :params
     response :response}]
  {:method method
   :uri (str server-uri uri)
   :params params
   :format (ajax.edn/edn-request-format)
   :response-format (ajax.edn/edn-response-format)
   :on-success [response true]
   :on-failure [response false]})

;;;;

(defn photo-ids
  "Get a set of photo/ids from a collection of photo maps."
  [p]
  (set (map :photo/id p)))

;;;; Our ajax responses from server

(re-frame/reg-event-db
 :on-get-tags
 (fn [db [_ success? response]]
   (assoc db :tags response)))

(re-frame/reg-event-db
 :on-get-photos
 (fn [db [_ success? new-photos]]
   (when success?
     (let [ids (clojure.set/intersection (photo-ids new-photos)
                                         (:selected db))]
       (assoc db
              :photos new-photos
              :selected ids)))))

(re-frame/reg-event-fx
 :on-tag
 (fn [cofx [_ success? response]]
   ;; We've just tagged images
   (when success?
     {:dispatch [:get-photos]})))

(re-frame/reg-event-fx
 :on-create-tag
 (fn [cofx [_ success? response]]
   ;; We've made a new tag in the system.
   (when success?
     {:dispatch-n [[:reload-tags]
                   [:cancel-create-tag-dlg]]})))

;;;; Ajax end

(re-frame/reg-fx
 :dispatch-after-delay
 (fn [{event :event timeout :timeout}]
   (js/setTimeout
    #(re-frame/dispatch event)
    timeout)))

(re-frame/reg-event-fx
 :initialize
 (fn [{db :db} _]
   (let [db' (if (:init-done db)
               db
               {:photos []
                :tags []
                :state nil
                :selected-tags #{}
                :selected #{}
                :tags-union true
                :init-done true})]
     {:dispatch [:reload-tags]
      :db db'})))

(re-frame/reg-event-fx
 :reload-tags
 (fn [_ _]
   {:http-xhrio (build-edn-request :method :get
                                   :uri "/tags"
                                   :response :on-get-tags)}))

;; Store changed input value under a chain of keys in db,
;; under :input.
(re-frame/reg-event-db
 :change-input
 (fn [db [_ data-ids new-value]]
   (assoc-in db (conj (seq data-ids) :input) new-value)))

;;; Set tags on selected photos!
(re-frame/reg-event-fx
 :toggle-tag-on-selected
 (fn [{db :db} [_ tag-id]]
   (when-let [sel (seq (-> db :selected))]
     (timbre/debugf "Setting tag %d to photos %s" tag-id sel)
     {:http-xhrio (build-edn-request :method :post
                                     :uri "/tag-photo"
                                     :params {:tag tag-id
                                              :photos sel}
                                     :response :on-tag)})))

(re-frame/reg-event-fx
 :toggle-tags-filter-union
 (fn [{db :db} [_ item]]
   (let [any-tags-selected? (-> db :selected-tags seq)]
     (merge
      {:db (update db item not)}
      (when any-tags-selected?
        {:dispatch [:get-photos]})))))

(re-frame/reg-event-db
 :show-edit-tag-dlg
 (fn [db [_ tag-id]]
   ;; omg a linear search
   (let [tag (first (filter #(= tag-id (-> % :tag/id)) (:tags db)))
         ;; apply the checkbox if there's color present.
         tag (assoc tag :tag-color? (-> tag :tag/style_color boolean))]
     (-> db
         (assoc :state :tag-dialog)
         (assoc-in [:input :tag] tag)))))

(re-frame/reg-event-db
 :show-create-tag-dlg
 (fn [db _]
   (assoc db :state :tag-dialog)))

(re-frame/reg-event-fx
 :create-new-tag
 (fn [{db :db} _]
   (let [input (spy (-> db :input :tag))]
     {:http-xhrio (build-edn-request :method :post
                                     :uri "/tag"
                                     :params input
                                     :response :on-create-tag)})))

(re-frame/reg-event-db
 :cancel-create-tag-dlg
 (fn [db _]
   (-> db
       (assoc :state nil)
       ;; Clear set values, if any
       (assoc-in [:input :tag] {}))))

(re-frame/reg-event-db
 :select-photo
 (fn [db [_ photo-id]]
   (update db :selected #(toggle-set-membership photo-id %))))

(re-frame/reg-event-db
 :clear-selection
 (fn [db _]
   (update db :selected empty)))

;; Toggle tag from query

(re-frame/reg-event-fx
 :toggle-tag-filter
 (fn [{db :db} [_ tag-id]]
   (let [tags-selection (toggle-set-membership tag-id (:selected-tags db))]
     {:db (assoc-in db [:selected-tags] tags-selection)
      :dispatch [:get-photos]
      })))

(re-frame/reg-event-fx
 :get-photos
 (fn [{db :db} _]
   (let [filter-criteria (-> db :input :filter
                             (assoc :tags (:selected-tags db))
                             (assoc :tags-union (:tags-union db)))]
     (debug "Get photos with:" filter-criteria)
     {:http-xhrio (build-edn-request :method :get
                                     :uri "/photos"
                                     :params filter-criteria
                                     :response :on-get-photos)})))

;;; queries from db

(re-frame/reg-sub
 :state
 (fn [db _]
   (:state db)))

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
 :selected-count
 (fn [db _]
   (count (-> db :selected))))

(re-frame/reg-sub
 :selected
 (fn [db _]
   (-> db :selected)))

(re-frame/reg-sub
 :input
 (fn [db [_ data-ids]]
   (get-in db (concat [:input] data-ids))))

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

(defn render-tags-from-ids
  [tags-db tags]
  [:div.inline-tags
   (for [t tags]
     ^{:key (str "small-tag-" t)}
     [:span (-> t tags-db :tag/name)]
     )])

;;; Views and components

(defn data-bound-toggle-button
  [data-id {:keys [label-on label-off class-on class-off]}]
  (let [data-bind @(re-frame/subscribe [data-id])
        class (if data-bind
                (or class-on "button-toggle-on")
                (or class-off "button-toggle-off"))
        label (if data-bind
                (or label-on "On")
                (or label-off "Off"))]
    [:a.button {:class class
                :on-click #(re-frame/dispatch [:toggle-tags-filter-union data-id])}
     label]))

(defn slide [image]
  [:div.slide-wrapper
   [:div.slide
    {:on-context-menu #(dispatch-preventing-default-action % [:select-photo (:photo/id image)])
     :class (let [sels @(re-frame/subscribe [:selected])]
              (if (sels (:photo/id image))
                "selected-slide"
                ""))}
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
  "Build an input element that binds into a chain `data-ids`. Props is a
  map that goes into creating the element. Optional `textarea?` makes
  this a textarea."
  [data-ids props & textarea?]
  (let [checkbox? (= :checkbox (:type props))
        bound @(re-frame/subscribe [:input data-ids])
        ;; Checkboxes (thus react) won't accept nils as false
        bound (if checkbox?
                (boolean bound)
                bound)
        change-fn #(re-frame/dispatch [:change-input
                                       data-ids
                                       (if checkbox?
                                         (not bound)
                                         (-> % .-target .-value))])
        props (assoc props
                     :on-change change-fn
                     :value bound)
        ;; more checkbox handling
        props (if checkbox?
                (assoc props :checked bound)
                props)]
    [(if textarea? :textarea :input) props]))

(defn data-bound-select
  "Build a select element that binds into a chain `data-ids`. Options is
  a seq of maps with keys [:name, :value]. The value comes out thru as
  a clojurescript readable symbol or value."
  [data-ids options]
  (let [bound @(re-frame/subscribe [:input data-ids])
        change-fn #(re-frame/dispatch [:change-input
                                       data-ids
                                       (cljs.reader/read-string
                                        (-> % .-target .-value))])
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
     [data-bound-input [:filter :camera-make]
      {:type "text" :placeholder "Camera make"}]
     [data-bound-input [:filter :camera-model]
      {:type "text" :placeholder "Camera model"}]]
    [:div
     "Taken between "
     [data-bound-input [:filter :taken-begin]
      {:type "date"}]
     [data-bound-input [:filter :taken-end]
      {:type "date"}]]
    [:h1 "Order"]
    [:div
     "Order by "
     [data-bound-select [:filter :order-by]
      [{:name "Taken" :value "taken_ts"}
       {:name "Random" :value "random"}]]]
    [:a#filter-btn.button
     {:on-click #(re-frame/dispatch [:get-photos])}
     "Filter"]
    [data-bound-toggle-button :tags-union
     {:label-on "Any tag"
      :label-off "All tags"
      :class-on "btn-any-tag"
      :class-off "btn-all-tags"}]
    ]])

(defn tag-view
  "Render a small element that visually represents a clickable tag."
  [tag]
  (let [tag-id (:tag/id tag)
        selections (re-frame/subscribe [:selected-tags])
        selected? (@selections tag-id)]
    ^{:key tag-id}
    [:li {:on-click #(re-frame/dispatch [:toggle-tag-on-selected tag-id])
          :on-context-menu #(dispatch-preventing-default-action % [:toggle-tag-filter tag-id])
          :on-double-click #(re-frame/dispatch [:show-edit-tag-dlg tag-id])
          :class (when selected? "selected")
          :style {:border-color (if-let [color (:tag/computed_color tag)]
                                  color
                                  "")}
          :title (or (:tag/description tag) "")}
     (:tag/name tag)]))

(defn tags-view []
  [:ul#tags
   (doall
    (for [t @(re-frame/subscribe [:tags])]
      (tag-view t)))
   [:div#create-tag.button
    {:on-click #(re-frame/dispatch [:show-create-tag-dlg])}
    "+"]])

(defn modal-state?
  "Is this state a modal one?"
  [state]
  ;; All non-nil states are modal, for now.
  state)

(defn modal-background []
  (let [enabled? (modal-state? @(re-frame/subscribe [:state]))]
    [:div#modal-bg {:class (if enabled? "modal-shown" "")}]))

(defn tag-edit-dialog []
  (let [enabled? (= :tag-dialog @(re-frame/subscribe [:state]))
        tag @(re-frame/subscribe [:input [:tag]])
        new? (-> tag :tag/id nil?)
        tag-sub-level (fn [tag]
                        (-> tag :tag/nested_label count dec))
        tags (->> @(re-frame/subscribe [:tags])
                  (map (juxt :tag/id :tag/name tag-sub-level)))
        incomplete-form? (-> tag :tag/name empty?)
        parent-id-invalid? (and (-> tag :tag/id)
                                (= (-> tag :tag/id)
                                   (-> tag :tag/parent_id)))
        prevent-saving? (or incomplete-form? parent-id-invalid?)]
    [:div.modal-dialog
     {:class (if enabled? "modal-shown" "")}
     [:h1 (if new?
            "Create"
            "Modify")
      \space
      [:span.tag-edit
       (:tag/name tag)]]

     [:div.dialog-row
      [:div.dialog-column
       [:label {:for "tag-name"} "Name"] [:br]
       [data-bound-input [:tag :tag/name]
        {:type :text
         :placeholder "Name"
         :name "tag-name"}]
       (when incomplete-form?
         " * required")
       [:br]
       [:label {:for "tag-description"} "Description"] [:br]
       [data-bound-input [:tag :tag/description]
        {:type :text
         :placeholder "Description"
         :name "tag-description"}
        :yes-do-a-textarea]]

      [:div.dialog-column
       [:label "Parent tag"] [:br]
       [data-bound-select [:tag :tag/parent_id]
        (concat [{:name "--" :value "nil"}]
                (for [[tag-id tag-name sub-level] tags]
                  {:name (str (apply str (repeat sub-level "—")) " " tag-name)
                   :value tag-id}))]
       (when parent-id-invalid?
         " !!")
       [:br]
       [:label "Tag color"] [:br]
       [data-bound-input [:tag :tag/style_color]
        {:type :color}]
       "  "
       [data-bound-input [:tag :tag-color?]
        {:type :checkbox
         :value "yes"}]
       "Use color"]]

     [:div.footer
      [:a.button {:on-click #(when-not prevent-saving?
                               (re-frame/dispatch [:create-new-tag]))
                  :class (when prevent-saving? "btn-disabled")}
       (if new? "Create" "Save")]
      [:a.button {:on-click #(re-frame/dispatch [:cancel-create-tag-dlg])}
       "Close"]]]))

(defn lighttable-bare []
  [:div.lighttable
   (for [photo @(re-frame/subscribe [:photos])]
     ^{:key (:photo/uuid photo)} [slide photo])])

(defn header []
  (let [pc @(re-frame/subscribe [:photos-count])
        sc @(re-frame/subscribe [:selected-count])]
    [:h1#head "Photos"
     [:span.photos-count \# pc]
     (when (pos? sc)
       [:span.selection-count "Selected " sc \space
        [:a {:class "clear"
             :on-click #(re-frame/dispatch [:clear-selection])
             :href "#"} "Clear"]])]))

(defn user-interface []
  [:div
   ;; Visible "zero-level" elements
   [header]
   [filter-panel]
   [tags-view]
   [lighttable-bare]

   ;; Modal dialogs that go above level zero. Shown and hidden as
   ;; needed.
   [modal-background]
   [tag-edit-dialog]])

;;; re-frame boilerplate below

(defn get-app-element []
  (js/document.getElementById "app"))

(defn mount [el]
  (reagent/render-component [user-interface] el))

(defn mount-app-element []
  (when-let [el (get-app-element)]
    (re-frame/dispatch-sync [:initialize])
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
