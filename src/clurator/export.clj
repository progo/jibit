(ns clurator.export
  (:require [taoensso.timbre :as timbre :refer [debug spy]]
            [me.raynes.fs :as fs]
            [java-time :as time]
            clurator.utils
            common.settings
            clurator.settings))

(defn format-output-file-name
  "Name the file by its taken timestamp, if present. Alternative is to
  take its processing ts which is our best bet at guessing when the
  file was last modified or processed."
  [photo]
  (let [taken_ts (:taken_ts photo)
        processed_ts (:process_ts photo)
        ts (time/local-date-time (or taken_ts processed_ts))]
    (str
     (time/format "yyyy-MM-dd (ccc) HH-mm-ss"
                  (time/local-date-time ts))
     (if-not taken_ts "(proctime)" ""))))

(defn int->ssstring
  "Convert integer into a spreadsheet style column name."
  [num]
  (loop [n num, result ""]
    (if (pos? n)
      (let [n' (quot (dec n) 26)
            r  (rem  (dec n) 26)]
        (recur n'
               (str (char (+ 97 r)) result)))
      result)))

(defn find-first-nonconflicting-name
  "Find if `fixed.ext` is available and if not, stuff letters in between
  until the name is available."
  [base ext]
  (loop [base base, stuffings (map int->ssstring (range))]
    (if-not (fs/exists? (str base (first stuffings) ext))
      ;; Found an available path
      (str base (first stuffings) ext)

      (recur base (rest stuffings))
      )))

(defn export-resize-photos
  "Export selected photos as JPEGs, resize to certain size limit. With
  raw files we probably want to either run dcraw or extract the
  preview files if possible. Or just ignore for now. "
  [scheme-key photos target-dir]
  (fs/mkdir target-dir)
  (doseq [photo photos]
    (let [export-scheme (get common.settings/export-schemes scheme-key)
          export-method (:method export-scheme)
          source (str clurator.settings/storage-directory "/"
                      (:storage_filename photo))
          target (find-first-nonconflicting-name
                  (str target-dir "/" (format-output-file-name photo))
                  ;; If we just copy, we want to keep the orig extension
                  (if (= export-method :passthrough)
                    (fs/extension source)
                    ".jpeg"))]
      (case export-method
        :convert
        (do
          (debug "Converting" source "=>" target)
          (apply fs/exec
                 (concat ["convert" source]
                         (:args export-scheme)
                         [target])))

        :passthrough
        (do
          (debug "Passthrough (copy as-is)" source "=>" target)
          (fs/copy source target)))))

  ;; We could be deleting the empty dir if there are problems with
  ;; input data. But might be as convenient to leave it be.
  ;; (fs/delete target-dir)
  )
