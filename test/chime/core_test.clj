(ns chime.core-test
  (:require [chime.core :as chime]
            [clojure.test :as t])
  (:import (java.time Instant Duration)
           (java.time.temporal ChronoUnit)))

(defn check-timeliness!
  "Checks whether the chimes actually happended at the time for they were scheduled."
  [proof]
  (doseq [[value taken-at] proof
          :let [diff (->> [value taken-at]
                          (map #(.toEpochMilli ^Instant %))
                          ^long (apply -)
                          (Math/abs))]]
    (t/is (< diff 20)
          (format "Expected to run at ±%s but run at %s, i.e. diff of %dms" value taken-at diff))))

(t/deftest test-chime-at
  (let [times [(.minusSeconds (Instant/now) 2)
               (.plusSeconds (Instant/now) 1)
               (.plusSeconds (Instant/now) 2)]
        proof (atom [])]
    (with-open [sched (chime/chime-at times
                                    (fn [t]
                                      (swap! proof conj [t (Instant/now)])))]
      (Thread/sleep 2500))
    (t/is (= times (mapv first @proof)))
    (check-timeliness! (rest @proof))))

(t/deftest empty-times
  (t/testing "Empty or completely past sequences are acceptable"
    (let [proof (atom false)]
      (chime/chime-at []
                    identity
                    {:on-finished (fn []
                                    (reset! proof true))})

      (t/is @proof))))

(t/deftest test-on-finished
  (let [proof (atom false)]
    (chime/chime-at [(.plusMillis (Instant/now) 500) (.plusMillis (Instant/now) 1000)]
                  (fn [time])
                  {:on-finished (fn []
                                  (reset! proof true))})
    (Thread/sleep 1200)
    (t/is @proof)))

(t/deftest test-error-handler
  (t/testing "returning true continues the schedule"
    (let [proof (atom [])
          sched (chime/chime-at [(.plusMillis (Instant/now) 500)
                                 (.plusMillis (Instant/now) 1000)]
                                (fn [time]
                                  (throw (ex-info "boom!" {:time time})))
                                {:error-handler (fn [e]
                                                  (swap! proof conj e)
                                                  true)})]
      (t/is (not= ::timeout (deref sched 1500 ::timeout)))
      (t/is (= 2 (count @proof)))
      (t/is (every? ex-data @proof))))

  (t/testing "returning false stops the schedule"
    (let [proof (atom [])
          sched (chime/chime-at [(.plusMillis (Instant/now) 500)
                                 (.plusMillis (Instant/now) 1000)]
                                (fn [time]
                                  (throw (ex-info "boom!" {:time time})))
                                {:error-handler (fn [e]
                                                  (swap! proof conj e)
                                                  false)})]
      (t/is (not= ::timeout (deref sched 1500 ::timeout)))
      (t/is (= 1 (count @proof)))
      (t/is (every? ex-data @proof)))))

(t/deftest test-long-running-jobs
  (let [proof (atom [])
        !latch (promise)
        now (Instant/now)
        times (->> (chime/periodic-seq now (Duration/ofMillis 500))
                   (take 3))
        sched (chime/chime-at times
                            (fn [time]
                              (swap! proof conj [time (Instant/now)])
                              (Thread/sleep 750)))]

    (t/is (not= ::nope (deref sched 4000 ::nope)))
    (t/is (= times (map first @proof)))
    (check-timeliness! (map vector
                            (->> (chime/periodic-seq now (Duration/ofMillis 750))
                                 (take 3))
                            (map second @proof)))))

(t/deftest test-cancelling-overrunning-task
  (let [!proof (atom [])
        !error (atom nil)
        !latch (promise)]
    (with-open [sched (chime/chime-at (chime/periodic-seq (Instant/now) (Duration/ofSeconds 1))
                                      (fn [now]
                                        (swap! !proof conj now)
                                        (Thread/sleep 3000))
                                      {:error-handler (fn [e]
                                                        (reset! !error e))
                                       :on-finished (fn []
                                                      (deliver !latch nil))})]
      (Thread/sleep 2000))

    (t/is (not= ::timeout (deref !latch 500 ::timeout)))

    (t/is (= 1 (count @!proof)))
    (t/is (instance? InterruptedException @!error))))
