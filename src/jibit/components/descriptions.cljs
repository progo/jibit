(ns jibit.components.descriptions
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [taoensso.timbre :as timbre :refer [debug spy]]
   [jibit.utils :as utils :refer [build-edn-request]]
   ))


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



(re-frame/reg-event-db
 :fx/update-photo
 (fn [db [_ photo field value]]
   (assoc-in db [:db/focused-photo field] value)))


(re-frame/reg-event-fx
 :fx/save-descriptions-focused-photo
 (fn [{db :db} [_]]
   (let [photo (-> db :db/focused-photo)]
     {:db (-> db
              (assoc :activity "Saving...")
              (update :photos update-photo photo))
      :http-xhrio (build-edn-request :method :post
                                     :uri "/title-photo"
                                     :params {:id (:id photo)
                                              :title (:title photo)
                                              :notes (:notes photo)}
                                     :response :on-background-save)})))


(defn changed-new-value [evt]
  (str (-> evt .-target .-value)))


(defn keydown-handler-handle-ctrl-s
  [evt]
  (let [ctrl? (.-ctrlKey evt)
        s? (= (.-key evt) "s")]
    (when (and ctrl? s?)
      (.preventDefault evt)
      (re-frame/dispatch [:fx/save-descriptions-focused-photo])
      false)))


(defn descriptions-panel
  []
  (let [focused-photo @(re-frame/subscribe [:sub/focused-photo])]
    [:div
     {:class (str "border "
                  "border-orange-400 "
                  "rounded-xl "
                  "p-2 ")}
     [:input
      {:type :input
       :class "text-md bg-black p-1"
       :placeholder "Title"
       :on-change (fn [evt]
                    (re-frame/dispatch
                     [:fx/update-photo
                      focused-photo
                      :title
                      (changed-new-value evt)]))
       :on-key-down keydown-handler-handle-ctrl-s
       :value (or (-> focused-photo :title) "")}]

     [:textarea
      {:type :input
       :class "text-sm w-full block"
       :rows 3
       :placeholder "Notes"
       :on-change (fn [evt]
                    (re-frame/dispatch
                     [:fx/update-photo
                      focused-photo
                      :notes
                      (changed-new-value evt)]))
       :on-key-down keydown-handler-handle-ctrl-s
       :value (or (-> focused-photo :notes) "")}]

     [:textarea
      {:type :input
       :class "text-sm w-full block"
       :rows 3
       :placeholder "Computer-generated description"
       :read-only true
       :value (or (-> focused-photo :llm_description) "")}]]))
