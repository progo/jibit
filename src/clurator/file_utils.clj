(ns clurator.file-utils
  (:require [me.raynes.fs :as fs]
            [java-time :as time]
            clurator.settings))

(defn file-extension-in-set?
  [path ext-set]
  (and
   (fs/file? path)
   (ext-set (.toLowerCase (fs/extension path)))))

(defn raw-file?
  [path]
  (file-extension-in-set? path clurator.settings/image-raw-extensions))

(defn bitmap-file?
  [path]
  (file-extension-in-set? path clurator.settings/image-bitmap-extensions))

(defn eligible-file?
  "The types of files we (try to) support."
  [path]
  (or (raw-file? path)
      (bitmap-file? path)))

(defn move-under-directory*
  "Move file or dir 'f' under target directory 'target'. On name
  conflict, rename using current datetime stamp."
  [f target]
  (let [filename (fs/base-name f)
        full-target (str target "/" filename)
        conflict? (fs/exists? full-target)
        timestamp (->> (time/local-date-time)
                       (time/format ".yyyy-MM-dd--HH-mm-ss-SSS"))]
    (cond
      conflict?
      (fs/move f (str full-target timestamp))

      :else
      (fs/move f full-target)
    )))
