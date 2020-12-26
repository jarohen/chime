(ns chime.util
  (:require [chime.core :as c]))

(defn merge-schedules [left right]
  (lazy-seq
   (case [(boolean (seq left)) (boolean (seq right))]
       [false false] []
       [false true] right
       [true false] left
       [true true] (let [[l & lmore] left
                         [r & rmore] right]
                     (if (.isBefore (c/to-instant l) (c/to-instant r))
                       (cons l (merge-schedules lmore right))
                       (cons r (merge-schedules left rmore)))))))
