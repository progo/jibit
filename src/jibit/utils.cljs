(ns jibit.utils
  (:require
   [cljs.pprint :refer [cl-format]]
   ))


(defn rgb->rgba
  "Given hex string #RRGGBB turn it into #RRGGBBAA. Opacity is a decimal
  between [0, 1]"
  [rgb opacity]
  (let [op (int (* 255 opacity))]
    (cl-format nil "~a~2'0x" rgb op)))

(defn get-default-export-scheme-key
  []
  (-> common.settings/export-schemes first first))

;; dissoc-in, inspired by whynotsoftware blog post but recreated
;; because of lack of license.

(defn dissoc-in
  "Dissociate from nested maplike `m` using keys `ks`."
  [m [k & ks]]
  (cond
    ;; empty assoc
    (nil? k) m

    ;; just one key left
    (nil? ks) (dissoc m k)

    ;; several keys
    :else (assoc m k (dissoc-in (get m k) ks))))
