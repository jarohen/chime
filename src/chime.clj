(ns chime
  (:require [clj-time.core :as t])
  (:import [java.util.concurrent ScheduledExecutorService Executors TimeUnit]))

(defn ^:dynamic *now* []
  (t/now))

(defn- ms-until [time]
  (let [now (*now*)]
    (if (t/after? time now)
      (-> (t/interval now time) (t/in-msecs))
      0)))

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

(defn- tick-handler [ag scheduler f error-handler]
  (fn handle-tick [times]
    (when (seq times)

      (let [[past-times future-times] (split-at-now times)]

        ;; run the fn for any times that have passed
        (doseq [time past-times]
          (try
            (f time)
            (catch Exception e
              (error-handler e))))

        ;; reschedule this fn if we have more times
        (if (seq future-times)
          (schedule-at scheduler (first future-times) #(send-off ag handle-tick))
          (shutdown scheduler))

        ;; and keep the future times for the next invocation
        future-times))))

(defn chime-at [times f & [{:keys [error-handler]
                            :or {error-handler #(.printStackTrace %)}}]]
  (let [future-times (drop-while #(t/before? % (*now*)) times)
        ag (agent future-times)]

    (send-off ag (tick-handler ag (new-scheduler) f error-handler))
    
    (fn cancel-schedule []
      (send-off ag (constantly [])))))


(comment
  (chime-at [(-> 2 t/secs t/ago)
             (-> 2 t/secs t/from-now)
             (-> 3 t/secs t/from-now)
             (-> 5 t/secs t/from-now)]
            #(println "Chiming!" %)))

(comment
  (chime-at [(-> 2 t/secs t/from-now)
             (-> 3 t/secs t/from-now)]
            #(throw (Exception. (format "Failing at time %s." %)))))
