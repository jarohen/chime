(ns chime.joda-time
  (:require [chime.core :as chime]
            [clj-time.core :as t])
  (:import [java.time Instant]
           [org.joda.time ReadableInstant]))

(extend-protocol chime/->Instant
  ReadableInstant
  (->instant [jt-instant]
    (Instant/ofEpochMilli (.getMillis jt-instant))))
