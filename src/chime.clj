(ns chime
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.core.async :as a :refer [<! >! go-loop]]
            [clojure.core.async.impl.protocols :as p]))

(defn- ms-between [start end]
  (if (t/before? end start)
    0
    (-> (t/interval start end) (t/in-millis))))

(defn chime-ch
  "Returns a core.async channel that 'chimes' at every time in the
  times list. Times that have already passed are ignored.

  Arguments:
    times - (required) Sequence of java.util.Dates, org.joda.time.DateTimes
                       or msecs since epoch

    ch    - (optional) Channel to chime on - defaults to a new unbuffered channel
                       Closing this channel stops the schedule.

  Usage:

    (let [chimes (chime-ch [(-> 2 t/seconds t/ago) ; has already passed, will be ignored.
                            (-> 2 t/seconds t/from-now)
                            (-> 3 t/seconds t/from-now)])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (prn \"Chiming at:\" msg)
                 (recur)))))

  There are extensive usage examples in the README"
  [times & [{:keys [ch] :or {ch (a/chan)}}]]

  (let [cancel-ch (a/chan)
        times-fn (^:once fn* [] times)]
    (go-loop [now (t/now)
              [next-time & more-times] (->> (times-fn)
                                            (map tc/to-date-time)
                                            (drop-while #(t/before? % now)))]
      (a/alt!
        cancel-ch (a/close! ch)

        (a/timeout (ms-between now next-time)) (do
                                                 (>! ch next-time)

                                                 (if (seq more-times)
                                                   (recur (t/now) more-times)
                                                   (a/close! ch)))

        :priority true))

    (reify
      p/ReadPort
      (take! [_ handler]
        (p/take! ch handler))

      p/Channel
      (close! [_] (p/close! cancel-ch)))))

(defn chime-at [times f & [{:keys [error-handler on-finished]
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

;; ---------- TESTS ----------

(comment
  ;; some quick tests ;)

  (chime-at [(-> 2 t/seconds t/ago)
             (-> 2 t/seconds t/from-now)
             (-> 3 t/seconds t/from-now)
             (-> 5 t/seconds t/from-now)]
            #(println "Chiming!" %))

  (let [chimes (chime-ch [(-> 2 t/seconds t/ago)
                          (-> 2 t/seconds t/from-now)
                          (-> 3 t/seconds t/from-now)])]
    (a/<!! (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Chiming at:" msg)
               (recur)))))

  (let [chimes (chime-ch [(-> 2 t/seconds t/ago)
                          (-> 2 t/seconds t/from-now)
                          (-> 3 t/seconds t/from-now)])]

    (a/<!!
     (a/go
       (prn (<! chimes))
       (a/close! chimes)
       (prn (<! chimes)))))

  (chime-at [(-> 2 t/seconds t/from-now) (-> 4 t/seconds t/from-now)]

            (fn [time]
              (println "Chiming at" time))

            {:on-finished (fn []
                            (println "Schedule finished."))}))

(comment
  ;; test case for 0.1.5 bugfix - thanks Nick!
  (require '[clj-time.periodic :refer [periodic-seq]])

  (let [ch (chime-ch (->> (periodic-seq (-> (-> (t/now) (t/plus (t/seconds 1)))
                                            (.withMillisOfSecond 0))
                                        (-> 1 t/seconds))
                        (take 3)))]

    (println (a/<!! ch))
    (println ";; pause")
    (a/<!! (a/timeout 3000))
    ;; Pending timestamps come through in the past.
    (println (a/<!! ch))
    (println (a/<!! ch))))

(comment
  ;; code from #16 - thanks Dave!

  (defn do-stuff [now]
    (println "starting" now)
    (Thread/sleep 3000) ;; some overrunning task
    (println "done" now))

  (require '[clj-time.periodic :refer [periodic-seq]])

  (def cancel-stuff!
    (chime-at (rest (periodic-seq (t/now) (t/seconds 2))) do-stuff))

  (cancel-stuff!)

  )
