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
              times-seq (->> (times-fn)
                             (map tc/to-date-time)
                             (drop-while #(t/before? % now)))]
      (if-let [[next-time & more-times] (seq times-seq)]
        (a/alt!
          cancel-ch (a/close! ch)

          (a/timeout (ms-between now next-time)) (do
                                                   (>! ch next-time)

                                                   (recur (t/now) more-times))

          :priority true)

        (a/close! ch)))

    (reify
      p/ReadPort
      (take! [_ handler]
        (p/take! ch handler))

      p/Channel
      (close! [_] (p/close! cancel-ch))
      (closed? [_] (p/closed? cancel-ch)))))

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
