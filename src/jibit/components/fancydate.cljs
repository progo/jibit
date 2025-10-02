(ns jibit.components.fancydate
  "Provide a DB-bound Date selector."
  (:require [cljs.core.match :refer-macros [match]]
            [taoensso.timbre :as timbre :refer [debug spy]]
            [re-frame.core :as re-frame]
            [reagent.core :as re]
            [jibit.components.inputs :refer [data-bound-input]]
            [jibit.datetime :refer [today ->dt ymd->dt dt->ymd ymd->iso]]))

(def fancydate-presets
  ^{:doc "Ordered seq of maps with keys {:key :label :begin-dt
  :end-dt}. Begin/end dts are zero-arity functions that return Date
  objects to evaluate applied range."}
  (let [today jibit.datetime/today]
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


;;;;
;;
;; Two units of data:
;;
;; - YMD is a 3-tuple of ints
;; - positive numbers
;; - month in [1, 12]
;;
;; - DR (date range) is a 2-tuple
;;   - elem is YMD or nil
;;
;; Everything is a range, as before.
;; - [nil Y] means all dates up to Dec 31 Y
;; - [YM Y] means all dates from Y/M/1 to Dec 31 Y
;; - etc
;; - [YMD YMD] when same, still means the very day

(defn validate-ymd
  "Return a valid YMD or nil."
  [ymd]
  (let [[y m d] ymd]
    (match [y m d]
           [nil nil nil] ymd
           [y nil nil] ymd
           [y m nil] ymd
           [y nil d] nil
           [y m d] (when (.isValid (ymd->dt ymd))
                     ymd)
           [_ _ _] nil)))

(defn ymd
  "Deals with YMDs instead of dates or ISO dates"
  [value on-change]
  (let [[y m d] value
        valid-ymd (validate-ymd [y m d])
        change (fn [ymd]
                 (on-change ymd))]
    [:div.block.whitespace-nowrap
     {:class (str
              "pr-0 ymd border "
              (if valid-ymd
                "border-yellow-700"
                "border-red-600"))}
     [:input.dtinput.w-14
      {:type :number
       :placeholder "YYYY"
       :value y
       :on-change #(change [(-> % .-target .-value) m d])}]
     " "
     [:input.dtinput.w-12
      {:type :number
       :placeholder "MM"
       :value m
       :on-change #(change [y (-> % .-target .-value) d])}]
     " "
     [:input.dtinput.w-12
      {:type :number
       :placeholder "DD"
       :value d
       :on-change #(change [y m (-> % .-target .-value)])}]
     " "
     [:button.fdclear
      {:on-click #(change nil)}
      "X"]]))


;; The data that fancydate and fancydate2 handle is basically a
;; 2-tuple but we express it as a map: {:begin YMD :end YMD}, where
;; YMD: a triplet presenting a date Y/M/D
(defn fancydate2
  [data on-change]
  (let [clear-fn (fn []
                   (on-change :both [nil nil nil]))
        today-fn (fn []
                   (let [tday (-> (today) dt->ymd)]
                     (on-change :both tday)))]
    [:div.flex.flex-row
     [:button.px-1.border.rounded-lg
      {:on-click clear-fn}
      "Clear"]
     [:button.px-1.border.rounded-lg
      {:on-click today-fn}
      "Today"]
     [:span.px-1
      "from"]
     [ymd (:begin data) #(on-change :begin %)]
     [:span.px-1
      "to"]
     [ymd (:end data) #(on-change :end %)]]))


(defn data-bound-fancydate2
  "Wraps around lower-level functional fancydate2."
  [data-ids options]
  (let [bound (re-frame/subscribe [:input data-ids])
        change-fn (fn [end new-end-value]
                    (let [value' (cond
                                   (= end :both)
                                   {:begin new-end-value
                                    :end new-end-value}

                                   :else
                                   (assoc @bound end new-end-value))]
                      (re-frame/dispatch [:change-input data-ids value'])))
        props {}]
    (fn []
      [fancydate2 @bound change-fn])))

;;;;
;;

(defn format-date-range
  "Format a date range that begins from one date and ends with another.
  It is an open ended range when only one is defined; if the dates are
  same, it's assumed to be the one day only; if both are null we don't
  have a range."
  [begin end]
  (cond
    (and (empty? begin) (empty? end))
    "Not selected"

    (= begin end)
    (jibit.datetime/org-format begin)

    :t
    (str (jibit.datetime/org-format begin)
         " ⟶ "
         (jibit.datetime/org-format end))))


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

