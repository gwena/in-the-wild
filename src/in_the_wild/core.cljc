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

(def cloud-pink-w 256)
(def cloud-pink-h 111)

(def game-over-w 375)
(def game-over-h 198)

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
                       :score               0
                       :start-time          (helper/now)
                       :pressed-keys        #{}
                       :x-velocity          0
                       :y-velocity          0
                       :player-x            20
                       :player-y            0
                       :player-width        1
                       :player-height       (/ 257 231) ;; ratio height / width
                       :can-jump?           false
                       :started?            false
                       :direction           :right
                       :player-images       {}
                       :ninja-mode          :ninja-both-booster
                       :tiled-map           nil
                       :tiled-map-entity    nil
                       :camera              (e/->camera true)
                       :target-color-weight 0
                       :clouds              (generate-clouds)
                       :rewards             []
                       :killers             []}))

(def tiled-map (edn/read-string (read-tiled-map "level/level-1.tmx")))

(def images
  (->> [:title :game-over :cloud-1 :cloud-2 :energy :released-energy]
       (concat move/ninja-modes)
       (map #(vector % nil))
       (into {})
       (merge
        {:weapon {:file "five-blades-star" :size 2}})))

(def expand-images
  (->> images
       (mapcat (fn [[k v]] (if (map? v)
                            (for [i (range 0 (:size v))]
                              [(keyword (str (name k) "-" i)) (str (:file v) "-" i ".png")])
                            [[k v]])))
       (into {})))

(defn init [game]
  ;; allow transparency in images
  (gl game enable (gl game BLEND))
  (gl game blendFunc (gl game SRC_ALPHA) (gl game ONE_MINUS_SRC_ALPHA))

  ;; load font
  (#?(:clj load-font-clj :cljs load-font-cljs)
   :blox-brk
   (fn [{:keys [data]} baked-font]
     (let [font-entity    (text/->font-entity game data baked-font)
           dynamic-entity (c/compile game (i/->instanced-entity font-entity))]
       (swap! *state assoc
              :font-entity font-entity
              :dynamic-entity dynamic-entity))))

  ;; load images and put them in the state atom
  (doseq [[k path] expand-images]
    (let [filename (or path (str (name k) ".png"))]
      (utils/get-image (str "img/" filename)
                       (fn [{:keys [data width height]}]
                         (let [;; create an image entity (a map with info necessary to display it)
                               entity (e/->image-entity game data width height)
                               ;; compile the shaders so it is ready to render
                               entity (c/compile game entity)
                               ;; assoc the width and height to we can reference it later
                               entity (assoc entity :width width :height height)]
                           ;; add it to the state
                           (swap! *state update :player-images assoc k entity))))))

  ;; load the tiled map
  (tiles/load-tiled-map game tiled-map
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

(defn render [game camera {:keys [img gw gh w h x y]}]
  ;; TODO could abstract gw, gh, and use keyword for img, see also w h as optional
  (when img
    (c/render game
              (-> img
                  (t/project gw gh)
                  (t/camera camera)
                  (t/translate x y)
                  (t/scale w h)))))

(defn tick [game]
  (let [{:keys [font-entity
                dynamic-entity
                lifecycle
                end-time
                player-x
                player-y
                player-width
                player-height
                direction
                player-images
                ninja-mode
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

    (render game camera
            {:img tiled-map-entity
             :gw  game-width  :gh game-height
             :w   (* (/ (:width tiled-map-entity)
                        (:height tiled-map-entity))
                     game-height)
             :h   game-height :x  0 :y 0})

    (doseq [cloud clouds]
      (render game camera
              {:img (get player-images (:type cloud))
               :gw  game-width :gh game-height
               :w   (* (:size cloud) (:invert cloud) cloud-pink-w)
               :h   (* (:size cloud) cloud-pink-h)
               :x   (:x cloud) :y  (:y cloud) }))

    (doseq [reward rewards]
      (render game camera
              {:img (get player-images (:type reward))
               :gw  game-width                :gh game-height
               :w   64                        :h  64
               :x   (* (:x reward) tile-size) :y  (* (:y reward) tile-size)}))

    (doseq [killer killers]
      (render game camera
              {:img (get player-images (keyword (str "weapon-" (quot (:cycle killer) 15))))
               :gw  game-width                :gh game-height
               :w   64                        :h  64
               :x   (* (:x killer) tile-size) :y  (* (:y killer) tile-size)}))

    (when (and (= lifecycle :game-over) (< (- (helper/now) end-time) 5000))
      (render game camera
              {:img (get player-images :game-over)
               :gw  game-width                     :gh game-height
               :w   game-over-w                    :h  game-over-h
               :x   (- player-x (/ game-over-w 2)) :y  (/ (- game-height game-over-h) 2)}))

    (render game camera
            {:img (get player-images :title)
             :gw  game-width   :gh game-height
             :w   title-w      :h  title-h
             :x   (+ pos-x 10) :y  10})

    (render game camera
            {:img (get player-images ninja-mode)
             :gw  game-width :gh game-height
             :w   (cond-> player-width (= direction :left) (* -1))
             :h   player-height
             :x   (cond-> player-x (= direction :left) (+ player-width))
             :y   player-y})

    ;; render score
    (when dynamic-entity
      (let [score (str (:score state))]
        (c/render game (-> (reduce
                            (partial apply chars/assoc-char)
                            dynamic-entity
                            (for [char-num (range (count score))]
                              [0 char-num (chars/crop-char font-entity (get score char-num))]))
                           (t/project game-width game-height)
                           (t/translate 30 80))))))

  (swap! *state (move/move-all game))
  game)
