(ns jibit.components.lighttable
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [common.human :as human]
   [jibit.uri :as uri]
   [taoensso.timbre :as timbre :refer [debug spy]]
   jibit.components.tags
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   [jibit.utils :as utils :refer [photo-ids]]))


;;; Handles lighttable key events, when focused
(defn- handle-key-events [evt]
  (case (.-key evt)
    "ArrowLeft"
    (do
      (re-frame/dispatch [:fx/focus-previous-photo]))

    "ArrowRight"
    (do
      (re-frame/dispatch [:fx/focus-next-photo]))

    "Enter"
    (do
      (re-frame/dispatch [:fx/toggle-show-focused-photo]))

    nil))


(defn slide [photo]
  (let [tags-map @(re-frame/subscribe [:tags-map])
        focused? @(re-frame/subscribe [:sub/focused-photo? (:id photo)])
        selected? @(re-frame/subscribe [:selected-photo? (:id photo)])]

    [:div.slide-wrapper
     [:div.slide
      {:class (str (when selected? "selected-slide")
                   \space
                   (when focused? "focused-slide"))
       :on-click #(re-frame/dispatch [:ev/focus-photo photo])}

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
       ]

      [:div.img-wrapper
       [:div {:class (if selected? "encircled" "hidden")}]

       ;; Here we have support to show any "raw" indications
       ;; (when (:is_raw photo)
       ;;   [:div.raw-photo])

       [:img.photograph
        {:class (when (:is_raw photo) "raw-image")
         :src (uri/photo-thumbnail-uri photo)}]]

      [:ul.info
       [:li (human/datestamp (:taken_ts photo))]
       (when (seq (:title photo))
         [:li (:title photo)])
       (when-let [tags (seq (:tagged/ids photo))]
         [:li (jibit.components.tags/render-tags-from-ids tags-map tags)])]
      ]]))


(defn lighttable []
  (let [photos (re-frame/subscribe [:photos])]
    (fn []
      [:div#lighttable.outline-hidden
       {:tab-index 0
        :on-key-down handle-key-events}
       (for [photo @photos]
         ^{:key (:uuid photo)} [slide photo])])))


;; Photo focus things. One thing at a time only
;; This is a terrible approach that we use now, O(n) navigation, or worse.

(defn rotate
  "Rotate a seq shifting elements to the right by `n` elements.
  Negative `n` shifts to the left."
  [n s]
  (let [n' (if (neg? n)
             (+ (count s) n)
             n)]
    (concat
     (drop n' s)
     (take n' s))))

(defn focus-by-step
  "Given currently focused (or nil) `current-id` and an ordered seq of
  ids `photo-ids`, select the next one if step is 1, previous if step
  is -1. We wrap around if we reach the end."
  [current-id photo-ids step]
  (cond
    (nil? (seq photo-ids))
    nil

    (nil? current-id)
    (first photo-ids)

    :else
    (let [photo-ids' (rotate step photo-ids)
          mapped (map vector photo-ids photo-ids')]
      (->
       (drop-while (fn [[a b]]
                     (not= a current-id))
                   mapped)
       first
       second))))

(re-frame/reg-event-db
 :ev/focus-photo
 (fn [db [_ photo]]
   (assoc db :db/focused-photo photo)))

(re-frame/reg-event-db
 :fx/focus-next-photo
 (fn [db _]
   (update db :db/focused-photo #(focus-by-step % (:photos db) 1))))

(re-frame/reg-event-db
 :fx/focus-previous-photo
 (fn [db _]
   (update db :db/focused-photo #(focus-by-step % (:photos db) -1))))

;; Bring focus to the lighttable
(re-frame/reg-event-fx
 :fx/focus-lighttable
 (fn [_ _]
   (.focus (js/document.getElementById "lighttable"))))
