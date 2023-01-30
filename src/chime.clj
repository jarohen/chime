(ns ^:deprecated chime
  (:require [chime.core :as chime]
            [chime.core-async :as chime-async]
            [clojure.core.async :as a]
            [clojure.tools.logging :as log])
  (:import java.lang.AutoCloseable))

(defn ^:deprecated chime-ch
  "Deprecated: use `chime.core-async/chime-ch` - see the source of this fn for a migration.

  Returns a core.async channel that 'chimes' at every time in the
  times list. Times that have already passed are ignored.

  Arguments:
    times - (required) Sequence of java.util.Dates, java.time.Instant,
                       java.time.ZonedDateTime or msecs since epoch

    ch    - (optional) Channel to chime on - defaults to a new unbuffered channel
                       Closing this channel stops the schedule.

  Usage:

    (let [chimes (chime-ch [(.plusSeconds (Instant/now) -2)
                            (.plusSeconds (Instant/now) 2)
                            (.plusSeconds (Instant/now) 2)])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (prn \"Chiming at:\" msg)
                 (recur)))))

  There are extensive usage examples in the README"
  [times & [{:keys [ch] :or {ch (a/chan)}}]]

  (defonce chime-ch-deprecated-warning
    (log/warn "`chime/chime-ch` has moved to chime.core-async/chime-ch. see source of `chime/chime-ch` for the migration"))

  (chime-async/chime-ch (-> times chime/without-past-times)
                        {:ch ch}))

(defn ^:deprecated chime-at
  "Deprecated: use `chime.core/chime-at` instead - see the source of this fn for a migration.

  Calls `f` with the current time at every time in the `times` list."
  [times f & [{:keys [error-handler on-finished]
               :or {on-finished #()}
               :as opts}]]
  (defonce chime-at-deprecated-warning
    (log/warn "`chime/chime-at` has moved to chime.core/chime-at. see source of `chime/chime-at` for the migration"))

  (let [sched (chime/chime-at (->> times (chime/without-past-times))
                              f
                              (assoc opts
                                :error-handler (fn [e]
                                                 (if error-handler
                                                   (try
                                                     (error-handler e)
                                                     true
                                                     (catch Exception e
                                                       false))
                                                   (do
                                                     (log/warn e "Error running Chime schedule")
                                                     (not (instance? InterruptedException e)))))))]
    (fn close []
      (.close ^AutoCloseable sched))))
