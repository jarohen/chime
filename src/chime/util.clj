(ns chime.util
  (:require [clj-time.core :as t]))

(defn merge-schedules [left right]
  (lazy-seq
   (case [(boolean (seq left)) (boolean (seq right))]
       [false false] []
       [false true] right
       [true false] left
       [true true] (let [[l & lmore] left
                         [r & rmore] right]
                     (if (t/before? l r)
                       (cons l (merge-schedules lmore right))
                       (cons r (merge-schedules left rmore)))))))
