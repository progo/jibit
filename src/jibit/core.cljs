(ns ^:figwheel-hooks jibit.core
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   ajax.edn
   [taoensso.timbre :as timbre :refer [debug spy]]
   [common.human :as human]
   [cljs.pprint :refer [cl-format]]
   [cljsjs.photoswipe]
   [cljsjs.photoswipe-ui-default]
   [jibit.utils :as utils :refer [dissoc-in]]))

;;; events and handlers -- update db

;; OBS: Variations
;; reg-event-fx
;; reg-event-db

;; This is something we don't need in production. It can be left empty
;; there. This could also be something we don't need in figwheel
;; rounds if we can fix things in some end, but I don't know.

;; Remember to specify protocol. And no trailing slash.
;; (def server-uri "http://localhost:8088")
(def server-uri "")

(defn photo-image-uri
  [photo]
  (str server-uri "/photo/" (:uuid photo)))

(defn photo-thumbnail-uri
  [photo]
  (str server-uri "/thumbnail/" (:uuid photo)))

(defn get-photoswipe-elt
  []
  (first (array-seq (js/document.querySelectorAll ".pswp"))))

(defn rgb->rgba
  "Given hex string #RRGGBB turn it into #RRGGBBAA. Opacity is a decimal
  between [0, 1]"
  [rgb opacity]
  (let [op (int (* 255 opacity))]
    (cl-format nil "~a~2'0x" rgb op)))

(defn modal-state?
  "Is this state a modal one?"
  [state]
  ;; All non-nil states are modal, for now.
  state)

(defn i
  "{debug} Inspect DB."
  [& ks]
  (let [db @re-frame.db/app-db]
    (cond
      (nil? ks) (sort (keys db))
      :t (get-in db ks))))

(defn join-strings
  "Join nonempty non-nil strings with separator."
  ([coll]
   (join-strings ", " coll))
  ([sep coll]
   (apply str (interpose sep (filter identity coll)))))

