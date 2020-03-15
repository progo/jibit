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

(defn photo-thumbnail [filename]
  ;; TODO refactor settings
  (java.io.File. (str "/home/progo/pics/clurator/" filename)))

(comp/defroutes app
  (GET "/" [] index)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:filename" [filename] (photo-thumbnail filename))
  (route/not-found "not found"))

(defn -main [& args]
  (let [port 8088]
    (println "Running server at port" port)
    (run-server #'app {:port port})))
