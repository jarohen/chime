(ns chime-test
  (:require
   [chime :refer :all]
   [clojure.core.async :as a :refer [<! go-loop]]
   [clojure.test :refer :all])
  (:import (java.time Instant)
           (java.time.temporal ChronoUnit)))

(defn periodic-seq [^Instant start duration-or-period]
    (iterate
      #(.addTo duration-or-period ^Instant %)
      start))

(defn date->ms [d]
  (.toEpochMilli d))

(defn now
  []
  (Instant/now))

(defn check-timeliness!
  "Checks whether the chimes actually happended at the time for they were scheduled."
  [proof]
  (doseq [[value taken-at] @proof
          :let [diff (->> [value taken-at]
                          (map date->ms)
                          ^long (apply -)
                          (Math/abs))]]
    (is (< diff 20) (str "Expected to run at ±" value " but run at "
                          taken-at ", i.e. diff of "
                          diff "ms"))))

(deftest all

  (testing "chime-at"
    (let [will-be-omitted (.minusSeconds (now) 2)
          t1 (.plusSeconds (now) 2)
          t2 (.plusSeconds (now) 3)
          proof (atom [])]
      (chime-at [will-be-omitted
                 t1
                 t2]
                (fn [t]
                  (swap! proof conj [t
                                     (chime-test/now)])))
      (while (not (= (list t1 t2)
                     (map first @proof))))
      (is (= [t1 t2]
             (mapv first @proof)))
      (check-timeliness! proof)))

  (testing "chime-ch"
    (let [will-be-omitted (.minusSeconds (now) 2)
          t1 (.plusSeconds (now) 2)
          t2 (.plusSeconds (now) 3)
          chimes (chime-ch [will-be-omitted
                            t1
                            t2])
          proof (atom [])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (swap! proof conj [msg (chime-test/now)])
                 (recur))))
      (is (= [t1 t2]
             (mapv first @proof)))
      (check-timeliness! proof)))

  (testing "channel closing"
    (let [omitted (.minusSeconds (now) 2)
          expected (.plusSeconds (now) 2)
          dropped (.plusSeconds (now) 3)
          chimes (chime-ch [omitted expected dropped])
          proof (atom [])]
      (a/<!!
       (a/go
         (swap! proof conj (<! chimes))
         (a/close! chimes)
         (when-let [v (<! chimes)]
           (swap! proof conj v))))
      (is (= [expected]
             @proof))))

  (testing ":on-finished"
    (let [proof (atom false)]
      (chime-at [(.plusSeconds (now) 2) (.plusSeconds (now) 4)]

                (fn [time])

                {:on-finished (fn []
                                (reset! proof true))})
      (while (not @proof))
      (is @proof)))

  (testing "after a pause, past items aren't skipped"
    ;; test case for 0.1.5 bugfix - thanks Nick!

    (let [proof (atom [])
          ch (chime-ch (->> (periodic-seq (-> (.plusSeconds (chime-test/now) 2)
                                              (.truncatedTo (ChronoUnit/SECONDS)))
                                          (java.time.Duration/ofSeconds 1))
                            (take 3)))]

      (swap! proof conj [(a/<!! ch)
                         (chime-test/now)])
      (check-timeliness! proof)
      (a/<!! (a/timeout 4000))
      ;; Pending timestamps come through in the past.
      (is (a/<!! ch) "A time value is returned, and not nil")
      (is (a/<!! ch) "A time value is returned, and not nil")))

  (testing "cancellation works even in the face of overrun past tasks"
    (let [proof (atom [])
          do-stuff (fn [now]
                     ;; some overrunning task:
                     (swap! proof conj now)
                     (Thread/sleep 5000))
          cancel-stuff! (chime-at (rest (periodic-seq (chime-test/now)
                                                      (java.time.Duration/ofSeconds 1)))
                                  do-stuff)]
      (Thread/sleep 3000)
      (cancel-stuff!)
      (is (= 1
             (count @proof)))))

  (testing "Empty or completely past sequences are acceptable"
    (let [proof (atom false)]
      (chime-at (map #(.minusSeconds (now) (* 60 %)) [5 4 3 2])
                identity
                {:on-finished (fn []
                                (reset! proof true))})
      (while (not @proof))
      (is @proof))

    (let [proof (atom false)]
      (chime-at []
                identity
                {:on-finished (fn []
                                (reset! proof true))})
      (while (not @proof))
      (is @proof))))
