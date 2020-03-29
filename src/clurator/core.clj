(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST]]
            [ring.util.codec :refer [form-decode]]
            compojure.route
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.db :as db]
            [honeysql.core :as sql]
            clurator.settings))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

(defn handle-filter-criteria
  [criteria]
  ;; taken-begin  date
  ;; taken-end    date
  ;; camera-make  text
  ;; camera-model text
  ;; TODO handle empty strings at this point, nilify
  (merge {:order-by :%random}
         (if-let [x (criteria "camera-make")]
           {:camera-make x})
         (if-let [x (criteria "camera-model")]
           {:camera-model x})
         (if-let [tb (criteria "taken-begin")]
           {:taken-begin tb})
         (if-let [te (criteria "taken-end")]
           {:taken-end te})))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1><p>Soon serving jibit here...</p>"})

(defn list-photos [req]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (prn-str
          (db/filter-photos (handle-filter-criteria
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
