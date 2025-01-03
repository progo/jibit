(ns jibit.uri)

;; Remember to specify protocol. And no trailing slash.
(def server-uri "http://localhost:8088")
;; (def server-uri "")

(defn photo-image-uri
  [photo]
  (str server-uri "/photo/" (:uuid photo)))

(defn photo-thumbnail-uri
  [photo]
  (str server-uri "/thumbnail/" (:uuid photo)))

