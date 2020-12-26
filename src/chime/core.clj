(ns chime.core
  "Lightweight scheduling library."
  (:require [clojure.tools.logging :as log])
  (:import (clojure.lang IDeref IBlockingDeref IPending)
           (java.time ZonedDateTime Instant Clock)
           (java.time.temporal ChronoUnit TemporalAmount)
           (java.util Date)
           (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)
           (java.lang AutoCloseable Thread$UncaughtExceptionHandler)))

;; --------------------------------------------------------------------- time helpers
(defonce utc-clock (Clock/systemUTC))

(def ^:dynamic *clock*
  "The clock used to determine 'now'; you can override it with `binding` for
  testing purposes."
  utc-clock)

(defn now
  "Returns a date time for the current instant."
  (^java.time.Instant []
   (now *clock*))
  (^java.time.Instant [^Clock clock]
   (Instant/now clock)))

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

(def ^:private default-thread-factory
  (let [!count (atom 0)]
    (reify ThreadFactory
      (newThread [_ r]
        (doto (Thread. r)
          (.setName (format "chime-%d" (swap! !count inc))))))))

(defn- default-error-handler [e]
  (log/warn e "Error running scheduled fn")
  (not (instance? InterruptedException e)))

(defn chime-at
  "Calls `f` with the current time at every time in the `times` sequence.

  ```
  (:require [chime.core :as chime])
  (:import [java.time Instant])

  (let [now (Instant/now)]
    (chime/chime-at [(.plusSeconds now 2)
                     (.plusSeconds now 4)]
                    (fn [time]
                      (println \"Chiming at\" time)))
  ```

  Returns an AutoCloseable that you can `.close` to stop the schedule.
  You can also deref the return value to wait for the schedule to finish.

  Providing a custom `thread-factory` is supported, but optional (see `chime.core/default-thread-factory`).
  Providing a custom `clock` is supported, but optional (see `chime.core/utc-clock`).

  When the schedule is either cancelled or finished, will call the `on-finished` handler.

  You can pass an error-handler to `chime-at` - a function that takes the exception as an argument.
  Return truthy from this function to continue the schedule, falsy to cancel it.
  By default, Chime will log the error and continue the schedule.
  "
  (^java.lang.AutoCloseable [times f] (chime-at times f {}))

  (^AutoCloseable [times f {:keys [error-handler on-finished thread-factory clock]
                            :or {error-handler default-error-handler
                                 thread-factory default-thread-factory
                                 clock *clock*}}]
   (let [pool (Executors/newSingleThreadScheduledExecutor thread-factory)
         !latch (promise)]
     (letfn [(close []
               (.shutdownNow pool)
               (when (and (deliver !latch nil) on-finished)
                 (on-finished)))

             (schedule-loop [[time & times]]
               (letfn [(task []
                         (if (try
                               (f time)
                               true
                               (catch Exception e
                                 (try
                                   (error-handler e)
                                   (catch Exception e
                                     (log/error e "error calling chime error-handler, stopping schedule")))))

                           (schedule-loop times)
                           (close)))]

                 (if time
                   (.schedule pool ^Runnable task (.between ChronoUnit/MILLIS (now clock) time) TimeUnit/MILLISECONDS)
                   (close))))]

       (schedule-loop (map ->instant times))

       (reify
         AutoCloseable
         (close [_] (close))

         IDeref
         (deref [_] (deref !latch))

         IBlockingDeref
         (deref [_ ms timeout-val] (deref !latch ms timeout-val))

         IPending
         (isRealized [_] (realized? !latch)))))))

(defn periodic-seq [^Instant start ^TemporalAmount duration-or-period]
  (iterate #(.addTo duration-or-period ^Instant %) start))

(defn without-past-times
  ([times] (without-past-times times (now)))

  ([times now]
   (->> times
        (drop-while #(.isBefore ^Instant (->instant %) now)))))
