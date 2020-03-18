(ns clurator.thumbnails
  "We're making thumbnails here."
  (:require [me.raynes.fs :as fs]
            clurator.settings))

(defn create-thumbnail!
  "Create a thumbnail from file 'f'. If file is a raw file we'll
  extract a preview photo from its metadata.

  Thumbnail file will be stored under configured thumbnail dir and
  it'll be named using the `thumb-name' argument.

  Thumbnail size is controlled by settings.
  "
  [f thumb-name]
  )
