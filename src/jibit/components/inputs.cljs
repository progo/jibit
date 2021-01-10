(ns jibit.components.inputs
  "Provide DB-bound input components."
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [re-frame.core :as re-frame]
            [keybind.core :as keybind]
            cljs.reader))

(defn data-bound-toggle-button
  [data-ids {:keys [label-on
                    label-off
                    class-on
                    class-off]
             :or {label-on "On"
                  label-off "Off"
                  class-on "button-toggle-on"
                  class-off "button-toggle-off"}}]
  (let [bound @(re-frame/subscribe [:input data-ids])
        class (if bound class-on class-off)
        label (if bound label-on label-off)]
    [:a.button {:class class
                :on-click #(re-frame/dispatch [:toggle-input data-ids])}
     label]))


(defn data-bound-input
  "Build an input element that binds into a chain `data-ids`. Props is a
  map that goes into creating the element.

  The map `props` goes to the DOM element as-is.

  Optional named arguments:
  - `textarea?` makes this a textarea.
  - `:clearable?` that will incorporate hacks to make the input text or
    search clearable in firefox via a button.
  - `on-enter` (fn) function to call when user hits RET
  - `custom-eval-db-fn` (fn :: db -> new-value -> db) pass custom
    evaluation when modified field value is being stored in db
  "
  [data-ids props & {:keys [clearable? textarea? on-enter custom-eval-db-fn]}]
  (let [checkbox? (= :checkbox (:type props))
        bound @(re-frame/subscribe [:input data-ids])
        ;; Checkboxes (thus react) won't accept nils as false
        bound (if checkbox?
                (boolean bound)
                bound)
        change-fn #(re-frame/dispatch [:change-input
                                       data-ids
                                       (if checkbox?
                                         (not bound)
                                         (-> % .-target .-value))
                                       (or custom-eval-db-fn nil)])
        keyup-fn (when on-enter
                   #(when (= "Enter" (.-key %))
                      (on-enter)))
        ;; Hack to make firefox render a "Clear input" button
        clearable? (and (#{:input :search} (:type props)) clearable?)
        props (assoc props
                     :on-change change-fn
                     :on-key-up keyup-fn
                     :value bound)
        ;; more checkbox handling
        props (if checkbox?
                (assoc props :checked bound)
                props)
        ;; Disable global keybinds while we're focused on the input
        props (assoc props
                     :on-focus keybind/disable!
                     :on-blur keybind/enable!)]
    [:div
     {:class (if clearable? "input-outer-clearable" "input-outer")}
     [(if textarea? :textarea :input) props]
     (when clearable?
       [:div.input-clear
        {:title "Clear input"
         :on-click #(re-frame/dispatch [:change-input data-ids nil])}
        "X"])]))


(defn data-bound-select
  "Build a select element that binds into a chain `data-ids`. Options is
  a seq of maps with keys [:name, :value]. The value comes out thru as
  a clojurescript readable symbol or value."
  [data-ids options]
  (let [bound @(re-frame/subscribe [:input data-ids])
        change-fn #(re-frame/dispatch [:change-input
                                       data-ids
                                       (cljs.reader/read-string
                                        (-> % .-target .-value))])
        props {:on-change change-fn
               :value (or bound "")}]
    [:select props
     (doall
      (for [{name :name value :value} options]
        ^{:key value} [:option {:value value} name]))]))
