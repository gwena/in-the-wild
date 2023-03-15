(ns in-the-wild.core
  (:require [in-the-wild.ext.chars :as chars]
            [in-the-wild.helper :as helper]
            [in-the-wild.utils :as utils]
            [in-the-wild.move :as move]
            [clojure.edn :as edn]
            [play-cljc.gl.core :as c]
            [play-cljc.gl.entities-2d :as e]
            [play-cljc.gl.text :as text]
            [play-cljc.instances :as i]
            [play-cljc.transforms :as t]
            #?(:clj  [play-cljc.macros-java :refer [gl]]
               :cljs [play-cljc.macros-js :refer-macros [gl]])
            #?(:clj  [in-the-wild.tiles :as tiles :refer [read-tiled-map]]
               :cljs [in-the-wild.tiles :as tiles :refer-macros [read-tiled-map]])
            #?(:clj [in-the-wild.ext.text :refer [load-font-clj]]))
  #?(:cljs (:require-macros [in-the-wild.ext.text :refer [load-font-cljs]])))

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

(defn starting-state []
  {:lifecycle           :start
   :score               0
   :start-time          (helper/now)
   :pressed-keys        #{}
   :x-velocity          0
   :y-velocity          0
   :player-x            20
   :player-y            0
   :player-width        1
   :player-height       1 ;; same ratio for now (/ 257 231) ;; ratio height / width
   :can-jump?           false
   :started?            false
   :direction           :right
   :images              {}
   :ninja-mode          :ninja-both-booster
   :tiled-map           nil
   :tiled-map-entity    nil
   :camera              (e/->camera true)
   :target-color-weight 0
   :clouds              (generate-clouds)
   :rewards             []
   :killers             []})

(defonce *assets (atom {}))

(defonce *state (atom {}))

(defn reset-state! []
  (reset! *state (starting-state)))

(def tiled-map (edn/read-string (read-tiled-map "level/level-1.tmx")))

