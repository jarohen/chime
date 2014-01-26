(ns chime
  (:require [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clojure.core.async :as a :refer [<! >! go-loop]]))

(defn- ms-between [start end]
  (-> (t/interval start end) (t/in-millis)))

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

  (go-loop [now (t/now)
            [next-time & more-times] (->> times
                                          (map tc/to-date-time)
                                          (drop-while #(t/before? % now)))]
    (<! (a/timeout (ms-between now next-time)))
    (>! ch next-time)

    (if (seq more-times)
      (recur (t/now) more-times)
      (a/close! ch)))
  ch)

(defn chime-at [times f & [{:keys [error-handler]
                            :or {error-handler #(.printStackTrace %)}}]]
  (let [ch (chime-ch times)
        cancel-ch (a/chan)]
    (go-loop []
      (let [[time c] (a/alts! [cancel-ch ch] :priority true)]
        (when (and (= c ch) time)
          (<! (a/thread
               (try
                 (f time)
                 (catch Exception e
                   (error-handler e)))))
          (recur))))
    
    (fn cancel! []
      (a/close! cancel-ch))))
;; ---------- TESTS ----------

(comment
  ;; some quick tests ;)

  (chime-at [(-> 2 t/secs t/ago)
             (-> 2 t/secs t/from-now)
             (-> 3 t/secs t/from-now)
             (-> 5 t/secs t/from-now)]
            #(println "Chiming!" %))
  
  (let [chimes (chime-ch [(-> 2 t/secs t/ago)
                          (-> 2 t/secs t/from-now)
                          (-> 3 t/secs t/from-now)])]
    (a/<!! (go-loop []
             (when-let [msg (<! chimes)]
               (prn "Chiming at:" msg)
               (recur))))))

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
