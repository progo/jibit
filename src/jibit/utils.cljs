(ns jibit.utils
  (:require
   ajax.edn
   common.settings
   [jibit.uri :as uri]
   [reagent.core :as reagent]
   [re-frame.core :as re-frame]
   [cljs.pprint :refer [cl-format]]
   ))



(defn ok?
  [status]
  (= status :ok))

(defn falsey?
  "Python-like falsy check on values."
  [x]
  (case x
    nil true
    0 true
    false true
    "" true
    {} true
    [] true
    () true
    #{} true
    false))


(defn mapify-seq
  "Given a seq of elts (maps) that have a unique key under id-key (for
  example, :id or :photo/id), build a map of {id -> elt}"
  [coll & {:keys [id-key] :or {id-key :id}}]
  (->> coll
       (map (juxt id-key identity))
       (into {})))


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

(defn i
  "{debug} Inspect DB."
  [& ks]
  (let [db @re-frame.db/app-db]
    (cond
      (nil? ks) (sort (keys db))
      :t (get-in db ks))))

(defn join-strings
  "Join nonempty non-nil strings with separator."
  ([coll]
   (join-strings ", " coll))
  ([sep coll]
   (apply str (interpose sep (filter identity coll)))))

(defn truncate-time-component
  "Given an ISO datetime of string format, truncate possible time away."
  [ts]
  (clojure.string/replace ts #"T.*$" ""))

(defn toggle-set-membership
  [x s]
  (if (s x)
    (clojure.set/difference s #{x})
    (clojure.set/union      s #{x})))


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
