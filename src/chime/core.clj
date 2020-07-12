(ns chime.core
  "Lightweight scheduling library."
  (:require [clojure.tools.logging :as log])
  (:import (clojure.lang IDeref IBlockingDeref)
           (java.time ZonedDateTime Instant)
           (java.time.temporal ChronoUnit TemporalAmount)
           (java.util Date)
           (java.util.concurrent Executors ScheduledExecutorService ThreadFactory TimeUnit)
           (java.lang AutoCloseable Thread$UncaughtExceptionHandler)))

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

(defprotocol ChimeSchedule
  (remaining-chimes [chime-schedule]))

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

  When the schedule is either cancelled or finished, will call the `on-finished` handler.

  You can pass an exception-handler to `chime-at` - a function that takes the exception as an argument.
  Return truthy from this function to continue the schedule, falsy to cancel it.
  By default, Chime will log the exception and continue the schedule.
  "
  (^java.lang.AutoCloseable [times f] (chime-at times f {}))

  (^java.lang.AutoCloseable [times f {:keys [exception-handler on-finished] :as opts}]
   (let [pool (Executors/newSingleThreadScheduledExecutor thread-factory)
         !latch (promise)
         exception-handler (or exception-handler
                               (when-let [error-handler (:error-handler opts)]
                                 (defonce __error-handler-warn
                                   (log/warn "`:error-handler` is deprecated - rename to `:exception-handler`"))
                                 error-handler)
                               (fn [e]
                                 (let [interrupted? (instance? InterruptedException e)]
                                   (when-not interrupted?
                                     (log/warn e "Error running scheduled fn"))
                                   (not interrupted?))))
         !times (atom (map ->instant times))]
     (letfn [(close []
               (.shutdownNow pool)
               (deliver !latch nil)
               (when on-finished
                 (on-finished)))

             (schedule-loop []
               (let [time (first @!times)]
                 (letfn [(task []
                           (if (try
                                 (swap! !times rest)
                                 (f time)
                                 true
                                 (catch Exception e
                                   (try
                                     (exception-handler e)
                                     (catch Throwable e
                                       (log/error e "Error calling Chime exception-handler, stopping schedule"))))
                                 (catch Throwable t
                                   (log/error t (str (class t) " thrown, stopping schedule"))))

                             (schedule-loop)
                             (close)))]

                   (if time
                     (.schedule pool ^Runnable task (.between ChronoUnit/MILLIS (now) time) TimeUnit/MILLISECONDS)
                     (close)))))]

       (schedule-loop)

       (reify
         ChimeSchedule
         (remaining-chimes [_] @!times)

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
        (drop-while #(.isBefore ^Instant (->instant %) now)))))
