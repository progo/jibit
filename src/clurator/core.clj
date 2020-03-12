(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST]]
            [compojure.route :as route]
            [clurator.db :as db]
            [honeysql.core :as sql]
            ))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1>"})

(defn list-photos [req]
  {:status 200
   :headers {"Content-Type" "application/edn"
             "Access-Control-Allow-Origin" "*"}
   :body (prn-str (db/query! (sql/build {:select :*
                                         :from :photo
                                         :order-by :%random
                                         :limit 10})))})

(comp/defroutes app
  (GET "/" [] index)
  (GET "/photos" [] list-photos)
  (route/not-found "not found"))

(defn -main [& args]
  (run-server #'app {:port 8088}))
