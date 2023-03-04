(ns tiles
  (:require [in-the-wild.core :as core]
            [in-the-wild.tiles :as tiles]
            [in-the-wild.start :as start]
            [play-cljc.gl.core :as pc]))

(defonce *a (atom {}))

(def game
  (let [window (start/->window)]
    (pc/->game (:handle window))))

(defn load-tiles []
  (tiles/load-tiled-map game core/tiled-map
                        (fn [tiled-map entity]
                          (swap! *a assoc :tiled-map tiled-map :tiled-map-entity entity))))

(comment
  (load-tiles)
  (->> @*a
       :tiled-map
       :layers
       (#(get % "bonus"))
       (map (fn [[k v]] v ))
       (map (fn [m] (filter (fn [[k v]] v) m)))
       (filter not-empty))

  (->> @*a
       :tiled-map-entity
       )

  )
