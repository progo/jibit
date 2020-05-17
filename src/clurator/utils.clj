(ns clurator.utils)

(defn keywordify-map
  "Map `m` that has string keys, make them keyworded. Optional `ns`
  makes them qualified."
  ([m ns]
   (into {} (map (fn [[k v]] [(keyword (name ns) (name k)) v]) m)))
  ([m]
   (keywordify-map m nil)))
