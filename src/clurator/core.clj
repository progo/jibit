(ns clurator.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :as comp :refer [GET POST OPTIONS DELETE]]
            compojure.route
            [taoensso.timbre :as timbre :refer [debug spy]]
            clurator.view.gear
            clurator.view.tag
            clurator.view.photo
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as string]
            clurator.settings)
  (:gen-class))

;; We will serve jibit here, and provide an API, with websockets
;; probably.

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

(comp/defroutes app
  (GET "/" [] index)
  (POST    "/tag-photo" [] tag-photos)
  (POST    "/tag" [] create-update-new-tag)
  (DELETE  "/tag" [] delete-tag)
  (POST "/gear" [] update-gear)
  (GET "/tags" [] list-tags)
  (GET "/gear" [] list-gear)
  (GET "/photos" [] list-photos)
  (GET "/thumbnail/:uuid" [uuid] (clurator.view.photo/serve-thumbnail-by-uuid uuid))
  (GET "/photo/:uuid" [uuid] (clurator.view.photo/serve-full-by-uuid uuid))
  (compojure.route/resources "/fonts" {:root "/public/fonts"})
  (compojure.route/not-found "not found"))

(def cli-options
  [["-v" "--verbose"]
   ["-h" "--help"]
   ["-p" "--port PORT" "Run the server on this port"
    :default 8088
    :parse-fn #(Integer/parseInt %)]])

(defn usage [summary]
  (->> ["JIBIT"
        ""
        "Options:"
        summary
        ""
        "Actions:"
        "  import [DIR [DIR...]]   Import photos from specified DIR(s)"
        (str "                          (default: " clurator.settings/inbox-path ")")
        "  server                  Launch the server to use Jibit in browser"]
       (string/join \newline)))

(defn parse-arguments
  [argv]
  (let [{:keys [options arguments errors summary]} (parse-opts argv cli-options)]
    (cond
      (:help options)
      {:exit-message (usage summary)}

      errors
      {:exit-message errors}

      (and (seq arguments)
           (#{"import" "server"} (first arguments)))
      {:action (first arguments)
       :args (rest arguments)
       :options options}

      :else
      {:exit-message (usage summary)})))

(defn -main [& argv]
  (let [{:keys [exit-message action args options]} (parse-arguments argv)]
    (cond
      exit-message
      (println exit-message)

      (= action "server")
      (let [port (:port options)]
        (when (seq args)
          (timbre/warn "Ignoring extraneous crap:" args))
        (timbre/info "Running server at port" port)
        (run-server #'app {:port port}))

      (= action "import")
      (doseq [dir (or (seq args) [clurator.settings/inbox-path])]
        (println (format "Importing from %s" dir))))))
