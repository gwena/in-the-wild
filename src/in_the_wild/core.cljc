(ns in-the-wild.core
  (:require [in-the-wild.helper :as helper]
            [in-the-wild.utils :as utils]
            [in-the-wild.move :as move]
            [clojure.edn :as edn]
            [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.transforms :as t]
            #?(:clj  [play-cljc.macros-java :refer [gl math]]
               :cljs [play-cljc.macros-js :refer-macros [gl math]])
            #?(:clj  [in-the-wild.tile :as tile :refer [read-tiled-map]]
               :cljs [in-the-wild.tile :as tile :refer-macros [read-tiled-map]])
            #?(:cljs [goog.dom :as dom])))

(def cloud-pink-w 179)
(def cloud-pink-h 121)

(def game-over-w 375)
(def game-over-h 198)

(def display-title? true)
(def title-w 200)
(def title-h 46)

(defn generate-clouds []
  (let [nb-clouds (helper/rand-range 22 30)]
    (repeatedly nb-clouds
                #(hash-map
                  :type (rand-nth [:cloud-1 :cloud-2])
                  :size (+ 0.20 (rand 1))
                  :x (helper/rand-range 0 10000)
                  :y (helper/rand-range 0 300)
                  :invert (rand-nth [-1 1])
                  :speed (/ (helper/rand-range 1 5) 10)))))

(defonce *state (atom {:lifecycle           :start
                       :score               0 ;; @TODO not sure if some of the stuff should be move
                       :starting-time       (helper/now)
                       :mouse-x             0
                       :mouse-y             0
                       :pressed-keys        #{}
                       :x-velocity          0
                       :y-velocity          0
                       :player-x            20
                       :player-y            0
                       :player-width        1
                       :player-height       (/ 607 448) ;; ratio height / width
                       :can-jump?           false
                       :started?            false
                       :direction           :right
                       :player-images       {}
                       :player-walk-keys    [:ninja-no-booster :ninja-left-booster :ninja-both-booster :ninja-right-booster]
                       :player-image-key    :ninja-both-booster
                       :tiled-map           nil
                       :tiled-map-entity    nil
                       :camera              (e/->camera true)
                       :target-color-weight 0
                       :clouds              (generate-clouds)
                       :rewards             []
                       :killers             []}))

(def tiled-map (edn/read-string (read-tiled-map "level/level-1.tmx")))

(defn init [game]
  ;; allow transparency in images
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  ;; load images and put them in the state atom
  (doseq [[k path] {:ninja-no-booster    "ninja_hoverboard_none.png"
                    :ninja-left-booster  "ninja_hoverboard_left.png"
                    :ninja-right-booster "ninja_hoverboard_right.png"
                    :ninja-both-booster  "ninja_hoverboard_both.png"
                    :cloud-1             "cloud_pink.png"
                    :cloud-2             "cloud_transparent.png"
                    :game-over           "game-over.png"
                    :trophy              "trophy.png"
                    :ballon              "ballon.png"
                    :logo                "title-small.png"
                    :skull               "skull.png"}]

    (utils/get-image (str "img/" path)
                     (fn [{:keys [data width height]}]
                       (let [;; create an image entity (a map with info necessary to display it)
                             entity (e/->image-entity game data width height)
                             ;; compile the shaders so it is ready to render
                             entity (c/compile game entity)
                             ;; assoc the width and height to we can reference it later
                             entity (assoc entity :width width :height height)]
                         ;; add it to the state
                         (swap! *state update :player-images assoc k entity)))))

  ;; load the tiled map
  (tile/load-tiled-map game tiled-map
                       (fn [tiled-map entity]
                         (swap! *state assoc :tiled-map tiled-map :tiled-map-entity entity))))

(def color-blueish [0.68 0.85 0.92 1])
(def color-dark-blue [0.18 0.25 0.32 1])