(def image-keys->filenames
  (->> [:title :ninja-title :keys :game-over :cloud-1 :cloud-2 :energy :released-energy]
       (concat move/ninja-modes)
       (map #(vector % nil))
       (concat [[:weapon {:file "five-blades-star" :size 3}]])
       (mapcat (fn [[k v]] (if (map? v)
                            (for [i (range 0 (:size v))]
                              [(keyword (str (name k) "-" i)) (str (:file v) "-" i ".png")])
                            [[k (or v (str (name k) ".png"))]])))
       (into {})))

(defn new-game [game]
  (reset-state!)

  ;; Load Tiled Map and Tileset
  (tiles/load-tiled-map game tiled-map
                        (fn [tiled-map entity]
                          (swap! *state assoc :tiled-map tiled-map :tiled-map-entity entity))))

(defn init [game]
  ;; Allow transparency in images
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  ;; Load Font
  (#?(:clj load-font-clj :cljs load-font-cljs)
   :blox-brk
   (fn [{:keys [data]} baked-font]
     (let [font-entity    (text/->font-entity game data baked-font)
           dynamic-entity (c/compile game (i/->instanced-entity font-entity))]
       (swap! *assets assoc
              :font-entity font-entity
              :dynamic-entity dynamic-entity))))

  ;; Load Images
  (doseq [[k filename] image-keys->filenames]
    (utils/get-image (str "img/" filename)
                     (fn [{:keys [data width height]}]
                       (let [;; create an image entity (a map with info necessary to display it)
                             entity (e/->image-entity game data width height)
                             ;; compile the shaders so it is ready to render
                             entity (c/compile game entity)
                             ;; assoc the width and height to we can reference it later
                             entity (assoc entity :width width :height height)]
                         (swap! *assets update :images assoc k entity)))))

  (new-game game))

(def color-blueish [0.68 0.85 0.92 1])
(def color-dark-blue [0.18 0.25 0.32 1])

(defn color-transform [from to to-weight]
  (let [from-weight (- 1.0 to-weight)
        components  (map vector from to)]
    (vec (map #(+ (* (first %) from-weight) (* (last %) to-weight))
              components))))

(defn screen-entity [target-color-weight]
  {:viewport {:x 0 :y 0 :width 0 :height 0}
   :clear    {:color (color-transform color-blueish color-dark-blue target-color-weight) :depth 1}})

(defn render [game camera [gw gh] {:keys [img w h x y]}]
  (when img
    (c/render game
              (-> img
                  (t/project gw gh)
                  (t/camera camera)
                  (t/translate x y)
                  (t/scale (or w (:width img)) (or h (:height img)))))))

(defn text->img [{:keys [font-entity dynamic-entity]} s]
  (reduce (partial apply chars/assoc-char)
          dynamic-entity
          (for [char-num (range (count s))]
            [0 char-num (chars/crop-char font-entity (get s char-num))])))

(defn tick [game]
  (let [{:keys [tiled-map
                tiled-map-entity
                lifecycle
                end-time
                player-x
                player-y
                player-width
                player-height
                direction
                ninja-mode
                camera
                target-color-weight
                clouds
                rewards
                killers]
         :as   state} @*state

        {:keys [dynamic-entity images]} @*assets

        game-size                (utils/get-size game)
        [game-width game-height] game-size
        offset                   (/ game-width 2)
        tile-size                (/ game-height (:map-height tiled-map))
        player-x                 (* player-x tile-size)
        player-y                 (* player-y tile-size)
        player-width             (* player-width tile-size)
        player-height            (* player-height tile-size)
        pos-x                    (- player-x offset)
        camera                   (t/translate camera (- player-x offset) 0)]

    ;; render sky
    (c/render game (update (screen-entity target-color-weight) :viewport
                           assoc :width game-width :height game-height))

    (render game camera game-size
            {:img tiled-map-entity
             :w   tile-size
             :h   tile-size
             :x   0 :y 0})

    (render game camera game-size
            {:img (get images :title)
             :x   (+ pos-x 10) :y 10})

    (doseq [cloud clouds]
      (render game camera game-size
              (let [{:keys [width height] :as img} (get images (:type cloud))]
                {:img img
                 :w   (* (:size cloud) (:invert cloud) width)
                 :h   (* (:size cloud) height)
                 :x   (:x cloud) :y (:y cloud)})))

    (doseq [reward rewards]
      (render game camera game-size
              {:img (get images (:type reward))
               :x   (* (:x reward) tile-size) :y (* (:y reward) tile-size)}))

    (doseq [killer killers]
      (render game camera game-size
              {:img (get images (keyword (str "weapon-" (quot (:cycle killer) 8))))
               :x   (* (:x killer) tile-size) :y (* (:y killer) tile-size)}))

    ;; render the ninja
    (render game camera game-size
            {:img (get images ninja-mode)
             :w   (cond-> player-width (= direction :left) (* -1))
             :h   player-height
             :x   (cond-> player-x (= direction :left) (+ player-width))
             :y   player-y})

    ;; render the score
    (when dynamic-entity
      (let [score (str (:score state))]
        (c/render game (-> (text->img @*assets score )
                           (t/project game-width game-height)
                           (t/translate 10 60)))))

    ;; If game-over, render the keys to play, and initially game-over, then the full size ninja
    (when (= lifecycle :game-over)
      (if (< (- (helper/now) end-time) 5000)
        (render game camera game-size
                (let [{:keys [width height] :as img} (get images :game-over)]
                  {:img img
                   :x   (- player-x (/ width 2)) :y (/ (- game-height height) 2)}))
        (render game camera game-size
                (let [{:keys [width height] :as img} (get images :ninja-title)]
                  {:img img
                   :x   (- player-x (/ width 2)) :y (/ (- game-height height) 2)})))
      (render game camera game-size
              (let [{:keys [width height] :as img} (get images :keys)]
                {:img img
                 :x   (- player-x (/ width 2)) :y (- game-height height 50)})))
    )

  (swap! *state (move/move-all game))

  (when (= (:lifecycle @*state) :restart)
    (init game))

  game)
