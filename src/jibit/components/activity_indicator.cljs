(ns jibit.components.activity-indicator
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]))

(defn activity-indicator
  "Nonmodal spinner with an in-progress message."
  []
  (let [activity-msg @(re-frame/subscribe [:activity])]
    [:div#activity
     {:class (when (nil? activity-msg) "hidden")}
     (str activity-msg)
     \space
     [:img {:src "/img/film-spinner-sq-orange.gif"}]]))

