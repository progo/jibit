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
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   [jibit.components.fancydate :refer [data-bound-fancydate]]
   [jibit.utils :as utils :refer [dissoc-in]]))

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

;; Defining client/server conversations

(defn build-edn-request
  "Build a request building map that can be passed to :http-xhrio. This
  is our basic XHR protocol of choice, EDN on both ends. Use :body only
  when uploading files (using js/FormData objects)."
  [&{method :method
     uri :uri
     params :params
     body :body
     response :response}]
  {:method method
   :uri (str server-uri uri)
   :params params
   :body body
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

(defn mapify-seq
  "Given a seq of elts (maps) that have a unique key under id-key (for
  example, :id or :photo/id), build a map of {id -> elt}"
  [coll & {:keys [id-key] :or {id-key :id}}]
  (->> coll
       (map (juxt id-key identity))
       (into {})))

(defn ok?
  [status]
  (= status :ok))

(re-frame/reg-event-fx
 :import-photos
 (fn [{db :db} [_ files-cmp]]
   (let [fd (js/FormData.)
         files (.-files files-cmp)
         files# (count (array-seq files))
         name (.-name files-cmp)]
     (if (pos? files#)
       (do
         (doseq [file-key (.keys js/Object files)]
           (.append fd name (aget files file-key)))
         {:db (assoc db :activity "Importing...")
          :http-xhrio (build-edn-request :method :post
                                         :uri "/upload"
                                         :body fd
                                         :response :on-sync-inbox)})
       ;; Do nothing when no files (we probs cleared/cancelled the input)
       {}))))


(re-frame/reg-event-fx
 :show-file-import
 (fn [_ _]
   {:activate-file-upload nil}))

(re-frame/reg-event-db
 :on-get-gear
 (fn [db [_ {status :status response :response}]]
   ;; We'll build two data structures from the same data.
   (-> db
       (assoc :gear (:gear response))
       (assoc :gear-map (mapify-seq (:gear response))))))

(re-frame/reg-event-db
 :on-get-tags
 (fn [db [_ {status :status tags :response}]]
   ;; We'll build two data structures from the same data.
   (-> db
       (assoc :tags tags)
       (assoc :tags-map (mapify-seq tags)))))

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
 :on-export
 (fn [{db :db} [_ {status :status response :response}]]
   {:db (assoc db :activity nil)
    :dispatch-n [[:show-message "Export finished."]]}))

;; A generic event for when we do small background sync/saves
(re-frame/reg-event-fx
 :on-background-save
 (fn [{db :db} [_]]
   {:db (assoc db :activity nil)}))

;; This handles both Inbox-sync and Import responses.
(re-frame/reg-event-fx
 :on-sync-inbox
 (fn [{db :db} [_ {status :status response :response}]]
   (let [photos# (-> response :total-files)
         get-photos? (and (ok? status)
                          (pos? photos#))
         message (if (zero? photos#)
                   "No photos imported"
                   (cl-format nil "~d photo~:p imported" photos#))]
     {:db (assoc db :activity nil)
      :dispatch-n [[:show-message message]
                   (when get-photos?
                     [:get-photos])]})))


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
         tag (-> db :tags-map (get tag-id))]
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
 ;; We take a photo and a coll of photos, we look up the index of the
 ;; photo in the mix (linear time) and build a JSON object for
 ;; PhotoSwipe to show around.
 :show-photo-on-lightbox
 (fn [[photo photos]]
   (let [cooked-photos (filter #(not (:is_raw %)) photos)

         ;; Find the index of photo among the cooked photos
         [index _] (first
                    (filter (fn [[ind phot]] (= photo phot))
                            (map-indexed vector cooked-photos)))

         ;; build a minimal data structure for PhotoSwipe
         projected-photos (map (fn [p]
                                 {:src (photo-image-uri p)
                                  :msrc (photo-thumbnail-uri p)
                                  :w (:width p)
                                  :h (:height p)})
                               cooked-photos)]
     (doto (js/PhotoSwipe. (get-photoswipe-elt)
                           js/PhotoSwipeUI_Default
                           (clj->js projected-photos)
                           #js {:index index})
       (.init)))))

;; activate input[type=file] to show a file selector
(re-frame/reg-fx
 :activate-file-upload
 (fn [_]
   (.click (js/document.getElementById "file-upload"))))

;; The popup label editor has to be positioned near where user
;; clicked.
(re-frame/reg-fx
 :position-quick-label-editor
 (fn [[x y]]
   (let [cmp (js/document.getElementById "quick-label-editor")]
     (set! (.. cmp -style -top) (str y "px"))
     (set! (.. cmp -style -left) (str x "px")))))

;; Focus the first input
(re-frame/reg-fx
 :focus-quick-label-editor
 (fn [_]
   (let [cmp (js/document.getElementById "quick-label-edit-title")]
     (js/setTimeout #(.focus cmp) 0))))

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
                :activity nil
                :message nil
                :show-filter-panel? true
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

(re-frame/reg-event-fx
 :show-message
 (fn [{db :db} [_ msg]]
   (if msg
     {:db (assoc db :message msg)
      :dispatch-after-delay {:event [:show-message nil]
                             :timeout 2000}}
     {:db (assoc db :message nil)})))

(re-frame/reg-event-db
 :toggle-show-filter-panel
 (fn [db _]
   (update db :show-filter-panel? not)))

;; Store changed input value under a chain of keys in db,
;; under :input.
;;
;; An optional function `custom-eval-fn` can be passed that'll have the
;; database, the data ids, and the new value, and will return new
;; database. This is to allow more *complex* logic on updating user
;; input. Handle with caution and always, TODO: get rid of it.
(re-frame/reg-event-db
 :change-input
 (fn [db [_ data-ids new-value custom-eval-fn]]
   (cond
     (not (nil? custom-eval-fn))
     (custom-eval-fn db data-ids new-value)

     (nil? new-value)
     (dissoc-in db (conj (seq data-ids) :input))

     :else
     (assoc-in db (conj (seq data-ids) :input) new-value))))

(re-frame/reg-event-db
 :toggle-input
 (fn [db [_ data-ids]]
   (update-in db (conj (seq data-ids) :input) not)))

;;; Set tags on selected photos!
(re-frame/reg-event-fx
 :toggle-tag-on-selected
 (fn [{db :db} [_ tag-id]]
   (when-let [sel (seq (-> db :selected))]
     ;; (timbre/debugf "Setting tag %d to photos %s" tag-id sel)
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
   (let [tag (-> db :tags-map (get tag-id))
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

(defn match-human-label->gear-id
  "Do a linear search over human labeled gear, return an ID or nil if
  not found."
  [label gear-list & {:keys [label-fn] :or {label-fn human/gear-label}}]
  (let [label (clojure.string/trim
               (or label ""))]
    (if (empty? label)
      nil
      ;; we build a seq of [human-label {gear-map}] tuples
      (->> (map (juxt label-fn identity) gear-list)
           (filter #(= label (first %)))
           first
           second
           :id))))

(defn clean-nil-values
  "Remove those keys from map `m` where value is nil."
  [m]
  (into {}
        (filter (fn [[k v]]
                  (not (nil? v)))
                m)))

(re-frame/reg-event-fx
 :export-selected
 (fn [{db :db} _]
   (if-let [selection (seq (-> db :selected))]
     {:db (assoc db :activity "Exporting...")
      :http-xhrio (build-edn-request :method :post
                                     :uri "/export"
                                     :params {:photos selection}
                                     :response :on-export)}
     {:dispatch [:show-message "No photos selected."]})))

(re-frame/reg-event-fx
 :do-sync-inbox
 (fn [{db :db} _]
   (debug "Going to check for new photos in the inbox")
   {:db (assoc db :activity "Syncing...")
    :http-xhrio (build-edn-request :method :post
                                   :uri "/inbox/sync"
                                   :timeout (* 30 60 1000)
                                   :params []
                                   :response :on-sync-inbox)}))

(re-frame/reg-event-fx
 :get-photos
 (fn [{db :db} _]
   (let [filter-criteria (-> db
                             :input
                             :filter

                             ;; add currently selected tags to the mix
                             (assoc :tags (:selected-tags db))

                             ;; if gear is something preselected we
                             ;; want to filter by id
                             (assoc :lens-id (match-human-label->gear-id
                                              (-> db :input :filter :lens)
                                              (-> db :gear)))
                             (assoc :camera-id (match-human-label->gear-id
                                                (-> db :input :filter :camera)
                                                (-> db :gear)))

                             clean-nil-values)]
     (debug "Get photos with:" filter-criteria)
     {:http-xhrio (build-edn-request :method :get
                                     :uri "/photos"
                                     :params filter-criteria
                                     :response :on-get-photos)})))

(re-frame/reg-event-fx
 :show-photo
 (fn [{db :db} [_ photo]]
   (if-not (:is_raw photo)
     {:show-photo-on-lightbox [photo (-> db :photos)]}
     {:dispatch [:show-message "I can't show raw photos."]})))

;; User clicks on a slide to edit the texts, we'll pop up
;; a (semi)modal dialog to let them.
(re-frame/reg-event-fx
 :show-quick-label-editor
 (fn [{db :db} [_ photo click-position]]
   {:db (-> db
            (assoc-in [:input :label-edit]
                      {:photo photo
                       :title (:title photo)
                       :notes (:notes photo)})
            (update :state conj :quick-label-edit))
    :focus-quick-label-editor nil
    :position-quick-label-editor click-position}))

(defn update-photo
  "Search linearly for photo in photos by ID and update the record."
  [photos photo]
  ;; This is frustratingly bad code now, reflects on poor usage of
  ;; data structures for `photos`.
  (let [selection-fn #(= (:id %) (:id photo))
        antiselect-fn (complement selection-fn)
        head (take-while antiselect-fn photos)
        tail (drop 1 (drop-while antiselect-fn photos))]
    ;; now `photos` == `head` . [`photo`] . `tail`
    (concat head [photo] tail)))

;; User closes (and saves) the label editor. We'll want to update the
;; labels instantly on local and then send a background query to the
;; server to persist them there.
(re-frame/reg-event-fx
 :save-close-label-popup
 (fn [{db :db} [_]]
   (let [label (-> db :input :label-edit)
         photo' (assoc (-> label :photo)
                       :title (:title label)
                       :notes (:notes label))]
     {:db (-> db
              (assoc :activity "Saving...")
              (update :state pop)
              (update :photos update-photo photo'))
      :http-xhrio (build-edn-request :method :post
                                     :uri "/title-photo"
                                     :params {:id (:id photo')
                                              :title (:title photo')
                                              :notes (:notes photo')}
                                     :response :on-background-save)})))

;;; queries from db

(re-frame/reg-sub
 :message
 (fn [db _]
   (:message db)))

(re-frame/reg-sub
 :activity
 (fn [db _]
   (:activity db)))

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
 :show-filter-panel?
 (fn [db _]
   (-> db :show-filter-panel?)))

(defn falsey?
  "Python-like falsy check on values."
  [x]
  (case x
    nil true
    0 true
    false true
    "" true
    {} true
    [] true
    () true
    #{} true
    false))

(re-frame/reg-sub
 :active-filters
 (fn [db _]
   (let [filters (or (-> db :input :filter) {})
         ;; Would be cool to have these declared in a better way. This
         ;; is duplicated, a second source of truth.
         values-to-check #{:show-only-untitled?
                           :show-only-untagged?
                           :show-only-unrated?
                           :show-only-uncooked?
                           :imported-ts
                           :taken-ts
                           :camera
                           :lens}]
     (some (fn [k]
             (not (falsey? (filters k))))
           values-to-check))))

(re-frame/reg-sub
 :gear-raw
 (fn [db [_ gear-type]]
   (if (nil? gear-type)
     ;; Give them everything
     (:gear db)
     ;; Or filter by type...
     (filter #(= (name gear-type) (:gear_type %)) (:gear db)))))

;; All gear in a map of {id -> gear}
(re-frame/reg-sub
 :gear-db
 (fn [db _]
   (-> db :gear-map)))

;; All tags in a map of {id -> tag}
(re-frame/reg-sub
 :tags-map
 (fn [db _]
   (-> db :tags-map)))

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

;; TODO can we refactor this with `tag-view`?
(defn small-tag-view
  "Render a small RO tag element meant to be used as information."
  [tag]
  (let [tag-id (:id tag)]
    ^{:key tag-id}
    [:li {:style (if-let [color (:computed_color tag)]
                   {:background-color (rgb->rgba color 0.20)
                    :border-color color})
          :title (or (:description tag) "")}
     (:name tag)]))

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
   (let [tags @(re-frame/subscribe [:tags])]
     (if (seq tags)
       (doall
        (for [t tags]
          (tag-view t)))
       [:span#no-tags "Tags will appear here."]))
   [:div#create-tag.button
    {:on-click #(re-frame/dispatch [:show-create-tag-dlg])}
    "+"]])

(defn render-tags-from-ids
  [tags-db tag-ids]
  [:ul.inline-tags
   (doall
    (for [tid tag-ids]
      ^{:key (str "inline-tag-" tid)}
      (small-tag-view (tags-db tid))))])

;;; Views and components

(defn slide [photo]
  (let [tags-map @(re-frame/subscribe [:tags-map])
        gear-db @(re-frame/subscribe [:gear-db])
        selected? @(re-frame/subscribe [:selected-photo? (:id photo)])]

    [:div.slide-wrapper
     [:div.slide
      {:class (when selected? "selected-slide")}

      ;; Offer 3 different selections. Work on one type.
      [:div.overlay-controls
       {:on-double-click #(re-frame/dispatch [:show-photo photo])}

       [:img.selector.sel1
        {:src (if selected? "/img/selection-1-fill.png" "/img/selection-1.png")
         :on-click #(re-frame/dispatch [:select-photo (:id photo)])}]
       ;; [:img.selector.sel2
       ;;  {:src "/img/selection-2.png"
       ;;   :on-click #(debug "clicking on X2")}]
       ;; [:img.selector.sel3
       ;;  {:src "/img/selection-3.png"
       ;;   :on-click #(debug "clicking on X3")}]

       ;; Then offer other quick edit tools, such as RATING
       [:span
        {:style {:position :absolute :bottom 0}
         :on-click (fn [e]
                     (re-frame/dispatch [:show-quick-label-editor
                                         photo
                                         [(.-pageX e) (- (.-pageY e) 20)]]))}
        (if (:title photo)
          "Edit title"
          "Add title")]]

      [:div.img-wrapper
       [:div {:class (if selected? "encircled" "hidden")}]

       ;; Here we have support to show any "raw" indications
       ;; (when (:is_raw photo)
       ;;   [:div.raw-photo])

       [:img.photograph
        {:class (when (:is_raw photo) "raw-image")
         :src (photo-thumbnail-uri photo)}]]

      [:ul.info
       [:li (human/datestamp (:taken_ts photo))]
       ;; [:li (-> photo :camera_id gear-db human/gear-label)]
       ;; [:li (-> photo :lens_id gear-db human/gear-label)]
       (when (seq (:title photo))
         [:li (:title photo)])
       [:li.technical-details
        [:span.small-label "FL"]
        (human/focal-length (:focal_length_35 photo)) " mm"
        [:span.small-label "ƒ/"]
        (human/aperture (:aperture photo))
        [:span.small-label "SS"]
        (human/shutter-speed (:shutter_speed photo))
        [:span.small-label "ISO"] (human/iso (:iso photo))
        (when-not (zero? (:exposure_comp photo))
          [:span
           [:span.small-label "EC"]
           (human/exp-comp (:exposure_comp photo)) " EV" ])]

       ;; Debug things...
       [:li
        [:span.small-label "ID"]
        (:id photo)]

       (when-let [tags (seq (:tagged/ids photo))]
         [:li (render-tags-from-ids tags-map tags)])
       ]

      ]]))

(defn filter-panel []
  (let [get-photos #(re-frame/dispatch [:get-photos])
        show-panel? @(re-frame/subscribe [:show-filter-panel?])]
    [:div#filter
     {:class (when-not show-panel? "hidden")}
     [:div.filter-row
      [:div.filter-column
       [:h1 "Filter options"]
       [:div
        [data-bound-input [:filter :camera]
         {:type :search
          :list "camera-list"
          :title "Filter by camera make or model"
          :placeholder "Camera"}
         :on-enter get-photos
         :clearable? true]
        [data-bound-input [:filter :lens]
         {:type :search
          :list "lens-list"
          :title "Filter by lens make or model"
          :placeholder "Lens"}
         :on-enter get-photos
         :clearable? true]]

       [:div
        "Taken "
        [data-bound-fancydate [:filter :taken-ts]
                              {:style {:width "50%"}}]]
       [:div
        "Imported "
        [data-bound-fancydate [:filter :imported-ts]
                              {:style {:width "50%"}}]]

       [:h1 "Order options"]
       "Order by "
       [data-bound-select [:filter :order-by]
        [{:name "Taken" :value "taken_ts"}
         {:name "Rating" :value "rating"}]]]

      [:div.filter-column
       [data-bound-toggle-button [:filter :show-only-untitled?] {}]
       " Only untitled"
       [:br]
       [data-bound-toggle-button [:filter :show-only-untagged?] {}]
       " Only untagged"
       [:br]
       [data-bound-toggle-button [:filter :show-only-unrated?] {}]
       " Only unrated"
       [:br]
       [data-bound-toggle-button [:filter :show-only-uncooked?] {}]
       " Only developed"
       [:br]
       [data-bound-toggle-button [:filter :tags-union?]
        {:label-on "ANY"
         :label-off "ALL"
         :class-on "btn-any-tag"
         :class-off "btn-all-tags"}]
       " Filter by selected tags"]]

     [:div.filter-row
      [:a#filter-btn.button
       {:on-click get-photos}
       "Filter"]]]))

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

(defn gear-edit-dialog []
  (let [gear (re-frame/subscribe [:gear-raw])]
    [gear-edit-dialog* @gear]))

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
        prevent-saving? (or incomplete-form? parent-id-invalid?)
        do-save #(when-not prevent-saving?
                   (re-frame/dispatch [:create-new-tag]))]
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
         :name "tag-name"}
        :on-enter do-save]
       (when incomplete-form?
         " * required")
       [:br]
       [:label {:for "tag-description"} "Description"] [:br]
       [data-bound-input [:tag :description]
        {:type :text
         :placeholder "Description"
         :name "tag-description"}
        :textarea? true]]

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
      [:a.button {:on-click do-save
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
  (let [gear @(re-frame/subscribe [:gear-raw gear-type])]
    [:datalist
     {:id id}
     (for [g gear]
       ^{:key (str "gear-" (:id g))}
       [:option {:data-value (:id g)}
        (human/gear-label g)
        ])]))

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

(defn message-box
  "Nonmodal temporary message in the corner"
  []
  (let [message @(re-frame/subscribe [:message])]
    [:div#message
     {:class (when (nil? message) "hidden")}
     (str message)]))

(defn activate-button
  "When a buttonlike `a` is in focus and user presses SPC or RET, we
  want to activate it as if it was clicked on."
  [evt]
  (when (#{" " "Enter"} (.-key evt))
    (doto (.-target evt) (.click))))

(defn quick-label-edit
  "Small two-part editor for titles and notes."
  []
  (let [enabled? (= :quick-label-edit @(re-frame/subscribe [:current-state]))
        save-fn #(re-frame/dispatch [:save-close-label-popup])]
    [:div#quick-label-editor
     {:class (if enabled? "" "hidden")}

     [data-bound-input [:label-edit :title]
      {:type :text
       :placeholder "Title"
       :auto-complete "off"
       :id "quick-label-edit-title"
       :tab-index 0
       :name "photo-title"}
      :on-enter save-fn]
     [:br]

     [data-bound-input [:label-edit :notes]
      {:rows 7
       :placeholder "Notes..."
       :tab-index 0
       :name "photo-notes"}
      :textarea? true]

     [:br]
     [:a.button
      {:on-click save-fn
       :on-key-down activate-button
       :tab-index 0}
      "Close"]]))

(defn activity-indicator
  "Nonmodal spinner in top right corner."
  []
  (let [activity-msg @(re-frame/subscribe [:activity])]
    [:div#activity
     {:class (when (nil? activity-msg) "hidden")}
     (str activity-msg)
     \space
     [:img {:src "/img/film-spinner-sq-orange.gif"}]]))

(defn header []
  (let [pc @(re-frame/subscribe [:photos-count])
        sc @(re-frame/subscribe [:selected-photos#])]
    [:h1#head "Photos"
     [:span.photos-count \# pc]
     [filter-button]
     (when (pos? sc)
       [:span.selection-count "Selected " sc \space
        [:a {:class "clear"
             :on-click #(re-frame/dispatch [:clear-selection])
             :href "#"} "Clear"]])

     [:div#menu
      [:a {:on-click #(re-frame/dispatch [:show-gear-dlg])
           :title "Open gear data editor"
           :href "#"}
       "Gear"]
      \space

      [:a {:on-click #(re-frame/dispatch [:do-sync-inbox])
           :title "Sync inbox"
           :href "#"}
       "Inbox"]
      \space

      [:a {:on-click #(re-frame/dispatch [:show-file-import])
           :title "Select or drop photos to import"
           :href "#"}
       "Import"]
      [:input#file-upload.hidden
       {:name "upload"
        :on-change #(re-frame/dispatch [:import-photos (-> % .-target)])
        :type :file
        :multiple true}]
      \space

      [:a {:on-click #(re-frame/dispatch [:export-selected])
           :title "Export selected photos"
           :href "#"}
       "Export"]
      ]]))

(defn user-interface []
  [:div#main
   ;; Visible "zero-level" elements
   [header]
   [filter-panel]
   [tags-view]
   [lighttable]
   [activity-indicator]
   [message-box]
   [quick-label-edit]

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
