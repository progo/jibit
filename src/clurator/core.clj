(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST]]
            [compojure.route :as route]
            ))

;;
;; We will serve jibit here, and provide an API, with websockets probably.

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1>"})

(comp/defroutes app
  (GET "/" [] index)
  (route/not-found "not found"))

(defn -main [& args]
  (run-server #'app {:port 8088}))
