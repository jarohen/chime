(ns chime.joda-time
  (:require [chime.core :as chime])
  (:import [java.time Instant]))

(comment
  ;; use this when dealing with `JodaTime`
  (extend-protocol chime/->Instant
    org.joda.time.ReadableInstant
    (->instant [jt-instant]
      (Instant/ofEpochMilli (.getMillis jt-instant))))

  )

