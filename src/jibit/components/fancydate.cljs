(ns jibit.components.fancydate
  "Provide a DB-bound Date selector."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [re-frame.core :as re-frame]
            [jibit.components.inputs :refer [data-bound-input]]
            jibit.datetime))

(def fancydate-presets
  ^{:doc "Ordered seq of maps with keys {:key :label :begin-dt
  :end-dt}. Begin/end dts are zero-arity functions that return Date
  objects to evaluate applied range."}
  (let [today jibit.datetime/moment]
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

(defn fancydate-custom-updater
  "Make sure that when user selects a beginning, the end isn't before
  it. We'll sniff from the end of `data-ids` if we are dealing
  with :begin or :end here."
  [db data-ids new-value]
  (let [full-path (conj (seq data-ids) :input)
        path-init (butlast full-path)
        which-end (last full-path) ; :begin/:end
        other-end (case which-end
                    :end :begin
                    :begin :end)
        other-value (get (get-in db path-init) other-end)

        ;; these dates happen to be in ISO so we can just string-compare
        other-value* (cond
                       ;; New begin goes after end.
                       (and (= which-end :begin)
                            (>= new-value other-value))
                       new-value

                       ;; New end goes before begin.
                       (and (= which-end :end)
                            (<= new-value other-value))
                       new-value

                       :else other-value)]
    (-> db
        (assoc-in full-path new-value)
        (assoc-in (conj (vec path-init) other-end) other-value*))))


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

(defn data-bound-fancydate
  "Create a fancydate type dropdown/date range selector. Binds to a
  chain `data-ids`. Property map `props` is fed to the outermost
  element (a div)."
  [data-ids props]
  (let [{:keys [begin end]} @(re-frame/subscribe [:input data-ids])
        not-selected? (and (empty? begin) (empty? end))
        repr (format-date-range begin end)
        props' (update props
                       :class
                       #(when not-selected?
                         (str % " no-selection")))]
    [:div.fancydate
     props'
     repr
     [:ul.fd-dropdown
      (for [{:keys [key label]} fancydate-presets]
        ^{:key key}
        [:li {:on-click #(re-frame/dispatch [:change-input-date-preset data-ids key])}
         label])
      [:li "Custom"
       [:ul
        [:li "Begin "
         (data-bound-input
          (conj data-ids :begin)
          {:type :date}
          :custom-eval-db-fn fancydate-custom-updater)]
        [:li "End "
         (data-bound-input
          (conj data-ids :end)
          {:type :date}
          :custom-eval-db-fn fancydate-custom-updater)]]
       ]]]))
