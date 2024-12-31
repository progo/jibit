(ns jibit.utils
  (:require
   ajax.edn
   [jibit.uri :as uri]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [cljs.pprint :refer [cl-format]]
   ))


(defn dispatch-preventing-default-action
  [evt event]
  (. evt preventDefault)
  (re-frame/dispatch event))


(defn rgb->rgba
  "Given hex string #RRGGBB turn it into #RRGGBBAA. Opacity is a decimal
  between [0, 1]"
  [rgb opacity]
  (let [op (int (* 255 opacity))]
    (cl-format nil "~a~2'0x" rgb op)))

(defn get-default-export-scheme-key
  []
  (-> common.settings/export-schemes first first))

;; dissoc-in, inspired by whynotsoftware blog post but recreated
;; because of lack of license.

(defn dissoc-in
  "Dissociate from nested maplike `m` using keys `ks`."
  [m [k & ks]]
  (cond
    ;; empty assoc
    (nil? k) m

    ;; just one key left
    (nil? ks) (dissoc m k)

    ;; several keys
    :else (assoc m k (dissoc-in (get m k) ks))))


(defn photo-ids
  "Get an ordered seq of photo/ids from a collection of photo maps."
  [p]
  (map :id p))


(defn build-edn-request
  "Build a request building map that can be passed to :http-xhrio. This
  is our basic XHR protocol of choice, EDN on both ends. Use :body only
  when uploading files (using js/FormData objects)."
  [&{method :method
     uri :uri
     params :params
     body :body
     response :response}]
  {:method method
   :uri (str uri/server-uri uri)
   :params params
   :body body
   :format (ajax.edn/edn-request-format)
   :response-format (ajax.edn/edn-response-format)
   :on-success [response]
   :on-failure [response {:status :fail}]})
