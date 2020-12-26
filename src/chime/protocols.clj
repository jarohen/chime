(ns chime.protocols
  (:import (java.util Date)
           (java.time Instant ZonedDateTime)))

(defprotocol ->Instant
  (->instant [obj] "Convert `obj` to an Instant instance."))

(extend-protocol ->Instant
  Date
  (->instant [date]
    (.toInstant date))

  Instant
  (->instant [inst] inst)

  Long
  (->instant [epoch-msecs]
    (Instant/ofEpochMilli epoch-msecs))

  ZonedDateTime
  (->instant [zdt]
    (.toInstant zdt)))
