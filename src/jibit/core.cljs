(ns ^:figwheel-hooks jibit.core
  (:require
   reagent.dom
   [re-frame.core :as re-frame]
   [day8.re-frame.http-fx]
   [taoensso.timbre :as timbre :refer [debug spy]]
   [common.human :as human]
   [jibit.uri :as uri]
   common.settings
   [cljs.pprint :refer [cl-format]]
   [keybind.core :as keybind]
   [jibit.components.quick-label :as quick-label]
   [jibit.components.header]
   [jibit.components.lighttable]
   [jibit.components.tag-edit]
   [jibit.components.tags]
   [jibit.components.filters]
   ;; ["photoswipe" :as PhotoSwipe]
   [jibit.components.gear-editor :as gear-editor]
   [jibit.utils :as utils :refer [build-edn-request photo-ids]]))

(defn get-photoswipe-elt
  []
  (first (array-seq (js/document.querySelectorAll ".pswp"))))

;; Poor implementation but this is so because we haven't fit PSWP into react framework
(defn projector-on?
  "Is projector currently active and visible."
  []
  (.contains (.-classList (get-photoswipe-elt)) "pswp--visible"))

(defn modal-state?
  "Is this state a modal one?"
  [state]
  ;; All non-nil states are modal, for now.
  state)

;; We could store context out of `db` to be perhaps less smelly
(defn user-in-context?
  ([ctx]
   (user-in-context? @re-frame.db/app-db ctx))
  ([db ctx]
   (if (nil? ctx)
     true
     ((-> db :contexts) ctx))))

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

;; `coverage` = nil ==> count all
;; `coverage` = :just-selection ==> count just selected
(defn get-selection
  "Get all selected photos, or currently focused photo. Return a seq."
  ([db]
   (get-selection db nil))
  ([db coverage]
   (let [all? (nil? coverage)
         sel (-> db :selected)
         focus (-> db :focused-photo)]
     (cond
       (seq sel) sel
       (and all? focus) (list focus)
       :else ()))))


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
 (fn [db [_ {status :status {metadata :meta photos :photos} :response}]]
   (when (ok? status)
     (let [photo-ids-set (set (photo-ids photos))]
       (-> db
           (update :focused-photo photo-ids-set)
           (assoc :photos-window metadata)
           (update :selected #(clojure.set/intersection photo-ids-set %))
           (assoc :photos photos))))))

(re-frame/reg-event-fx
 :on-match-tags
 (fn [{db :db} [_ {status :status tag-ids :response}]]
   {:dispatch [:get-photos]
    :db (assoc db :activity nil)}))

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

(def ^js/PhotoSwipe photoswipe-js)

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
                                 {:src (uri/photo-image-uri p)
                                  :msrc (uri/photo-thumbnail-uri p)
                                  :w (:width p)
                                  :h (:height p)})
                               cooked-photos)]
     (do
       (set! photoswipe-js (js/PhotoSwipe.
                            (get-photoswipe-elt)
                            js/PhotoSwipeUI_Default
                            (clj->js projected-photos)
                            #js {:index index}))
       (.init photoswipe-js)))))

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
                :contexts #{}
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


;; TODO! Our data structure choice is poor if we need to often use this.
(defn get-photo-by-id
  [pid photos]
  (first
   (drop-while (fn [p]
                 (not= (:id p) pid))
               photos)))


