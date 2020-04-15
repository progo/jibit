(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST OPTIONS ANY]]
            [ring.util.codec :refer [form-decode]]
            compojure.route
            [taoensso.timbre :as timbre :refer [debug spy]]
            [clurator.model.photo :as model.photo]
            [clurator.model.tag :as model.tag]
            [clurator.view.filtering :as view.filtering]
            clurator.settings))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

(def edn-headers
  {"Content-Type" "application/edn"
   "Access-Control-Allow-Headers" "Content-Type"
   "Access-Control-Allow-Origin" "*"})

(defn index [req]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "<h1>Hello, world!</h1><p>Soon serving jibit here...</p>"})

(defn list-photos [req]
  {:status 200
   :headers edn-headers
   :body (prn-str
          (model.photo/filter-photos
           (view.filtering/handle-filter-criteria
            (form-decode (:query-string req)))))})

(defn list-tags [req]
  {:status 200
   :headers edn-headers
   :body (prn-str (model.tag/query-tags))})

(defn tag-photos [req]
  {:status 200
   :headers edn-headers
   :body (prn-str
          (let [{tag-id :tag
                 photo-ids :photos} (view.filtering/read-edn req)]
            (model.tag/set-tag-for-photos tag-id photo-ids :toggle)))})

(defn create-update-new-tag [req]
  (let [resp (let [{use-color? :tag-color? :as tag} (view.filtering/read-edn req)
                   tag (if use-color?
                         tag
                         (dissoc tag :tag/style_color))]
               (if (model.tag/create-edit-tag tag)
                 :ok
                 :fail))
        status-code (case resp
                      :ok 200
                      :fail 500)]
    {:status status-code
     :headers edn-headers
     :body (prn-str resp)}))

(defn photo-thumbnail [uuid]
  (java.io.File. (str clurator.settings/thumbnail-dir "/" uuid ".jpeg")))

(comp/defroutes app
  (GET "/" [] index)
  (POST    "/tag-photo" [] tag-photos)
  (OPTIONS "/tag-photo" [] {:status 200 :headers edn-headers})
  (POST    "/tag" [] create-update-new-tag)
  (OPTIONS "/tag" [] {:status 200 :headers edn-headers})
  (GET "/tags" [] list-tags)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:uuid" [uuid] (photo-thumbnail uuid))
  (compojure.route/not-found "not found"))

(defn -main [& args]
  (let [port 8088]
    (timbre/info "Running server at port" port)
    (run-server #'app {:port port})))
