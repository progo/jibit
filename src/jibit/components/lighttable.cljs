(ns jibit.components.lighttable
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [common.human :as human]
   [jibit.uri :as uri]
   [taoensso.timbre :as timbre :refer [debug spy]]
   jibit.components.tags
   [jibit.components.inputs :refer [data-bound-toggle-button data-bound-input data-bound-select]]
   ))

(defn slide [photo]
  (let [tags-map @(re-frame/subscribe [:tags-map])
        gear-db @(re-frame/subscribe [:gear-db])
        focused? @(re-frame/subscribe [:focused-photo? (:id photo)])
        selected? @(re-frame/subscribe [:selected-photo? (:id photo)])]

    [:div.slide-wrapper
     [:div.slide
      {:class (str (when selected? "selected-slide")
                   \space
                   (when focused? "focused-slide"))
       :on-click #(re-frame/dispatch [:focus-photo (:id photo)])}

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
         :src (uri/photo-thumbnail-uri photo)}]]

      [:ul.info
       [:li (human/datestamp (:taken_ts photo))]
       ;; [:li (-> photo :camera_id gear-db human/gear-label)]
       ;; [:li (-> photo :lens_id gear-db human/gear-label)]
       (when (seq (:title photo))
         [:li (:title photo)])
       [:li.technical-details
        ;; TODO color-code the crop factor a little bit, and put 35mm
        ;; equivalent in a tooltip!
        [:span.small-label "FL"]
        (human/focal-length (:focal_length photo)) " mm"
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
       ;; [:li
       ;;  [:span.small-label "ID"]
       ;;  (:id photo)]

       (when-let [tags (seq (:tagged/ids photo))]
         [:li (jibit.components.tags/render-tags-from-ids tags-map tags)])
       ]

      ]]))


(defn lighttable []
  (let [photos (re-frame/subscribe [:photos])]
    (fn []
      [:div#lighttable
       (for [photo @photos]
         ^{:key (:uuid photo)} [slide photo])])))
