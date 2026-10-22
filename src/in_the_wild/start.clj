(ns in-the-wild.start
  (:require [in-the-wild.core :as c]
            [play-cljc.gl.core :as pc])
  (:import  [org.lwjgl.glfw GLFW Callbacks GLFWKeyCallbackI GLFW GLFWCharCallbackI
             GLFWFramebufferSizeCallbackI GLFWWindowCloseCallbackI GLFWScrollCallbackI]
            [org.lwjgl.opengl GL GL33])
  (:gen-class))

(def keycode-keyword-map
  ;; See issue with keycodes and localization, or different layouts, e.g. Colemak
  ;; https://github.com/glfw/glfw/issues/1502
  ;; Rather use common keys if possible
  {GLFW/GLFW_KEY_LEFT  :left
   GLFW/GLFW_KEY_RIGHT :right
   GLFW/GLFW_KEY_UP    :up
   GLFW/GLFW_KEY_SPACE :space
   GLFW/GLFW_KEY_Q     :q})

(defn keycode->keyword [keycode]
  (get keycode-keyword-map keycode))

(defn on-key! [window keycode scancode action mods]
  (when-let [k (keycode->keyword keycode)]
    (condp = action
      GLFW/GLFW_PRESS (swap! c/*state update :pressed-keys conj k)
      GLFW/GLFW_RELEASE (swap! c/*state update :pressed-keys disj k)
      nil)))

(defn on-char! [window codepoint])

(defn on-resize! [window width height])

(defn on-scroll! [window xoffset yoffset])

(defprotocol Events
  (on-key [this keycode scancode action mods])
  (on-char [this codepoint])
  (on-resize [this width height])
  (on-scroll [this xoffset yoffset])
  (on-tick [this game]))

(defrecord Window [handle])

(extend-type Window
  Events
  (on-key [{:keys [handle]} keycode scancode action mods]
    (on-key! handle keycode scancode action mods))
  (on-char [{:keys [handle]} codepoint]
    (on-char! handle codepoint))
  (on-resize [{:keys [handle]} width height]
    (on-resize! handle width height))
  (on-scroll [{:keys [handle]} xoffset yoffset]
    (on-scroll! handle xoffset yoffset))
  (on-tick [this game]
    (c/tick game)))

(defn listen-for-events [{:keys [handle] :as window}]
  (doto handle
    (GLFW/glfwSetKeyCallback
      (reify GLFWKeyCallbackI
        (invoke [this _ keycode scancode action mods]
          (on-key window keycode scancode action mods))))
    (GLFW/glfwSetCharCallback
      (reify GLFWCharCallbackI
        (invoke [this _ codepoint]
          (on-char window codepoint))))
    (GLFW/glfwSetFramebufferSizeCallback
      (reify GLFWFramebufferSizeCallbackI
        (invoke [this _ width height]
          (on-resize window width height))))
    (GLFW/glfwSetScrollCallback
      (reify GLFWScrollCallbackI
        (invoke [this _ xoffset yoffset]
          (on-scroll window xoffset yoffset))))
    (GLFW/glfwSetWindowCloseCallback
      (reify GLFWWindowCloseCallbackI
        (invoke [this window]
          (System/exit 0))))))

(defn ->window []
  (when-not (GLFW/glfwInit)
    (throw (Exception. "Unable to initialize GLFW")))
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
  (GLFW/glfwWindowHint GLFW/GLFW_RESIZABLE GLFW/GLFW_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MAJOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_CONTEXT_VERSION_MINOR 3)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_FORWARD_COMPAT GL33/GL_TRUE)
  (GLFW/glfwWindowHint GLFW/GLFW_OPENGL_PROFILE GLFW/GLFW_OPENGL_CORE_PROFILE)
  (if-let [window (GLFW/glfwCreateWindow 1024 768 "In the Wild!" 0 0)]
    (do
      (GLFW/glfwMakeContextCurrent window)
      (GLFW/glfwSwapInterval 1)
      (GL/createCapabilities)
      (->Window window))
    (throw (Exception. "Failed to create window"))))

(defn start [game window]
  (let [handle (:handle window)
        game   (assoc game :delta-time 0 :total-time (GLFW/glfwGetTime))]
    (GLFW/glfwShowWindow handle)
    (c/init game)
    (listen-for-events window)
    (loop [game game]
      (when-not (GLFW/glfwWindowShouldClose handle)
        (let [ts   (GLFW/glfwGetTime)
              game (assoc game
                          :delta-time (- ts (:total-time game))
                          :total-time ts)
              game (on-tick window game)]
          (GLFW/glfwSwapBuffers handle)
          (GLFW/glfwPollEvents)
          (recur game))))
    (Callbacks/glfwFreeCallbacks handle)
    (GLFW/glfwDestroyWindow handle)
    (GLFW/glfwTerminate)))

(defn -main [& args]
  (let [window (->window)]
    (start (pc/->game (:handle window)) window)))
