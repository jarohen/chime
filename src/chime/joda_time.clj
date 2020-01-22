(ns chime.joda-time
  (:require [chime :as c]
            [clj-time.core :as t])
  (:import [java.time Instant]
           [org.joda.time ReadableInstant]))

(extend-protocol c/->Instant
  ReadableInstant
  (->instant [jt-instant]
    (Instant/ofEpochMilli (.getMillis jt-instant))))
