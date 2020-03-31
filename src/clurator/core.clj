(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST]]
            [ring.util.codec :refer [form-decode]]
            compojure.route
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.model.photo :as model.photo]
            clurator.settings))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

(defn nilify
  "Turn empty strings into nils"
  [x]
  (if (empty? x) nil x))

(defmacro clean-input
  "Check if `kw' as a string is found in a scoped map called `criteria'
  and if so, produce a map of {kw (criteria (name kw))} for merge.

  Use in `handle-filter-criteria' and not much elsewhere.

  Optionally supply a body of code that will deal with captured `input'.
  "
  ([kw]
   `(if-let [val# (nilify (get ~'criteria ~(name kw)))]
      {~kw val#}))
  ([kw & body]
   `(if-let [~'input (nilify (get ~'criteria ~(name kw)))]
      {~kw ~@body}
      )))

(defn handle-filter-criteria
  [criteria]
  ;; order-by
  ;; taken-begin  date
  ;; taken-end    date
  ;; camera-make  text
  ;; camera-model text
  (merge {}
         (clean-input :order-by
              (case input
                "random" :%random
                "taken" :taken_ts
                :taken_ts))
         (clean-input :camera-make)
         (clean-input :camera-model)
         (clean-input :taken-begin)
         (clean-input :taken-end)))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1><p>Soon serving jibit here...</p>"})

(defn list-photos [req]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (prn-str
          (model.photo/filter-photos (handle-filter-criteria
                                      (form-decode (:query-string req)))))})

(defn photo-thumbnail [uuid]
  (java.io.File. (str clurator.settings/thumbnail-dir "/" uuid ".jpeg")))

(comp/defroutes app
  (GET "/" [] index)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:uuid" [uuid] (photo-thumbnail uuid))
  (compojure.route/not-found "not found"))

(defn -main [& args]
  (let [port 8088]
    (timbre/info "Running server at port" port)
    (run-server #'app {:port port})))
