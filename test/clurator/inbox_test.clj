(ns clurator.inbox-test
  (:require [clojure.test :refer :all]
            [clurator.inbox :as inbox]))


;; `C-c C-t p` runs all tests found

(deftest test-subdirectory-under-inbox
  (let [sui inbox/subdirectory-under-inbox]
    (is (= (sui "/inbox/path/"
                "/inbox/path/fez/quux.jpeg")
           "fez"))

    (is (= (sui "/inbox/path"
                "/inbox/path/fez/quux.jpeg")
           "fez"))

    ;; with slash
    (is (= (sui "/inbox/path/"
                "/inbox/path/fez")
           "path"))

    ;; without slash
    (is (= (sui "/inbox/path"
                "/inbox/path/fez")
           "path"))
    ))
