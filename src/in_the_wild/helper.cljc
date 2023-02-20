(ns in-the-wild.helper)

(defn now []
  #?(:clj (System/currentTimeMillis)
     :cljs (.getTime (js/Date.))))

(defn rand-range [min max]
  (+ min (rand-int (inc max))))

(defn rand-span [middle offset]
  (rand-range (- middle offset) (+ middle offset)))
