(ns jibit.components.activity-indicator
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]))

(defn activity-indicator
  "Nonmodal spinner with an in-progress message."
  []
  (let [activity-msg @(re-frame/subscribe [:activity])]
    [:div.text-base.flex.flex-row.items-end
     {:class (when (nil? activity-msg) "hidden")}
     (str activity-msg)
     [:img.ml-2.h-12 {:src "/img/film-spinner-sq-orange.gif"}]]))