(defn toggle-set-membership
  [x s]
  (if (s x)
    (clojure.set/difference s #{x})
    (clojure.set/union      s #{x})))

(defn dispatch-preventing-default-action
  [evt event]
  (. evt preventDefault)
  (re-frame/dispatch event))

(defn get-tag-by-id
  "Linear search from db"
  [db tag-id]
  (->> db
       :tags
       (filter #(= tag-id (-> % :id)))
       first))

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
   :on-success [response]
   :on-failure [response {:status :fail}]})

;;;;

(defn photo-ids
  "Get a set of photo/ids from a collection of photo maps."
  [p]
  (set (map :id p)))

;;;; Our ajax responses from server.
;; Events come with a map with keys [:status :response], where status
;; is one of {:ok :fail :user}.
;; - :ok is a 200
;; - :fail is a 5xx
;; - :user is a 2xx status where original request wasn't completed
;;   because of ambiguous data or similar -- we want to ask the user
;;   about how to proceed.

(defn ok?
  [status]
  (= status :ok))

(re-frame/reg-event-fx
 :on-get-gear
 (fn [{db :db} [_ {status :status response :response}]]
   {:db (assoc db :gear (:gear response))}))

(re-frame/reg-event-db
 :on-get-tags
 (fn [db [_ {status :status response :response}]]
   (assoc db :tags response)))

(re-frame/reg-event-fx
 :on-save-gear
 (fn [_ [_ {status :status response :response}]]
   (debug "Gear updated I guess")))

(re-frame/reg-event-db
 :on-get-photos
 (fn [db [_ {status :status new-photos :response}]]
   (when (ok? status)
     (let [ids (clojure.set/intersection (photo-ids new-photos)
                                         (:selected db))]
       (assoc db
              :photos new-photos
              :selected ids)))))

(re-frame/reg-event-fx
 :on-tag
 (fn [cofx [_ {status :status response :response}]]
   ;; We've just tagged images
   (when (ok? status)
     {:dispatch [:get-photos]})))

(defn format-tag-delete-problems
  "Deleting a tag can have issues, we decode those issues to the user.
  The argument map `problems` potentially has keys :photos#
  and :children that should be formatted as a prompt message."
  [tag problems]
  [:ul.problem-list
   (when-let [photos# (:photos# problems)]
     [:li photos# " photos have been tagged with "
      (:name tag) ". They will be untagged."])
   (when-let [children (:children problems)]
     [:li "The following subtags will be lifted one level up."
      [:ul
       (for [c children]
         ^{:key c} [:li c])
       ]])])

(re-frame/reg-event-fx
 :on-delete-tag
 (fn [{db :db} [_ {status :status response :response}]]
   (let [tag-id (:tag-id response)
         tag (get-tag-by-id db tag-id)]
     (case status
       :ok {:dispatch-n [[:reload-tags]
                         [:close-and-clear-tag-dlg]]}
       :user {:dispatch [:show-prompt
                         {:title (str "Confirm deletion of " (:name tag))
                          :text (format-tag-delete-problems tag (:problems response))
                          :label-yes "Go ahead!"
                          :callback-yes [:delete-tag tag-id true]}]}
       :fail {}))))

(re-frame/reg-event-fx
 :on-create-tag
 (fn [cofx [_ {status :status response :response}]]
   ;; We've made a new tag in the system.
   (when (ok? status)
     {:dispatch-n [[:reload-tags]
                   [:close-and-clear-tag-dlg]]})))

;;;; Ajax end

(re-frame/reg-fx
 :show-photo-on-lightbox
 (fn [{width :width
       height :height
       :as photo}]
   (let [thumbnail-uri (photo-thumbnail-uri photo)
         full-uri (photo-image-uri photo)]
     (doto (js/PhotoSwipe. (get-photoswipe-elt)
                           js/PhotoSwipeUI_Default
                           (clj->js [{:src full-uri
                                      :msrc thumbnail-uri
                                      :w width
                                      :h height}])
                           #js {:index 0})
       (.init)))))

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
                :state ()
                :selected-tags #{}
                :selected #{}
                :init-done true})]
     {:dispatch-n [[:reload-tags]
                   [:reload-gear]]
      :db db'})))

(re-frame/reg-event-fx
 :reload-tags
 (fn [_ _]
   {:http-xhrio (build-edn-request :method :get
                                   :uri "/tags"
                                   :response :on-get-tags)}))

(re-frame/reg-event-fx
 :reload-gear
 (fn [_ _]
   {:http-xhrio (build-edn-request :method :get
                                   :uri "/gear"
                                   :response :on-get-gear)}))


;; Store changed input value under a chain of keys in db,
;; under :input.
(re-frame/reg-event-db
 :change-input
 (fn [db [_ data-ids new-value]]
   (if (nil? new-value)
     (dissoc-in db (conj (seq data-ids) :input))
     (assoc-in db (conj (seq data-ids) :input) new-value))))

(def fancydate-presets
  ^{:doc "Ordered seq of maps with keys {:key :label :begin-dt
  :end-dt}. Begin/end dts are zero-arity functions that return Date
  objects to evaluate applied range."}
  (let [today jibit.datetime/moment]
    [{:key :nil
      :label "[Clear]"
      :begin-dt (constantly nil)
      :end-dt (constantly nil)}
     {:key :today
      :label "Today"
      :begin-dt #(today)
      :end-dt #(today)}
     {:key :yesterday
      :label "Yesterday"
      :begin-dt #(.subtract (today) 1 "days")
      :end-dt #(.subtract (today) 1 "days")}
     {:key :this-week
      :label "This week"
      :begin-dt #(.startOf (today) "week")
      :end-dt #(today)}
     {:key :last-year
      :label "Last year"
      :begin-dt #(.startOf (.subtract (today) 1 "years") "year")
      :end-dt #(.endOf (.subtract (today) 1 "years") "year")}
     {:key :this-year
      :label "This year"
      :begin-dt #(.startOf (today) "year")
      :end-dt #(today)}]))

;; Fancydate can provide with presets, we'll update accordingly
(re-frame/reg-event-db
 :change-input-date-preset
 (fn [db [_ data-ids preset]]
   (let [{:keys [begin-dt end-dt]} (first (filter #(#{preset} (:key %)) fancydate-presets))
         begin (begin-dt)
         end (end-dt)]
     (assoc-in db
               (conj (seq data-ids) :input)
               (into {}
                     [(if begin
                        [:begin (jibit.datetime/iso-format begin)])
                      (if end
                        [:end (jibit.datetime/iso-format end)])])))))

(re-frame/reg-event-db
 :toggle-input
 (fn [db [_ data-ids]]
   (update-in db (conj (seq data-ids) :input) not)))

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

(def gear-table-columns
  [{:title "Type" :field "gear_type", :width 80}
   {:title "EXIF Make" :field "exif_make" :width 200}
   {:title "EXIF Model" :field "exif_model" :width 200}
   {:title "Label" :field "user_label" :width 200 :editor "input"}])

(def gear-table-column-sort
  [{:column "gear_type" :dir "asc"}])

;; TODO Terrible that we are bypassing the sub model!
(defn flatten-gear-list
  []
  (-> @re-frame.db/app-db :gear))

(re-frame/reg-event-db
 :show-gear-dlg
 (fn [db _]
   (update db :state conj :gear-dialog)))

(re-frame/reg-event-fx
 :save-gear-dlg
 (fn [{db :db} [_ gear-data]]
   (let [data (map #(select-keys % ["id" "user_label"]) gear-data)]
     {:http-xhrio (build-edn-request :method :post
                                     :uri "/gear"
                                     :params {:gear-data data}
                                     :response :on-save-gear)})))

(re-frame/reg-event-fx
 :close-gear-dlg
 (fn [{db :db} _]
   {:db (update db :state pop)
    :dispatch [:reload-gear]}))

(re-frame/reg-event-db
 :show-edit-tag-dlg
 (fn [db [_ tag-id]]
   ;; omg a linear search
   (let [tag (first (filter #(= tag-id (-> % :id)) (:tags db)))
         ;; apply the checkbox if there's color present.
         tag (assoc tag :tag-color? (-> tag :style_color boolean))]
     (-> db
         (update :state conj :tag-dialog)
         (assoc-in [:input :tag] tag)))))

;; User has selected not to save tag color and we'll give feedback
;; about the choice by clearing out any selected color in the picker.
(re-frame/reg-event-db
 :clear-tag-color
 (fn [db _]
   (if (-> db :input :tag :tag-color?)
     (assoc-in db [:input :tag :style_color] nil)
     db)))

(re-frame/reg-event-db
 :activate-tag-use-color
 (fn [db _]
   (assoc-in db [:input :tag :tag-color?] true)))

(re-frame/reg-event-db
 :show-create-tag-dlg
 (fn [db _]
   (update db :state conj :tag-dialog)))

(re-frame/reg-event-fx
 :delete-tag
 (fn [{db :db} [_ tag-id & [surely?]]]
   {:http-xhrio (build-edn-request :method :delete
                                   :uri "/tag"
                                   :params {:tag-id tag-id
                                            :bypass? surely?}
                                   :response :on-delete-tag)}))

(re-frame/reg-event-fx
 :create-new-tag
 (fn [{db :db} _]
   (let [input (-> db :input :tag)]
     {:http-xhrio (build-edn-request :method :post
                                     :uri "/tag"
                                     :params input
                                     :response :on-create-tag)})))

(re-frame/reg-event-db
 :close-and-clear-tag-dlg
 (fn [db _]
   (-> db
       (update :state pop)
       ;; Clear set values, if any
       (assoc-in [:input :tag] {}))))

(re-frame/reg-event-db
 :show-prompt
 (fn [db [_ {title :title
             text :text
             label-yes :label-yes
             label-no :label-no
             callback-yes :callback-yes
             :or {title "Confirm"
                  text "Are you sure?"
                  label-yes "Ok"
                  label-no "Cancel"}}]]
   (-> db
       (update :state conj :modal-prompt)
       (assoc :prompt {:title title
                       :text text
                       :label-yes label-yes
                       :label-no label-no
                       :callback-yes callback-yes}))))

(re-frame/reg-event-db
 :close-prompt
 (fn [db _]
   (update db
           :state pop
           :prompt empty)))

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
                             (assoc :tags (or (:selected-tags db) #{})))]
     (debug "Get photos with:" filter-criteria)
     {:http-xhrio (build-edn-request :method :get
                                     :uri "/photos"
                                     :params filter-criteria
                                     :response :on-get-photos)})))

(re-frame/reg-event-fx
 :show-photo
 (fn [_ [_ photo]]
   (if-not (:is_raw photo)
     {:show-photo-on-lightbox photo}
     {})))

;;; queries from db

(re-frame/reg-sub
 :current-state
 (fn [db _]
   (peek (:state db))))

(re-frame/reg-sub
 :state-stack
 (fn [db _]
   (:state db)))

(re-frame/reg-sub
 :prompt
 (fn [db _]
   (:prompt db)))

(re-frame/reg-sub
 :tags
 (fn [db _]
   (-> db :tags)))

(re-frame/reg-sub
 :gear-raw
 (fn [db _]
   (-> db :gear)))

;; Tags but in a map of (tag-id -> tag)
(re-frame/reg-sub
 :tags-map
 (fn [db _]
   (into {} (map (juxt :id identity) (-> db :tags)))))

(re-frame/reg-sub
 :selected-photos#
 (fn [db _]
   (count (-> db :selected))))

;; Set of all selected photos
(re-frame/reg-sub
 :selected-photos-all
 (fn [db _]
   (-> db :selected)))

;; Is this photo selected or not
(re-frame/reg-sub
 :selected-photo?
 (fn [db [_ photo-id]]
   (contains? (-> db :selected)
              photo-id)))

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

(defn tag-view
  "Render a small element that visually represents a clickable tag."
  [tag]
  (let [tag-id (:id tag)
        photos-selected? (pos? @(re-frame/subscribe [:selected-photos#]))
        selections (re-frame/subscribe [:selected-tags])
        selected? (@selections tag-id)]
    ^{:key tag-id}
    [:li {:on-click #(when photos-selected?
                       (re-frame/dispatch [:toggle-tag-on-selected tag-id]))
          :on-context-menu #(dispatch-preventing-default-action % [:toggle-tag-filter tag-id])
          :on-double-click #(re-frame/dispatch [:show-edit-tag-dlg tag-id])
          :class (str
                  (if photos-selected? "" "not-taggable")
                  \space
                  (if selected? "selected" ""))
          :style (if-let [color (:computed_color tag)]
                   {:background-color (rgb->rgba color 0.20)
                    :border-color color})
          :title (or (:description tag) "")}
     (:name tag)]))

(defn tags-view []
  [:ul.tags-bar-big
   (doall
    (for [t @(re-frame/subscribe [:tags])]
      (tag-view t)))
   [:div#create-tag.button
    {:on-click #(re-frame/dispatch [:show-create-tag-dlg])}
    "+"]])

(defn render-tags-from-ids
  [tags-db tag-ids]
  [:ul.inline-tags
   (doall
    (for [tid tag-ids]
      ^{:key (str "inline-tag-" tid)}
      (tag-view (tags-db tid))))])

;;; Views and components

(defn data-bound-toggle-button
  [data-ids {:keys [label-on
                    label-off
                    class-on
                    class-off]
             :or {label-on "On"
                  label-off "Off"
                  class-on "button-toggle-on"
                  class-off "button-toggle-off"}}]
  (let [bound @(re-frame/subscribe [:input data-ids])
        class (if bound class-on class-off)
        label (if bound label-on label-off)]
    [:a.button {:class class
                :on-click #(re-frame/dispatch [:toggle-input data-ids])}
     label]))

(defn slide [photo]
  (let [tags-map (re-frame/subscribe [:tags-map])
        selected? @(re-frame/subscribe [:selected-photo? (:id photo)])]
    [:div.slide-wrapper
     [:div.slide
      {:on-context-menu #(dispatch-preventing-default-action
                          %
                          [:select-photo (:id photo)])
       :class (when selected? "selected-slide")}
      [:img {:class (when (:is_raw photo)
                      "raw-image")
             :on-click #(re-frame/dispatch [:show-photo photo])
             :src (photo-thumbnail-uri photo)}]
      [:ul.info
       [:li (human/datestamp (:taken_ts photo))]
       [:li (:camera_exif_model photo)]
       [:li (:lens_exif_model photo)]
       [:li (human/focal-length (:focal_length_35 photo)) " mm"]
       [:li (human/aperture (:aperture photo))]
       [:li (human/shutter-speed (:shutter_speed photo)) " s"]
       (when-not (zero? (:exposure_comp photo))
         [:li (human/exp-comp (:exposure_comp photo)) " EV"])
       [:li "ISO " (:iso photo)]
       (when (:is_raw photo)
         [:li "RAW"])
       (when-let [tags (seq (:tagged/ids photo))]
         [:li (render-tags-from-ids @tags-map tags)])
       ]]]))

(defn data-bound-input
  "Build an input element that binds into a chain `data-ids`. Props is a
  map that goes into creating the element.

  `props` can have an optional key `:clearable?` that will incorporate
  hacks to make the input text or search clearable in firefox via a
  button.

  Optional `textarea?` makes this a textarea."
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
        ;; Hack to make firefox render a "Clear input" button
        clearable? (and (#{:input :search} (:type props))
                        (:clearable? props))
        props (-> props
                  (assoc :on-change change-fn
                         :value bound)
                  (dissoc :clearable?))
        ;; more checkbox handling
        props (if checkbox?
                (assoc props :checked bound)
                props)]
    [:div
     {:class (if clearable? "input-outer-clearable" "input-outer")}
     [(if textarea? :textarea :input) props]
     (when clearable?
       [:div.input-clear
        {:title "Clear input"
         :on-click #(re-frame/dispatch [:change-input data-ids nil])}
        "X"])]))

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

(defn data-bound-fancydate
  "Create a fancydate type dropdown/date range selector. Binds to a
  chain `data-ids`. Property map `props` is fed to the outermost
  element (a div)."
  [data-ids props]
  (let [{:keys [begin end]} @(re-frame/subscribe [:input data-ids])
        repr (cond
               (and (empty? begin) (empty? end))
               "Not selected"

               (= begin end)
               (jibit.datetime/org-format begin)

               :t
               (str (jibit.datetime/org-format begin)
                    " ⟶ "
                    (jibit.datetime/org-format end)))
        props' props]
    [:div.fancydate
     props'
     repr
     [:ul.fd-dropdown
      (for [{:keys [key label]} fancydate-presets]
        ^{:key key}
        [:li {:on-click #(re-frame/dispatch [:change-input-date-preset data-ids key])}
         label])
      [:li "Custom"
       [:ul
        [:li "Begin "
         (data-bound-input
          (conj data-ids :begin)
          {:type :date})]
        [:li "End "
         (data-bound-input
          (conj data-ids :end)
          {:type :date})]]
       ]]]))

(defn filter-panel []
  [:div#filter
   [:div.filter-row
    [:div.filter-column
     [:h1 "Filter options"]
     [:div
      [data-bound-input [:filter :camera]
       {:type :search
        :clearable? true
        :list "camera-list"
        :title "Filter by camera make or model"
        :placeholder "Camera"}]
      [data-bound-input [:filter :lens]
       {:type :search
        :clearable? true
        :list "lens-list"
        :title "Filter by lens make or model"
        :placeholder "Lens"}]]

     [:div
      "Taken "
      (data-bound-fancydate [:filter :taken-ts]
                            {:style {:width "50%"}})]
     [:div
      "Imported "
      (data-bound-fancydate [:filter :imported-ts]
                            {:style {:width "50%"}})]

     [:h1 "Order options"]
     "Order by "
     [data-bound-select [:filter :order-by]
      [{:name "Taken" :value "taken_ts"}
       {:name "Rating" :value "rating"}]]]

    [:div.filter-column
     "Only untitled "
     [data-bound-toggle-button [:filter :show-only-untitled?] {}]
     [:br]
     "Only untagged "
     [data-bound-toggle-button [:filter :show-only-untagged?] {}]
     [:br]
     "Only unrated "
     [data-bound-toggle-button [:filter :show-only-unrated?] {}]
     [:br]
     "Only developed "
     [data-bound-toggle-button [:filter :show-only-uncooked?] {}]
     [:br]
     "Filter by selected tags "
     [data-bound-toggle-button [:filter :tags-union?]
     {:label-on "ANY"
      :label-off "ALL"
      :class-on "btn-any-tag"
      :class-off "btn-all-tags"}]]]

   [:div.filter-row
    [:a#filter-btn.button
     {:on-click #(re-frame/dispatch [:get-photos])}
     "Filter"]]])

(defn modal-prompt
  []
  (let [enabled? (= :modal-prompt @(re-frame/subscribe [:current-state]))
        {title :title
         text :text
         label-yes :label-yes
         label-no :label-no
         callback-yes :callback-yes} @(re-frame/subscribe [:prompt])]
    [:div#modal-prompt {:class (if enabled? "modal-shown" "")}
     [:h1 title]
     [:div text]
     [:div.footer
      [:div.button {:on-click #(do (re-frame/dispatch callback-yes)
                                   (re-frame/dispatch [:close-prompt]))}
       label-yes]
      [:div.button {:on-click #(re-frame/dispatch [:close-prompt])}
       label-no]]]))

(defn modal-background []
  (let [enabled? (modal-state? @(re-frame/subscribe [:current-state]))]
    [:div#modal-bg {:class (if enabled? "modal-shown" "")}]))

;; Let's experiment with form-3 components, now that we need to hack
;; something around component display.
(defn gear-edit-dialog []
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
                              (debug "Redrawing gear table!")
                              (.setData @tabulator-instance (clj->js (flatten-gear-list))))
      :display-name "gear-edit-dialog"
      :reagent-render
      (fn []
        (let [enabled? (some #{:gear-dialog} @(re-frame/subscribe [:state-stack]))]
          [:div.modal-dialog
           {:class (when enabled? "modal-shown")}
           [:h1 "Gear"]
           [:div
            [:table#gear-table]]
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

(defn tag-edit-dialog []
  (let [enabled? (some #{:tag-dialog} @(re-frame/subscribe [:state-stack]))
        tag @(re-frame/subscribe [:input [:tag]])
        new? (-> tag :id nil?)
        tag-sub-level (fn [tag]
                        (-> tag :nested_label count dec))
        tags (->> @(re-frame/subscribe [:tags])
                  (map (juxt :id :name tag-sub-level)))
        incomplete-form? (-> tag :name empty?)
        parent-id-invalid? (and (-> tag :id)
                                (= (-> tag :id)
                                   (-> tag :parent_id)))
        prevent-saving? (or incomplete-form? parent-id-invalid?)]
    [:div.modal-dialog
     {:class (if enabled? "modal-shown" "")}
     [:h1 (if new?
            "Create tag"
            "Modify tag")
      \space
      [:span.tag-edit
       (:name tag)]]

     [:div.dialog-row
      [:div.dialog-column
       [:label {:for "tag-name"} "Name"] [:br]
       [data-bound-input [:tag :name]
        {:type :text
         :placeholder "Name"
         :auto-complete "off"
         :name "tag-name"}]
       (when incomplete-form?
         " * required")
       [:br]
       [:label {:for "tag-description"} "Description"] [:br]
       [data-bound-input [:tag :description]
        {:type :text
         :placeholder "Description"
         :name "tag-description"}
        :yes-do-a-textarea]]

      [:div.dialog-column
       [:label "Parent tag"] [:br]
       [data-bound-select [:tag :parent_id]
        (concat [{:name "--" :value "nil"}]
                (for [[tag-id tag-name sub-level] tags]
                  {:name (str (apply str (repeat sub-level "—")) " " tag-name)
                   :value tag-id}))]
       (when parent-id-invalid?
         " !!")
       [:br]
       [:label "Tag color"] [:br]
       [data-bound-input [:tag :style_color]
        {:type :color
         :on-click #(re-frame/dispatch [:activate-tag-use-color])}]
       "  "
       [data-bound-input [:tag :tag-color?]
        {:type :checkbox
         :on-click #(re-frame/dispatch [:clear-tag-color])
         :value "yes"}]
       "Use color"]]

     [:div.footer
      [:a.button {:on-click #(when-not prevent-saving?
                               (re-frame/dispatch [:create-new-tag]))
                  :class (when prevent-saving? "btn-disabled")}
       (if new? "Create" "Save")]
      [:a.button {:on-click #(re-frame/dispatch [:close-and-clear-tag-dlg])}
       "Close"]
      (when-not new?
        [:a.button.red.right
         {:on-click #(re-frame/dispatch [:show-prompt
                                         {:title (str "Confirm deletion of " (:name tag))
                                          :label-yes "Delete!"
                                          :callback-yes [:delete-tag (-> tag :id)]}])}
         "Delete..."])]]))

(defn projector []
  [:div.pswp {:tab-index -1 :role "dialog" :aria-hidden true}
   [:div.pswp__bg]
   [:div.pswp__scroll-wrap
    [:div.pswp__container
     [:div.pswp__item]
     [:div.pswp__item]
     [:div.pswp__item]]
    [:div.pswp__ui.pswp__ui--hidden
     [:div.pswp__top-bar
      [:div.pswp__counter]
      [:button.pswp__button.pswp__button--close {:title "Close (Esc)"}]
      [:button.pswp__button.pswp__button--fs {:title "Toggle fs"}]
      [:button.pswp__button.pswp__button--zoom {:title "Zoom in/out"}]
      [:div.pswp__preloader
       [:div.pswp__preloader__icn
        [:div.pswp__preloader__cut
         [:div.pswp__preloader__donut]]]]
      [:button.pswp__button.pswp__button--arrow-left {:title "Previous"}]
      [:button.pswp__button.pswp__button--arrow-right {:title "Next"}]
      [:div.pswp__caption
       [:div.pswp__caption__center]]]]]])

(defn lighttable []
  (let [photos (re-frame/subscribe [:photos])]
    (fn []
      [:div#lighttable
       (for [photo @photos]
         ^{:key (:uuid photo)} [slide photo])])))

(defn gear-datalists [id gear-type]
  (let [gear @(re-frame/subscribe [:gear-raw])]
    [:datalist
     {:id id}
     ;; TODO could also build that into the subscription
     (for [g gear :when (= (name gear-type) (:gear_type g))]
       ^{:key (str "gear-" (:id g))}
       [:option {:value (:exif_model g)}])]))

(defn header []
  (let [pc @(re-frame/subscribe [:photos-count])
        sc @(re-frame/subscribe [:selected-photos#])]
    [:h1#head "Photos"
     [:span.photos-count \# pc]
     (when (pos? sc)
       [:span.selection-count "Selected " sc \space
        [:a {:class "clear"
             :on-click #(re-frame/dispatch [:clear-selection])
             :href "#"} "Clear"]])
     ;; BTW this darktable-esque button thing could work, initally
     ;; thought of this launcher as a dev-time thing but could work.
     [:a {:style {:float :right :color :silver :margin-right "20px"}
          :on-click #(re-frame/dispatch [:show-gear-dlg])
          :href "#"}
      "Gear"]]))

(defn user-interface []
  [:div
   ;; Visible "zero-level" elements
   [header]
   [filter-panel]
   [tags-view]
   [lighttable]

   ;; unrendered metadata for form inputs
   [gear-datalists "camera-list" :camera]
   [gear-datalists "lens-list"   :lens]

   ;; Modal lightbox, let's playfully call it a projector
   ;; [projector]
   ;; NB we currently skip reactifying this and go with native DOM
   ;; elements. index.html contains the elements.

   ;; Modal dialogs that go above level zero. Shown and hidden as
   ;; needed.
   [modal-background]
   [tag-edit-dialog]
   [gear-edit-dialog]

   ;; Prompt downmost
   [modal-prompt]])

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