;;; Set tags on selected photos!
(re-frame/reg-event-fx
 :toggle-tag-on-selected
 (fn [{db :db} [_ tag-id]]
   (when-let [selection (seq (get-selection db))]
     {:http-xhrio (build-edn-request :method :post
                                     :uri "/tag-photo"
                                     :params {:tag tag-id
                                              :photos selection}
                                     :response :on-tag)})))

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
   (update db :selected #(utils/toggle-set-membership photo-id %))))

(re-frame/reg-event-db
 :select-focused-photo
 (fn [db _]
   (if-let [focused (-> db :focused-photo)]
     (update db :selected #(utils/toggle-set-membership focused %))
     db)))

(re-frame/reg-event-db
 :select-all-photos
 (fn [db _]
   (assoc db :selected (-> db :photos photo-ids set))))

(re-frame/reg-event-db
 :clear-selection
 (fn [db _]
   (update db :selected empty)))

(re-frame/reg-event-db
 :show-quick-tags-popup
 (fn [db _]
   (let [sel? (boolean (seq (get-selection db)))
         currently-shown? (boolean (user-in-context? db :quick-tags))
         show? (case [(boolean sel?) (boolean currently-shown?)]
                 [true true] false ; hide
                 [true false] true ; show
                 [false true] false ; hide
                 [false false] false)] ; don't show if no selection
     (update db :contexts (if show? conj disj) :quick-tags))))

;; Inspect taken_ts times of selection and redo the search using the
;; date range so that we focus on those dates.
(re-frame/reg-event-fx
 :filter-search-on-selected
 (fn [{db :db} _]
   (when-let [photo-ids (seq (get-selection db))]
     (let [all-photos (:photos db)
           ;; TODO. Again slow application of `get-photo-by-id` here.
           photos (map #(get-photo-by-id % all-photos) photo-ids)
           taken-tss (map (comp utils/truncate-time-component :taken_ts) photos)
           taken-min (apply min taken-tss)
           taken-max (apply max taken-tss)]
       ;; (println "Earliest" taken-min "latest" taken-max)
       {:db (-> db
                (assoc-in [:input :filter :taken-ts :begin] taken-min)
                (assoc-in [:input :filter :taken-ts :end] taken-max))
        :dispatch [:get-photos]}))))

;; Key binds

(def keybindings
  {"M-a" {:kw ::select-all :dispatch :select-all-photos}
   ;; "M-l" {:kw ::deselect-all :dispatch :clear-selection}
   "M-c" {:kw ::deselect-all :dispatch :clear-selection}
   "M-e" {:kw ::export-selected :dispatch :export-selected}
   "M-`" {:kw ::filter-around-selected :dispatch :filter-search-on-selected}

   ;; quick tags and its context-specific bindings
   "M-q" {:kw ::show-quick-tags
          :dispatch :show-quick-tags-popup}
   "M-w" {:kw ::match-tags
          :context :quick-tags
          :dispatch :match-tags-on-selected}

   "left" {:kw ::focus-previous :dispatch :focus-previous-photo}
   "right" {:kw ::focus-next :dispatch :focus-next-photo}
   "M-1" {:kw ::select-1 :dispatch :select-focused-photo}
   "enter" {:kw ::show-focused :dispatch :show-focused-photo}})


(defn init-keybindings! [m]
  (doseq [[key {kw :kw context :context event :dispatch}] keybindings]
    (let [handler (fn [e]
                    (when (user-in-context? context)
                      (utils/dispatch-preventing-default-action e [event])))]
      (keybind/bind! key kw handler))))

;; Redo keybindings each load
(keybind/unbind-all!)
(init-keybindings! keybindings)



;; Toggle tag from query

(re-frame/reg-event-fx
 :toggle-tag-filter
 (fn [{db :db} [_ tag-id]]
   (let [tags-selection (utils/toggle-set-membership tag-id (:selected-tags db))]
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
 (fn [{db :db} [_ export-template]]
   (if-let [photos (get-selection db)]
     {:db (assoc db :activity "Exporting...")
      :http-xhrio (build-edn-request :method :post
                                     :uri "/export"
                                     :params {:photos photos
                                              :template (or export-template
                                                            (utils/get-default-export-scheme-key))}
                                     :response :on-export)}
     {:dispatch [:show-message "No photos selected."]})))

(re-frame/reg-event-fx
 :match-tags-on-selected
 (fn [{db :db} _]
   (when-let [selection (seq (get-selection db))]
     (if (= 1 (count selection))
       {:dispatch [:show-message "Not matching tags on a selection of one."]}

       {:db (assoc db :activity "Matching tags...")
        :http-xhrio (build-edn-request :method :post
                                       :uri "/tag-photo/match"
                                       :params {:photos selection}
                                       :response :on-match-tags)
        }))))

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
   (let [window (or (-> db :photos-window) {})
         filter-criteria (-> db
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

                             clean-nil-values

                             (assoc :offset (or (window :offset) 0)))]
     (debug "Get photos with:" filter-criteria)
     {:http-xhrio (build-edn-request :method :get
                                     :uri "/photos"
                                     :params filter-criteria
                                     :response :on-get-photos)})))

(defn shift-photos-window
  "Calculate new photo view offset based on number of photos. `delta`
  corresponds to a number of 'pages' to shift over."
  [{:keys [total-count offset limit]} delta]
  (let [offset' (+ offset (* delta limit))
        ;; wrap around to 'page 1' after reaching max
        offset-w (if (>= offset' total-count)
                   0
                   offset')
        ;; wrap around to last page, if below min
        offset-w (if (neg? offset-w)
                   (* limit
                      (Math/floor (/ total-count limit)))
                   offset-w)]
    {:total-count total-count
     :offset offset-w
     :limit limit}))

(re-frame/reg-event-fx
 :navigate-page
 (fn [{db :db} [_ delta]]
   {:db (update db :photos-window shift-photos-window delta)
    :dispatch [:get-photos]}))

(re-frame/reg-event-fx
 :show-photo
 (fn [{db :db} [_ photo]]
   (if-not (:is_raw photo)
     {:show-photo-on-lightbox [photo (-> db :photos)]}
     {:dispatch [:show-message "I can't show raw photos."]})))

(re-frame/reg-event-fx
 :show-focused-photo
 (fn [{db :db} _]
   (if (projector-on?)
     ;; Close projector
     (.close photoswipe-js)
     ;; Open projector
     (when-let [currently-focused (-> db :focused-photo)]
       (let [photo (get-photo-by-id currently-focused (-> db :photos))]
         {:dispatch [:show-photo photo]})))))

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

;; Count of all selected or focused photos
;; `coverage` = nil ==> count all
;; `coverage` = :just-selection ==> count just selected
(re-frame/reg-sub
 :selected-photos#
 (fn [db [_ coverage]]
   (count (get-selection db coverage))))

;; Set of all selected or focused photos
(re-frame/reg-sub
 :selected-photos-all
 (fn [db [_ coverage]]
   (get-selection db coverage)))

;; Is this photo selected or not
(re-frame/reg-sub
 :selected-photo?
 (fn [db [_ photo-id]]
   (contains? (-> db :selected)
              photo-id)))

;; Currently focused photo
(re-frame/reg-sub
 :focused-photo?
 (fn [db [_ photo-id]]
   (= (-> db :focused-photo) photo-id)))

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
 :in-context?
 (fn [db [_ ctx]]
   (-> (:contexts db)
       ctx
       boolean)))

(re-frame/reg-sub
 :photos
 (fn [db _]
   (-> db :photos)))

(re-frame/reg-sub
 :photos-count
 (fn [db _]
   (-> db :photos-count)))

(re-frame/reg-sub
 :photos-window
 ;; :total-count/ :offset / :limit
 (fn [db _]
   (-> db :photos-window)))

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

(defn quick-tags-popup []
  (let [show? @(re-frame/subscribe [:in-context? :quick-tags])
        sel# @(re-frame/subscribe [:selected-photos#])
        multiple-selected? (> sel# 1)
        ]
    [:div#quick-tags-popup
     {:class (when-not show? "hidden")}
     [:h1 "Tag " sel#  " photo" (if multiple-selected? \s) \:]
     [:p "a bit of a todo this here..."]
     [:br]
     (when multiple-selected?
       [:h1 "Match tags" [:kbd "[M-w]"]])
     ]))

(defn message-box
  "Nonmodal temporary message in the corner"
  []
  (let [message @(re-frame/subscribe [:message])]
    [:div#message
     {:class (when (nil? message) "hidden")}
     (str message)]))

(defn user-interface []
  [:div#main
   ;; Visible "zero-level" elements
   [jibit.components.filters/filter-panel]
   [jibit.components.tags/tags-view]
   [jibit.components.lighttable/lighttable]
   [quick-label/quick-label-edit]
   [jibit.components.header/header]
   [message-box]

   ;; unrendered metadata for form inputs
   [gear-editor/gear-datalists "camera-list" :camera]
   [gear-editor/gear-datalists "lens-list"   :lens]

   ;; Modal lightbox, let's playfully call it a projector
   ;; [projector]
   ;; NB we currently skip reactifying this and go with native DOM
   ;; elements. index.html contains the elements.

   ;; Modal dialogs that go above level zero. Shown and hidden as
   ;; needed.
   [modal-background]
   [jibit.components.tag-edit/tag-edit-dialog]
   [gear-editor/gear-edit-dialog]
   [quick-tags-popup]

   ;; Prompt downmost
   [modal-prompt]])

;;; re-frame boilerplate below

(defn get-app-element []
  (js/document.getElementById "app"))

(defn mount [el]
  (reagent.dom/render [user-interface] el))

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

(defn ^:dev/after-load on-reload []
  (mount-app-element)
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
