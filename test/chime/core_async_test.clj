(ns chime.core-async-test
  (:require [chime.core-async :as sut]
            [chime.core-test :refer [check-timeliness!]]
            [clojure.test :as t]
            [clojure.core.async :as a :refer [go-loop]])
  (:import [java.time Instant]))

(t/deftest test-chime-ch
  (let [now (Instant/now)
        times [(.minusSeconds now 2)
               (.plusSeconds now 1)
               (.plusSeconds now 2)]
        chimes (sut/chime-ch times)
        proof (atom [])]

    (a/<!! (go-loop []
             (when-let [msg (a/<! chimes)]
               (swap! proof conj [msg (Instant/now)])
               (recur))))

    (t/is (= times (mapv first @proof)))
    (check-timeliness! (rest @proof))))

(t/deftest test-channel-closing
  (let [now (Instant/now)
        times [(.minusSeconds now 2)
               (.plusSeconds now 1)
               (.plusSeconds now 2)]
        chimes (sut/chime-ch times)
        proof (atom [])]
    (a/<!! (a/go
             (swap! proof conj (a/<! chimes))
             (swap! proof conj (a/<! chimes))
             (a/close! chimes)
             (when-let [v (a/<! chimes)]
               (swap! proof conj v))))
    (t/is (= (butlast times) @proof))))
