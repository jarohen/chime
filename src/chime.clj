(ns chime
  "Lightweight scheduling library."
  (:require
    [clojure.core.async :as a :refer [<! >! go-loop]]
    [clojure.core.async.impl.protocols :as p])
  (:import (java.time ZonedDateTime Instant)
           (java.time.temporal ChronoUnit)
           (java.util Date)))

;; --------------------------------------------------------------------- time helpers

(def ^:dynamic *clock*
  "The clock used to determine 'now'; you can override it with `binding` for
  testing purposes."
  (java.time.Clock/systemUTC))

(defn- now
  "Returns a date time for the current instant"
  []
  (Instant/now *clock*))

(defprotocol ->Instant
  (^Instant
    ->instant [obj] "Convert `obj` to an Instant instance."))

(extend-protocol ->Instant
  ;; NOTE: Only implemented for the few types supported by Chime
  Date
  (->instant [^Date date]
    (.toInstant date))

  Instant
  (->instant [inst] inst)

  Long
  (->instant [epoch-msecs]
    (Instant/ofEpochMilli epoch-msecs))

  ZonedDateTime
  (->instant [zdt]
    (.toInstant zdt)))

(defn- before? [^Instant t1 ^Instant t2]
  (.isBefore t1 t2))

;; ---------------------------------------------------------------------

(defn- ms-between [^Instant start ^Instant end]
  (if (before? end start)
    0
    (.between ChronoUnit/MILLIS start end)))

(defn chime-ch
  "Returns a core.async channel that 'chimes' at every time in the
  times list. Times that have already passed are ignored.

  Arguments:
    times - (required) Sequence of java.util.Dates, java.time.Instant,
                       java.time.ZonedDateTime or msecs since epoch

    ch    - (optional) Channel to chime on - defaults to a new unbuffered channel
                       Closing this channel stops the schedule.

  Usage:

    (let [chimes (chime-ch [(.plusSeconds (Instant/now) -2) ; has already passed, will be ignored.
                            (.plusSeconds (Instant/now) 2)
                            (.plusSeconds (Instant/now) 2)])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (prn \"Chiming at:\" msg)
                 (recur)))))

  There are extensive usage examples in the README"
  [times & [{:keys [ch] :or {ch (a/chan)}}]]

  (let [cancel-ch (a/chan)
        times-fn (^:once fn* [] times)]
    (go-loop [now (now)
              times-seq (->> (times-fn)
                             (map ->instant)
                             (drop-while #(before? % now)))]
      (if-let [[next-time & more-times] (seq times-seq)]
        (a/alt!
          cancel-ch (a/close! ch)

          (a/timeout (ms-between now next-time)) (do
                                                   (>! ch next-time)

                                                   (recur (chime/now) more-times))

          :priority true)

        (a/close! ch)))

    (reify
      p/ReadPort
      (take! [_ handler]
        (p/take! ch handler))

      p/Channel
      (close! [_] (p/close! cancel-ch))
      (closed? [_] (p/closed? cancel-ch)))))

(defn chime-at
  "Calls `f` with the current time at every time in the `times` list."
  [times f & [{:keys [error-handler on-finished]
               :or {on-finished #()}}]]
  (let [ch (chime-ch times)
        !cancelled? (atom false)]
    (go-loop []
      (if-let [time (<! ch)]
        (do
          (<! (a/thread
                (try
                  (when-not @!cancelled?
                    (f time))
                  (catch Exception e
                    (if error-handler
                      (error-handler e)
                      (throw e))))))
          (recur))

        (on-finished)))

    (fn cancel! []
      (a/close! ch)
      (reset! !cancelled? true))))
