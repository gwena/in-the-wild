(ns in-the-wild.move
  (:require [in-the-wild.utils :as utils]
            [in-the-wild.tile :as tile]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])))

(def ^:const damping 0.1)
(def ^:const max-velocity 14)
(def ^:const max-jump-velocity (* max-velocity 6))
(def ^:const deceleration 0.8)
(def ^:const gravity 2.5)
(def ^:const animation-secs 0.2)

(defn decelerate
  [velocity]
  (let [velocity (* velocity deceleration)]
    (if (< (math abs velocity) damping)
      0
      velocity)))

#_(defn pause? [state]
    (assoc state :pause (not (:pause state)))
    #_(if (contains? (:pressed-keys state) :down)
        (assoc state :pause (not (:pause state)))
        state))

(defn get-x-velocity
  [{:keys [pressed-keys x-velocity]}]
  (cond
    (contains? pressed-keys :left)  (* -1 max-velocity)
    (contains? pressed-keys :right) max-velocity
    :else                           x-velocity))

(defn get-y-velocity
  [{:keys [pressed-keys y-velocity can-jump?]}]
  (cond
    (and can-jump? (contains? pressed-keys :up)) (* -1 max-jump-velocity)
    :else                                        y-velocity))

(defn get-direction
  [{:keys [x-velocity direction]}]
  (cond
    (> x-velocity 0) :right
    (< x-velocity 0) :left
    :else            direction))

(defn touch? [pl-x pl-y object-x object-y]
  (and (< (abs (- pl-x object-x)) 1)
       (< (abs (- pl-y object-y)) 1)))

(defn move
  [{:keys [delta-time] :as game}
   {:keys [lifecycle player-x player-y can-jump? started? tiled-map] :as state}]
  (cond (= lifecycle :game-over)                   state
        (> player-y (+ 2 (:map-height tiled-map))) (assoc state :lifecycle :game-over)
        :else
        (let [x-velocity (get-x-velocity state)
              y-velocity (+ (get-y-velocity state) (if started?
                                                     gravity
                                                     ;; initially make the gravity lower for Ninja floats down
                                                     1.5))
              x-change   (* x-velocity delta-time)
              y-change   (* y-velocity delta-time)]
          (if (or (not= 0 x-change) (not= 0 y-change))
            (assoc state
                   :x-velocity (decelerate x-velocity)
                   :y-velocity (decelerate y-velocity)
                   :x-change x-change
                   :y-change y-change
                   :player-x (+ player-x x-change)
                   :player-y (+ player-y y-change)
                   :can-jump? (if (neg? y-velocity) false can-jump?))
            state))))

(defn prevent-move
  [{:keys [player-x player-y player-width player-height x-change y-change tiled-map] :as state}]
  (let [old-x (- player-x x-change)
        old-y (- player-y y-change)
        up?   (neg? y-change)]
    (merge state
           (when (tile/touching-tile? tiled-map "walls" player-x old-y player-width player-height)
             {:x-velocity 0 :x-change 0 :player-x old-x})
           (when (tile/touching-tile? tiled-map "walls" old-x player-y player-width player-height)
             {:y-velocity 0         :y-change 0 :player-y old-y
              :can-jump?  (not up?) :started? true}))))

(defn drop-rewards
  [{:keys [rewards tiled-map player-x player-y] :as state}]
  (let [px              (int player-x)
        py              (int player-y)
        score-rewards
        (->> rewards
             (map #(assoc % :y (+ (:y %) (:velocity-y %))))
             (filter #(and (> (:y %) 0)
                           (< (:y %) (:map-height tiled-map))))
             (map #(if (touch? px py (:x %) (:y %))
                     (assoc % :velocity-y (- (:velocity-y %)) :type :ballon :points true)
                     %)))
        points          (* 500 (count (filter #(:points %) score-rewards)))
        updated-rewards (map #(dissoc % :points) score-rewards)
        new-reward      (if (> (rand 100) 82) {:type :trophy
                                               :x    (utils/rand-span player-x 15) :y 0 :velocity-y (/ (utils/rand-range 1 4) 10)})] ;; @TODO when?
    (assoc state
           :score (+ (:score state) points)
           :rewards (if new-reward (conj updated-rewards new-reward) updated-rewards)))) ;; @todo should be more idiomatic clojure

(defn new-killer? [state]
  (let [time (quot (- (utils/now) (:starting-time state)) 1000)]
    (cond (< time 30)  false
          (< time 300) (= (rand-int 10) 1)
          :else        (= (rand-int 5) 1))))

(defn drop-killers
  [{:keys [killers tiled-map player-x player-y] :as state}]
  (let [updated-killers
        (->> killers
             (map #(assoc % :y (+ (:y %) (:velocity-y %))))
             (filter #(and (> (:y %) 0)
                           (< (:y %) (:map-height tiled-map))))
             (map #(if (and (touch? player-x player-y (:x %) (:y %))
                            (not= (:lifecycle %) :splashed))
                     (assoc % :lifecycle :kill :velocity-y 0.002)
                     %)))
        new-killer (if (new-killer? state)  {:y          0 :x (utils/rand-span player-x 10)
                                             :velocity-y (/ (utils/rand-range 1 2) 10)})] ;; @TODO when?
    (assoc state
           :killers (if new-killer (conj updated-killers new-killer) updated-killers)
           :lifecycle (if (some #(= (:lifecycle %) :kill) killers)
                        :die
                        (:lifecycle state))))) ;; similar as above: more idiomatic

(defn animate
  [{:keys [total-time]}
   {:keys [lifecycle x-velocity y-velocity direction player-images player-walk-keys target-color-weight clouds] :as state}]
  (let [direction (get-direction state)]
    (-> state
        (assoc :player-image-key
               (cond
                 (not= y-velocity 0)
                 :ninja-both-booster
                 (not= x-velocity 0)
                 (let [cycle-time (mod total-time (* animation-secs (count player-walk-keys)))]
                   (nth player-walk-keys (int (/ cycle-time animation-secs))))
                 :else
                 :ninja-no-booster))
        (assoc :direction direction)
        (assoc :target-color-weight (if (= lifecycle :game-over) (min 1.0 (+ target-color-weight 0.01)) target-color-weight))
        (assoc :clouds
               (map #(assoc % :x (+ (:x %) (:speed %))) clouds)))))

;; @TODO extract function to get value of field

(defn player-info []
  #?(:cljs (-> js/document
               (.getElementById "name")
               (.-value)))
  #?(:clj "Not implemented for clj"))

(defn check
  [{:keys [lifecycle killers endgame] :as state}]

  (if (= lifecycle :die)
    (do
      (assoc state
             :lifecycle :game-over
             :killers (map #(if (= (:lifecycle %) :kill) (assoc % :lifecycle :splashed) %) killers)))
    state))

(defn gameover
  [{:keys [lifecycle endgame] :as state}]
  (if (= lifecycle :game-over)
    (assoc state :endgame (if endgame endgame (utils/now)))
    state))
