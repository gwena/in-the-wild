(ns in-the-wild.start
  (:require [in-the-wild.core :as c]
            [play-cljc.gl.core :as pc]
            [goog.events :as events])
  ;; music disabled for now
  #_
  (:require-macros [in-the-wild.music :refer [build-for-cljs]]))

(defn msec->sec [n]
  (* 0.001 n))

(defn game-loop [game]
  (let [game (c/tick game)]
    (js/requestAnimationFrame
      (fn [ts]
        (let [ts (msec->sec ts)]
          (game-loop (assoc game
                            :delta-time (- ts (:total-time game))
                            :total-time ts)))))))

(defn listen-for-mouse [canvas]
  (events/listen js/window "mousemove"
    (fn [event]
      (swap! c/*state
        (fn [state]
          (let [bounds (.getBoundingClientRect canvas)
                x (- (.-clientX event) (.-left bounds))
                y (- (.-clientY event) (.-top bounds))]
            (assoc state :mouse-x x :mouse-y y)))))))

(defn keycode->keyword [keycode]
  (condp = keycode
    37 :left
    39 :right
    38 :up
    nil))

(defn listen-for-keys []
  (events/listen js/window "keydown"
    (fn [event]
      (when-let [k (keycode->keyword (.-keyCode event))]
        (swap! c/*state update :pressed-keys conj k))))
  (events/listen js/window "keyup"
    (fn [event]
      (when-let [k (keycode->keyword (.-keyCode event))]
        (swap! c/*state update :pressed-keys disj k)))))

(defn resize [context]
  (let [display-width context.canvas.clientWidth
        display-height context.canvas.clientHeight]
    (set! context.canvas.width display-width)
    (set! context.canvas.height display-height)))

(defn listen-for-resize [context]
  (events/listen js/window "resize"
    (fn [event]
      (resize context))))

;; start the game

(defonce context
  (let [canvas (js/document.querySelector "canvas")
        context (.getContext canvas "webgl2")
        initial-game (assoc (pc/->game context)
                            :delta-time 0
                            :total-time (msec->sec (js/performance.now)))]
    (c/init initial-game)
    (listen-for-mouse canvas)
    (listen-for-keys)
    (resize context)
    (listen-for-resize context)
    (game-loop initial-game)
    context))

;; build music, put it in the audio tag, and make the button toggle it on and off

(defonce play-music? (atom false))

(defonce audio (js/document.querySelector "#audio"))
;; music disabled for now
;(set! (.-src audio) (build-for-cljs))
(when @play-music? (.play audio))

(defonce button (js/document.querySelector "#audio-button"))
(set! (.-onclick button)
      (fn [e]
        (if (swap! play-music? not)
          (.play audio)
          (.pause audio))))

