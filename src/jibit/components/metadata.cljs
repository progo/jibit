(ns jibit.components.metadata
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [common.human :as human]
   [jibit.utils :as utils]))

(def nbsp \u00a0)

(defn metadata-view []
  (let [photo @(re-frame/subscribe [:sub/focused-photo])
        gear-db @(re-frame/subscribe [:gear-db])]
    (when (and photo gear-db)
      [:div.w-full.p-2
       [:div.border.border-orange-400.p-2.rounded-xl
        [:ul.list-disc.list-inside
         [:li "Taken " (human/datestamp (:taken_ts photo))]
         [:li "Imported " (human/datestamp (:import_ts photo))]
         [:li "" (:original_file photo)]
         [:li
          (-> photo :focal_length) nbsp "mm"
          \space
          (human/shutter-speed (:shutter_speed photo))
          \space
          "f/" (-> photo :aperture)
          \space
          "ISO" (-> photo :iso)
          \space
          "EC" (-> photo :exposure_comp)]
         [:li (-> photo :camera_id gear-db human/gear-label)]
         [:li (-> photo :lens_id gear-db human/gear-label)]
         ]]])))
