(ns in-the-wild.move
  (:require [in-the-wild.helper :as helper]
            [in-the-wild.tiles :as tiles]
            [play-cljc.instances :as i]))

(def damping 0.1)
(def max-velocity 14)
(def max-jump-velocity (* max-velocity 6))
(def deceleration 0.8)
(def gravity 2.5)
(def pre-start-gravity 1.5) ;; for Ninja to float down at the start
(def animation-secs 0.2)
(def max-movement-per-frame 0.9)
(def min-movement-per-frame -0.9)

(def ninja-modes [:ninja-no-booster :ninja-left-booster :ninja-both-booster :ninja-right-booster])
(def sushis {:sushi-california-roll {:points 3000}
             :sushi-shrimp-nigiri   {:points 5000}
             :sushi-tuna-nigiri     {:points 6500}
             :sushi-salmon-nigiri   {:points 15000}})

(defn duration [state]
  (quot (- (helper/now) (:start-time state)) 1000))

(defn decelerate [velocity]
  (let [velocity (* velocity deceleration)]
    (if (< (abs velocity) damping)
      0
      velocity)))

(defn check-restart
  [{:keys [pressed-keys restart?] :as state}]
  (let [still-restarting? (contains? pressed-keys :q)]
    (cond
      still-restarting? (assoc state :restart? true)
      restart?          (assoc state
                               :restart? false
                               :lifecycle :restart)
      :else             state)))

(defn check-pause
  [{:keys [pressed-keys pause?] :as state}]
  (let [space? (contains? pressed-keys :space)]
    (assoc state :pause?
           (if pause?
             (not (and (not-empty pressed-keys) (not space?)))
             space?))))

(defn check-debug
  [{:keys [pressed-keys debug?] :as state}]
  (if (and debug? (contains? pressed-keys :debug))
    (assoc state :player-x 267)
    state))

(defn get-x-velocity
  [{:keys [pressed-keys x-velocity]}]
  (cond
    (contains? pressed-keys :left)  (* -1 max-velocity)
    (contains? pressed-keys :right) max-velocity
    :else                           x-velocity))

(defn get-y-velocity
  [{:keys [pressed-keys y-velocity can-jump?]}]
  (if (and can-jump? (contains? pressed-keys :up))
    (* -1 max-jump-velocity)
    y-velocity))

(defn get-direction
  [{:keys [x-velocity direction]}]
  (cond
    (pos? x-velocity) :right
    (neg? x-velocity) :left
    :else             direction))

(defn touch?
  [pl-x pl-y object-x object-y]
  (and (< (abs (- pl-x object-x)) 1)
       (< (abs (- pl-y object-y)) 1)))

(defn move
  [{:keys [delta-time]}
   {:keys [lifecycle player-x player-y can-jump? started? tiled-map] :as state}]
  (cond (= lifecycle :game-over)                   state
        (> player-y (+ 2 (:map-height tiled-map))) (assoc state :lifecycle :game-over)
        :else
        (let [x-velocity (get-x-velocity state)
              y-velocity (+ (get-y-velocity state)
                            (if started? gravity pre-start-gravity))
              x-change   (-> (* x-velocity delta-time)
                             (max min-movement-per-frame)
                             (min max-movement-per-frame))
              y-change   (-> (* y-velocity delta-time)
                             (max min-movement-per-frame)
                             (min max-movement-per-frame))]
          (if (and (zero? x-change) (zero? y-change))
            state
            (assoc state
                   :x-velocity (decelerate x-velocity)
                   :y-velocity (decelerate y-velocity)
                   :x-change x-change
                   :y-change y-change
                   :player-x (+ player-x x-change)
                   :player-y (+ player-y y-change)
                   :can-jump? (if (neg? y-velocity) false can-jump?))))))

(defn prevent-move
  [{:keys [player-x player-y player-width player-height x-change y-change tiled-map] :as state}]
  (let [old-x (- player-x x-change)
        old-y (- player-y y-change)
        up?   (neg? y-change)]
    (merge state
           (when (tiles/touching-tile tiled-map "walls" player-x old-y player-width player-height)
             {:x-velocity 0 :x-change 0 :player-x old-x})
           (when (tiles/touching-tile tiled-map "walls" old-x player-y player-width player-height)
             {:y-velocity 0         :y-change 0 :player-y old-y
              :can-jump?  (not up?) :started? true}))))

(def image-id->bonus
  {312 {:name :choco-square :points 5000}
   364 {:name :gold-star :points 20000}})

