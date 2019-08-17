(ns chime-test
  (:require
   [chime :refer :all]
   [clj-time.coerce]
   [clj-time.core :as t]
   [clj-time.periodic :refer [periodic-seq]]
   [clojure.core.async :as a :refer [<! go-loop]]
   [clojure.test :refer :all]))

(defn check-timeliness!
  "Checks whether the chimes actually happended at the time for they were scheduled."
  [proof]
  (doseq [[value taken-at] @proof
          :let [diff (->> [value taken-at]
                          (map clj-time.coerce/to-long)
                          (apply -)
                          (Math/abs))]]
    (is (< diff 10))))

(deftest all

  (testing "chime-at"
    (let [will-be-omitted (-> 2 t/seconds t/ago)
          t1 (-> 2 t/seconds t/from-now)
          t2 (-> 3 t/seconds t/from-now)
          proof (atom [])]
      (chime-at [will-be-omitted
                 t1
                 t2]
                (fn [t]
                  (swap! proof conj [t
                                     (t/now)])))
      (while (not (= (list t1 t2)
                     (map first @proof))))
      (is (= [t1 t2]
             (mapv first @proof)))
      (check-timeliness! proof)))

  (testing "chime-ch"
    (let [will-be-omitted (-> 2 t/seconds t/ago)
          t1 (-> 2 t/seconds t/from-now)
          t2 (-> 3 t/seconds t/from-now)
          chimes (chime-ch [will-be-omitted
                            t1
                            t2])
          proof (atom [])]
      (a/<!! (go-loop []
               (when-let [msg (<! chimes)]
                 (swap! proof conj [msg (t/now)])
                 (recur))))
      (is (= [t1 t2]
             (mapv first @proof)))
      (check-timeliness! proof)))

  (testing "channel closing"
    (let [omitted (-> 2 t/seconds t/ago)
          expected (-> 2 t/seconds t/from-now)
          dropped (-> 3 t/seconds t/from-now)
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
      (chime-at [(-> 2 t/seconds t/from-now) (-> 4 t/seconds t/from-now)]

                (fn [time])

                {:on-finished (fn []
                                (reset! proof true))})
      (while (not @proof))
      (is @proof)))

  (testing "after a pause, past items aren't skipped"
    ;; test case for 0.1.5 bugfix - thanks Nick!

    (let [proof (atom [])
          ch (chime-ch (->> (periodic-seq (-> (-> (t/now) (t/plus (t/seconds 1)))
                                              (.withMillisOfSecond 0))
                                          (-> 1 t/seconds))
                            (take 3)))]

      (swap! proof conj [(a/<!! ch)
                         (t/now)])
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
          cancel-stuff! (chime-at (rest (periodic-seq (t/now)
                                                      (t/seconds 1)))
                                  do-stuff)]
      (Thread/sleep 3000)
      (cancel-stuff!)
      (is (= 1
             (count @proof)))))

  (testing "Empty or completely past sequences are acceptable"
    (let [proof (atom false)]
      (chime-at (map #(-> % t/minutes t/ago) [5 4 3 2])
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
