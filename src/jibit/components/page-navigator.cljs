(ns jibit.components.page-navigator
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]))

(defn page-navigator
  "Show number of photos, where we are, and offer navigation tools."
  []
  (let [{total-photos :total-count
         offset :offset
         limit :limit} @(re-frame/subscribe [:photos-window])
        curr-page (inc (quot offset limit))
        pages# (Math/ceil (/ total-photos limit))
        show-navigation? (not (and (zero? offset)
                                   (<= total-photos limit)))]
    (if show-navigation?
      [:div {:style {:display :inline-block}}
       [:span.photos-count
        \# (inc offset) \- (min total-photos (+ offset limit)) \/ total-photos]
       [:div.page-navigator
        [:a.navigate {:on-click #(re-frame/dispatch [:navigate-page -1])} "◀"]
        \space
        [:span.curr-page curr-page]
        [:span.slash "/"]
        [:span.pagecount pages# \space]
        [:a.navigate {:on-click #(re-frame/dispatch [:navigate-page 1])} "▶"]]]
      [:span.photos-count \# total-photos])))

