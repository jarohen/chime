(ns chime.core
  "Lightweight scheduling library."
  (:require [clojure.tools.logging :as log])
  (:import (clojure.lang IDeref IBlockingDeref)
           (java.time ZonedDateTime Instant)
           (java.time.temporal ChronoUnit TemporalAmount)
           (java.util Date)
           (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)
           (java.lang AutoCloseable)))

;; --------------------------------------------------------------------- time helpers

(def ^:dynamic *clock*
  "The clock used to determine 'now'; you can override it with `binding` for
  testing purposes."
  (java.time.Clock/systemUTC))

(defn- now
  "Returns a date time for the current instant"
  ^java.time.Instant []
  (Instant/now *clock*))

(defprotocol ->Instant
  (->instant ^java.time.Instant [obj]
    "Convert `obj` to an Instant instance."))

(extend-protocol ->Instant
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

(def ^:private thread-factory
  (let [!count (atom 0)]
    (reify ThreadFactory
      (newThread [_ r]
        (doto (Thread. r)
          (.setName (format "chime-" (swap! !count inc))))))))

(defn chime-at
  "Calls `f` with the current time at every time in the `times` list."
  (^java.lang.AutoCloseable [times f] (chime-at times f {}))

  (^java.lang.AutoCloseable [times f {:keys [error-handler on-finished]}]
   (let [pool (Executors/newSingleThreadScheduledExecutor thread-factory)
         !latch (promise)]
     (letfn [(close []
               (.shutdownNow pool)
               (deliver !latch nil)
               (when on-finished
                 (on-finished)))

             (schedule-loop [[time & times]]
               (if time
                 (.schedule pool
                            ^Runnable
                            (fn []
                              (if (try
                                    (f time)
                                    true
                                    (catch Exception e
                                      (if error-handler
                                        (error-handler e)
                                        (log/warn e "Error running scheduled fn"))

                                      (not (instance? InterruptedException e))))

                                (schedule-loop times)
                                (close)))

                            (.between ChronoUnit/MILLIS (now) time)
                            TimeUnit/MILLISECONDS)

                 (close)))]

       (schedule-loop (map ->instant times))

       (reify
         AutoCloseable
         (close [_] (close))

         IDeref
         (deref [_] (deref !latch))

         IBlockingDeref
         (deref [_ ms timeout-val] (deref !latch ms timeout-val)))))))

(defn periodic-seq [^Instant start ^TemporalAmount duration-or-period]
  (iterate #(.addTo duration-or-period ^Instant %) start))

(defn without-past-times
  ([times] (without-past-times times (Instant/now)))

  ([times now]
   (->> times
        (drop-while #(.isBefore ^Instant % (->instant now))))))
