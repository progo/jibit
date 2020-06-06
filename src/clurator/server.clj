(ns clurator.server
  "The HTTP server and API."
  (:require org.httpkit.server
            [compojure.core :as comp :refer [GET POST OPTIONS DELETE]]
            compojure.route

            clurator.view.gear
            clurator.view.tag
            clurator.view.photo
            clurator.settings

            [taoensso.timbre :as timbre :refer [debug spy]]))

(def edn-headers
  {"Content-Type" "application/edn"})

(defn make-req-handler
  "Make a handler function that takes in a route request, patches it
  through provided `handler-fn` (:: request ->
  {:status [:ok|:user|:fail], :resp value}) and then makes a suitable
  response in EDN."
  [handler-fn]
  (fn [req]
    (let [{status :status response :resp} (handler-fn req)
          status-code (case status
                        :ok 200
                        :user 200
                        :fail 500
                        200)
          response {:status (or status :ok)
                    :response response}]
      {:status status-code
       :headers edn-headers
       :body (prn-str response)})))

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1><p>Soon serving jibit here...</p>"})

(def list-photos
  (make-req-handler #'clurator.view.photo/list-photos))

(def list-tags
  (make-req-handler #'clurator.view.tag/list-tags))

(def tag-photos
  (make-req-handler #'clurator.view.tag/tag-photos))

(def create-update-new-tag
  (make-req-handler #'clurator.view.tag/create-update-new-tag))

(def delete-tag
  (make-req-handler #'clurator.view.tag/delete-tag))

(def list-gear
  (make-req-handler #'clurator.view.gear/list-gear))

(def update-gear
  (make-req-handler #'clurator.view.gear/update-gear))

(def sync-inbox
  (make-req-handler #'clurator.view.operations/sync-inbox))

(def upload-photos
  (make-req-handler #'clurator.view.photo/upload-photos))

(def export-photos
  (make-req-handler #'clurator.view.operations/export-photos))

(comp/defroutes app
  (GET "/" [] index)
  (POST "/upload" [] upload-photos)
  (POST "/export" [] export-photos)
  (POST    "/tag-photo" [] tag-photos)
  (POST    "/tag" [] create-update-new-tag)
  (DELETE  "/tag" [] delete-tag)
  (POST "/gear" [] update-gear)
  (POST "/inbox/sync" [] sync-inbox)
  (GET "/tags" [] list-tags)
  (GET "/gear" [] list-gear)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:uuid" [uuid] (clurator.view.photo/serve-thumbnail-by-uuid uuid))
  (GET "/photo/:uuid" [uuid] (clurator.view.photo/serve-full-by-uuid uuid))
  (compojure.route/resources "/fonts" {:root "/public/fonts"})
  (compojure.route/not-found "not found"))

(defn run-server
  [port]
  (org.httpkit.server/run-server #'app {:port port}))
