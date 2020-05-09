(ns clurator.exif
  "Exif analysis and related tools."
  (:require [java-time :as time]
            clurator.utils)
  (:import [com.thebuzzmedia.exiftool ExifToolBuilder Tag]
           [com.thebuzzmedia.exiftool.core StandardTag]))

;; This thing is also supposed to close the process after 10 minutes
;; of inactivity. Currently I'm seeing 48-hour-old processes in my ps...
(def exiftool
  (-> (com.thebuzzmedia.exiftool.ExifToolBuilder.)
      (.withPoolSize 1)
      (.enableStayOpen)
      (.build)))


(defn read-tag [^Tag tag]
  (.getDisplayName tag))

(defn tag-map->clj [m]
  (->> m
       (map #(vector (read-tag (.getKey %))
                     (.getValue %)))
       (into {})))

(defn get-all-exif-data [image-file]
  (tag-map->clj (.getImageMeta exiftool image-file)))

(defn get-selected-exif-data*
  "Faster way that use tag enums from exiftool"
  [image-file fields]
  (tag-map->clj (.getImageMeta exiftool image-file fields)))

(defn get-selected-exif-data
  "Pick selected tags by DisplayName. We get all, and the filter out
  the clutter. So this is a slow approach."
  [image-file fields]
  (select-keys (get-all-exif-data image-file)
               fields))

;; Phil Harvey's exiftool collects and calculates many interesting
;; values for us. FOV, Focus distance, Light value. Many of the
;; cameras provide this shit and we do well to collect that for perusal.

;; Let's collect all interesting keys in one.
;; Thanks to Exiftool, some of the keys are shared and calculated.

;; LensInfo : Leica, Fuji X, Oly offers that info, but differs.
;; Zoom lens of `A--B mm f/X-Y` has info "A B X Y" in Olympus.
;; Prime lens of `A f/X` has info "A A X X" in Fuji and Q.
;; Prime lens of `A f/X-Y` has info "A A X Y" in Leica M.
;; Canon doesn't do that field.


(def exif-keys
  #{"Make"
    "Model"
    "ISO"
    "ImageWidth"
    "ImageHeight"
    "Megapixels"
    "LensInfo"
    "LensMake"
    "LensModel"
    "Aperture"
    "ExposureCompensation"
    "ExposureTime"
    "CreateDate"
    "FocalLength"
    "FocalLength35efl"
    "MinFocalLength"
    "MaxFocalLength"
    "LightValue"
    "Rating"
    })

(def some-common-exif-keys
  #{;; Static Camera properties
    "CircleOfConfusion"                 ; 5D, Q, Pok, EPL
    "Make"                              ; 5D, M, Q, Pok, EPL
    "Model"                             ; 5D, M, Q, Pok, EPL
    "UniqueCameraModel"                 ; M, Q
    ;; Image properties
    "ISO"                               ; 5D, M, Q, Pok, EPL
    "FNumber"  ,,,                      ; 5D, Q, EPL
    "Aperture"                          ; 5D, M, Q, Pok, EPL
    "ExposureCompensation"              ; 5D, M, Q, EPL
    "ShutterSpeed"  ,,,                 ; 5D, Q
    "ExposureTime"                      ; 5D, M, Q, Pok, EPL
    "CreateDate"                        ; 5D, M, Q, Pok, EPL
    "FocalLength"                       ; 5D, M, Q, Pok, EPL
    "FocalLength35efl"                  ; 5D, M, Q, Pok, EPL
    "DOF"                               ; 5D
    "FOV"                               ; 5D, Q, Pok, EPL
    "MeasuredEV"                        ; 5D
    "MeasuredEV2"                       ; 5D
    "LightValue"                        ; 5D, M, Q, Pok, EPL
    "Rating"                            ; Q
    ;; Static Lens properties
    "MinFocalLength"                    ; 5D, EPL
    "MaxFocalLength"                    ; 5D, EPL
    "LensMake"                          ; M
    "LensModel"                         ; 5D, M, Q, EPL
    "LensInfo"                          ; M, Q*, EPL
    "MinAperture"                       ; 5D, Pok
    "MaxAperture"                       ; 5D
    "MaxApertureValue"                  ; M, Q
    })


(defn str->int
  [s]
  (if s
    (int (Double. s))
    nil))

(defn str->double
  [s]
  (if s
    (Double. s)
    nil))

(defn str->datetime
  [s]
  (time/local-date-time "yyyy:MM:dd HH:mm:ss" s))

(defn get-exif-parsed
  "Get a collection of exif tags parsed into appropriate types. Map of
  keywords into strings or numbers."
  [f]
  (let [e (get-selected-exif-data f exif-keys)]
    (-> e
        (update "ISO" str->int)
        (update "LightValue" str->double)
        (update "ExposureCompensation" str->double)
        (update "FocalLength35efl" str->double)
        (update "FocalLength" str->double)
        (update "Rating" str->int)
        (update "Aperture" str->double)
        (update "ExposureTime" str->double)
        (update "CreateDate" str->datetime)
        (update "ImageWidth" str->int)
        (update "ImageHeight" str->int)
        (update "Megapixels" str->double)
        (clurator.utils/keywordify-map :exif))))