(defn grab-bonus
  [{:keys [player-x player-y tiled-map tiled-map-entity] :as state}]
  (let [y    (inc (int player-y))
        x    (int player-x)
        tile (when (and (pos? x) (< x (:map-width tiled-map))
                        (pos? y) (< y (:map-height tiled-map)))
               (get-in state [:tiled-map :layers "bonus" x y]))]
    (if tile
      (let [tile-id       (.indexOf (:tiles tiled-map) tile)
            image-id      (:image-id tile)
            bonus         (get image-id->bonus image-id)]
        (if bonus
          (let [{:keys [points name]} bonus]
            (-> state
                (assoc :tiled-map-entity (i/dissoc tiled-map-entity tile-id)
                       :tiled-map
                       (-> tiled-map
                           (update :tiles (fn [tiles]
                                            (into (subvec tiles 0 tile-id)
                                                  (subvec tiles (inc tile-id)))))
                           (assoc-in [:layers "bonus" x y] nil)))
                (update :score + points)
                (update-in [:bonus name] (fnil inc 0))))
          state))
      state)))

(defn new-sushi [state]
  (when (and (> (duration state) 2) (> (rand 1000) 960))
    {:type       (rand-nth (keys sushis))
     :x          (helper/rand-span (:player-x state) 15)
     :y          0
     :velocity-y (/ (helper/rand-range 1 4) 10)}))

(defn drop-rewards
  [{:keys [lifecycle rewards tiled-map player-x player-y] :as state}]
  (let [px              (int player-x)
        py              (int player-y)
        game-over?      (= lifecycle :game-over)
        score-rewards
        (->> rewards
             (map #(update % :y + (:velocity-y %)))
             (filter #(and (pos? (:y %))
                           (< (:y %) (:map-height tiled-map))))
             (map #(if (and (not game-over?) (touch? px py (:x %) (:y %)) (contains? sushis (:type %)))
                     (assoc % :velocity-y (- (:velocity-y %)) :type :reward-poof :points (get-in sushis [(:type %) :points] ))
                     %)))
        points          (if game-over? 0 (reduce + (keep :points score-rewards)))
        updated-rewards (map #(dissoc % :points) score-rewards)]
    (assoc state
           :score (+ (:score state) points)
           :rewards (if-let [sushi (new-sushi state)]
                      (conj updated-rewards sushi)
                      updated-rewards))))

(defn new-killer? [state]
  (let [time                 (duration state)
        no-killer-sec        5
        sec-to-increase-prob 10
        start-range          20
        max-range            8]
    (when (> time no-killer-sec)
      (let [extra-time (- time no-killer-sec)
            rg-prob    (max max-range (- start-range (quot extra-time sec-to-increase-prob)))]
        (= (rand-int rg-prob) 1)))))

(defn drop-killers
  [{:keys [killers tiled-map player-x player-y] :as state}]
  (let [updated-killers
        (->> killers
             (map #(update % :y + (:velocity-y %)))
             (map #(update % :cycle (fn [c] (mod (inc c) 24))))
             (filter #(and (pos? (:y %))
                           (< (:y %) (:map-height tiled-map))))
             (map #(if (and (touch? player-x player-y (:x %) (:y %))
                            (not= (:lifecycle %) :splashed))
                     (assoc % :lifecycle :kill :velocity-y 0.002)
                     %)))]
    (assoc state
           :killers (if (new-killer? state)
                      (conj updated-killers {:y          0
                                             :x          (helper/rand-span player-x 10)
                                             :velocity-y (/ (helper/rand-range 1 2) 10)
                                             :cycle      0})
                      updated-killers)
           :lifecycle (if (some #(= (:lifecycle %) :kill) killers)
                        :die
                        (:lifecycle state))))) ;; similar as above: more idiomatic

(defn animate
  [{:keys [total-time]}
   {:keys [lifecycle x-velocity y-velocity target-color-weight clouds] :as state}]
  (assoc state
         :ninja-mode (cond
                       (not= y-velocity 0) :ninja-both-booster
                       (not= x-velocity 0) (let [cycle-time (mod total-time (* animation-secs (count ninja-modes)))]
                                             (nth ninja-modes (int (/ cycle-time animation-secs))))
                       :else               :ninja-no-booster)
         :direction (get-direction state)
         :target-color-weight (if (= lifecycle :game-over) (min 1.0 (+ target-color-weight 0.01)) target-color-weight)
         :clouds (map #(update % :x + (:speed %)) clouds)))

(defn check-die
  [{:keys [lifecycle killers] :as state}]
  (if (= lifecycle :die)
    (assoc state
           :lifecycle :game-over
           :killers (map #(if (= (:lifecycle %) :kill) (assoc % :lifecycle :splashed) %) killers))
    state))

(defn game-over
  [{:keys [lifecycle end-time] :as state}]
  (if (= lifecycle :game-over)
    (assoc state :end-time (or end-time (helper/now)))
    state))

(defn move-all [game]
  (fn [init-state]
    (let [{:keys [pause?] :as state} (check-pause init-state)]
      (if pause?
        state
        (->> state
             (move game)
             (check-debug)
             (prevent-move)
             (drop-rewards)
             (drop-killers)
             (animate game)
             (grab-bonus)
             (check-die)
             (game-over)
             (check-restart))))))
