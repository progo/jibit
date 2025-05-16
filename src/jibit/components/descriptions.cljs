(ns jibit.components.descriptions
  (:require
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [jibit.components.inputs :refer [data-bound-input]]))

(defn descriptions-panel
  []
  (let [focused-photo @(re-frame/subscribe [:sub/focused-photo])]
    [:div
     {:class (str "w-full "
                  "bg-stone-600 "
                  "rounded-lg "
                  "p-2 ")}
     [:input
      {:type :input
       :class "text-md"
       :placeholder "Title"
       :value (or (-> focused-photo :title) "")}]
     [:textarea
      {:type :input
       :class "text-sm w-full block"
       :rows 3
       :placeholder "Description"
       :value (or (-> focused-photo :notes) "")}]
     [:textarea
      {:type :input
       :class "text-sm w-full block"
       :rows 3
       :placeholder "LLM description"
       :read-only true
       :value (or (-> focused-photo :llm_description) "")}]]))
