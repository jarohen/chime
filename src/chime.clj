(ns chime
  (:require [clj-time.core :as t])
  (:import [java.util.concurrent ScheduledExecutorService Executors TimeUnit]))

(defn ^:dynamic *now* []
  (t/now))

(defn- ms-until [time]
  (-> (t/interval (*now*) time) (t/in-msecs)))

(defprotocol TaskScheduler
  (schedule-at [scheduler time f])
  (shutdown [scheduler]))

(extend-protocol TaskScheduler
  ScheduledExecutorService
  (schedule-at [executor time f]
    (. executor (schedule f (ms-until time) TimeUnit/MILLISECONDS)))
  (shutdown [executor]
    (. executor (shutdown))))

(defn- new-scheduler []
  (Executors/newSingleThreadScheduledExecutor))

(defn- split-at-now [times]
  (split-with #(t/before? % (*now*)) times))

(defn- tick-handler [ag scheduler f]
  (fn handle-tick [times]
    (when (seq times)

      (let [[past-times future-times] (split-at-now times)]

        ;; run the fn for any times that have passed
        (doseq [time past-times]
          (f time))

        ;; reschedule this fn if we have more times
        (if (seq future-times)
          (schedule-at scheduler (first future-times) #(send-off ag handle-tick))
          (shutdown scheduler))

        ;; and keep the future times for the next invocation
        future-times))))

(defn chime-at [times f]
  (let [ag (agent times)]

    (send-off ag (tick-handler ag (new-scheduler) f))
    
    (fn cancel-schedule []
      (send-off ag (constantly [])))))


(chime-at [(-> 2 t/secs t/from-now) (-> 4 t/secs t/from-now) (-> 5 t/secs t/from-now) (-> 6 t/secs t/from-now)] #(println "Chiming!" %))