(defn color-transform [from to to-weight]
  (let [from-weight (- 1.0 to-weight)
        components  (map vector from to)]
    (into [] (map #(+ (* (first %) from-weight) (* (last %) to-weight))
                  components))))

(defn screen-entity [target-color-weight]
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear    {:color (color-transform color-blueish color-dark-blue target-color-weight) :depth 1}})

(defn total-score [{:keys [score starting-time]}]
  (+ score (quot (- (helper/now) starting-time) 100)))

(defn tick [game]
  (let [{:keys [lifecycle
                endgame
                player-x
                player-y
                player-width
                player-height
                direction
                player-images
                player-image-key
                tiled-map
                tiled-map-entity
                camera
                target-color-weight
                clouds
                rewards
                killers]
         :as   state} @*state
        game-width    (utils/get-width game)
        game-height   (utils/get-height game)
        offset        (/ game-width 2)
        tile-size     (/ game-height (:map-height tiled-map))
        player-x      (* player-x tile-size)
        player-y      (* player-y tile-size)
        player-width  (* player-width tile-size)
        player-height (* player-height tile-size)
        pos-x         (- player-x offset)
        camera        (t/translate camera (- player-x offset) 0)]

    ;; render sky
    (c/render game (update (screen-entity target-color-weight) :viewport
                           assoc :width game-width :height game-height))
    ;; render the tiled map
    (when tiled-map-entity
      (c/render game (-> tiled-map-entity
                         (t/project game-width game-height)
                         (t/camera camera)
                         (t/scale
                          (* (/ (:width tiled-map-entity)
                                (:height tiled-map-entity))
                             game-height)
                          game-height))))

    (doseq [cloud clouds]
      (when-let [image (get player-images (:type cloud))]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/camera camera)
                      (t/translate (:x cloud) (:y cloud))
                      (t/scale (* (:size cloud) (:invert cloud) cloud-pink-w)
                               (* (:size cloud) cloud-pink-h)))))
      clouds)

    (doseq [reward rewards]
      (when-let [image (get player-images (:type reward))]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/camera camera)
                      (t/translate (* (:x reward) tile-size) (* (:y reward) tile-size))
                      (t/scale 64 64))))
      rewards)

    (doseq [killer killers]
      (when-let [image (get player-images :skull)]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/camera camera)
                      (t/translate (* (:x killer) tile-size) (* (:y killer) tile-size))
                      (t/scale 64 64))))
      rewards)

    (when (and (= lifecycle :game-over) (< (- (helper/now) endgame) 5000))
      (when-let [image (get player-images :game-over)]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/camera camera)
                      (t/translate (- player-x (/ game-over-w 2)) (/ (- game-height game-over-h) 2))
                      (t/scale game-over-w game-over-h)))))

    (when display-title?
      (when-let [image (get player-images :logo)]
        (c/render game
                  (-> image
                      (t/project game-width game-height)
                      (t/camera camera)
                      (t/translate (+ pos-x 10) 10)
                      (t/scale title-w title-h)))))

    ;; update score
    (when (not= lifecycle :game-over)
      #?(:cljs (dom/setTextContent (.getElementById js/document "score") (total-score state)))
      #?(:clj (println (total-score state))))

    (when-let [player (get player-images player-image-key)]
      ;; render the player
      (c/render game
                (-> player
                    (t/project game-width game-height)
                    (t/camera camera)
                    (t/translate (cond-> player-x
                                   (= direction :left) (+ player-width))
                                 player-y)
                    #_(t/rotate (if (= direction :left) -10 10))
                    (t/scale (cond-> player-width
                               (= direction :left) (* -1))
                             player-height)))

      ;; change the state to move the player
      (swap! *state
             (fn [state]
               (->> state
                    (move/move game)
                    (move/prevent-move)
                    (move/drop-rewards)
                    (move/drop-killers)
                    (move/animate game)
                    (move/check)
                    (move/gameover))))))
  game)
