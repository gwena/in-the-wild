(ns in-the-wild.start-dev
  (:require [in-the-wild.start :as start]
            [in-the-wild.core :as c]
            [clojure.spec.test.alpha :as st]
            [clojure.spec.alpha :as s]
            [play-cljc.gl.core :as pc])
  (:import [org.lwjgl.glfw GLFW]
           [in_the_wild.start Window]))

(defn start []
  (st/instrument)
  (st/unstrument 'odoyle.rules/insert) ;; don't require specs for attributes
  (let [window (start/->window)
        game (pc/->game (:handle window))]
    (start/start game window)))

(defn -main []
  (start))
