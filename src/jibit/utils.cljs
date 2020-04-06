(ns jibit.utils)

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
