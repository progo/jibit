(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST OPTIONS DELETE]]
            compojure.route
            [taoensso.timbre :as timbre :refer [debug spy]]
            clurator.view.tag
            clurator.view.photo
            clurator.settings))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

(def edn-headers
  {"Content-Type" "application/edn"
   "Access-Control-Allow-Headers" "Content-Type"
   "Access-Control-Allow-Methods" "*"
   "Access-Control-Allow-Origin" "*"})

(defn make-req-handler
  "Make a handler function that takes in a route request, patches it
  through provided `handler-fn` (:: request ->
  {:status [:ok|:fail], :resp value}) and then makes a suitable
  response in EDN."
  [handler-fn]
  (fn [req]
    (let [{status :status response :resp} (handler-fn req)
          status-code (case status
                        :ok 200
                        :fail 500
                        200)]
      {:status status-code
       :headers edn-headers
       :body (prn-str response)})))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1><p>Soon serving jibit here...</p>"})

(def list-photos
  (make-req-handler clurator.view.photo/list-photos))

(def list-tags
  (make-req-handler clurator.view.tag/list-tags))

(def tag-photos
  (make-req-handler clurator.view.tag/tag-photos))

(def create-update-new-tag
  (make-req-handler clurator.view.tag/create-update-new-tag))

(def delete-tag
  (make-req-handler clurator.view.tag/delete-tag))

(defn photo-thumbnail [uuid]
  (java.io.File. (str clurator.settings/thumbnail-dir "/" uuid ".jpeg")))

(comp/defroutes app
  (GET "/" [] index)
  (POST    "/tag-photo" [] tag-photos)
  (OPTIONS "/tag-photo" [] {:status 200 :headers edn-headers})
  (POST    "/tag" [] create-update-new-tag)
  (OPTIONS "/tag" [] {:status 200 :headers edn-headers})
  (DELETE  "/tag" [] delete-tag)
  (GET "/tags" [] list-tags)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:uuid" [uuid] (photo-thumbnail uuid))
  (compojure.route/not-found "not found"))

(defn -main [& args]
  (let [port 8088]
    (timbre/info "Running server at port" port)
    (run-server #'app {:port port})))
