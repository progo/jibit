(ns clurator.thumbnails
  "We're making thumbnails here."
  (:require [clurator.file-utils :as futils]
            [clurator.exiftool :as exiftool]
            clurator.settings))

(defn select-largest-file
  [dir]
  (let [files (fs/list-dir dir)]
    (apply max-key (memfn length) files)))

(defn make-thumbnail-from-bmp
  "Invoke Imagemagick convert to make a converted JPEG thumbnail from
  file `f'. Store it under thumbnails directory using `thumb-name'."
  [f thumb-name]
  (let [target-name (str clurator.settings/thumbnail-dir "/" thumb-name ".jpeg")
        tsize clurator.settings/thumbnail-size]
    (fs/exec "convert" (str f) "-resize" (str tsize "x" tsize) target-name)
    target-name))

(defn extract-raw-preview
  "Extract JPEG preview from a raw file using exiftool invocation. Slow
  and bitter.

  Returns a j.io.File instance of the most suitable candidate in JPEG.

  Caution: working-dir must be clean and must be cleaned after use."
  [f working-dir]
  (when-not (fs/list-dir working-dir)
    (prn "Extracting JPEG previews from a raw file")
    (exiftool/extract-preview-files f working-dir)
    (select-largest-file working-dir)))

(defn create-thumbnail!
  "Create a thumbnail from file 'f'. If file is a raw file we'll
  extract a preview photo from its metadata.

  Thumbnail file will be stored under configured thumbnail dir and
  it'll be named using the `thumb-name' argument.

  Thumbnail size is controlled by settings.
  "
  [f uuid]
  (cond
    (futils/bitmap-file? f) (make-thumbnail-from-bmp
                             f
                             uuid)
    (futils/raw-file? f) (when-let [tempdir (fs/temp-dir "jibit-")]
                           (try
                             (make-thumbnail-from-bmp
                              (extract-raw-preview f tempdir)
                              uuid)
                             (finally
                               (fs/delete-dir tempdir))))
    :t nil))
