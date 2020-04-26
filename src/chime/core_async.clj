(ns chime.core-async
  (:require [chime.core :as chime]
            [clojure.core.async :as a :refer [<! >! go-loop]]
            [clojure.core.async.impl.protocols :as p]))

(defn chime-ch
  "Returns a core.async channel that 'chimes' at every time in the times list.

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
  [times & [{:keys [ch], :or {ch (a/chan)}}]]

  (let [sched (chime/chime-at times
                              (fn [time]
                                (a/>!! ch time))
                              {:on-finished (fn []
                                              (a/close! ch))})]
    (reify
      p/ReadPort
      (take! [_ handler]
        (p/take! ch handler))

      p/Channel
      (close! [_] (.close sched))
      (closed? [_] (p/closed? ch)))))
