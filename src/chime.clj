(ns chime
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.core.async :as a :refer [<! >! go-loop]]))

(defn- ms-until [time]
  (-> (t/interval (t/now) time) (t/in-millis)))

(defn chime-ch
  "Returns a core.async channel that 'chimes' at every time in the
  times list. Times that have already passed are ignored.

  Arguments:
    times - (required) Sequence of java.util.Dates, org.joda.time.DateTimes
                       or msecs since epoch

    ch    - (optional) Channel to chime on - defaults to a new unbuffered channel

  Usage:

    (let [chimes (chime-ch [(-> 2 t/secs t/ago) ; has already passed, will be ignored.
                            (-> 2 t/secs t/from-now)
                            (-> 3 t/secs t/from-now)])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (prn \"Chiming at:\" msg)
                 (recur)))))

  There are extensive usage examples in the README"
  [times & [{:keys [ch] :or {ch (a/chan)}}]]
  
  (go-loop [[next-time & more-times] (->> times
                                          (map tc/to-date-time)
                                          (drop-while #(t/before? % (t/now))))]
    (<! (a/timeout (ms-until next-time)))
    (>! ch next-time)

    (if (seq more-times)
      (recur more-times)
      (a/close! ch)))
  ch)

(comment
  ;; a quick test ;)
  
  (let [chimes (chime-ch [(-> 2 t/secs t/ago)
                          (-> 2 t/secs t/from-now)
                          (-> 3 t/secs t/from-now)])]
    (a/<!! (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Chiming at:" msg)
               (recur))))))
