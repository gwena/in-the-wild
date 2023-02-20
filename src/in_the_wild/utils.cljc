(ns in-the-wild.utils
  #?(:clj (:require [clojure.java.io :as io]))
  #?(:clj (:import [java.nio ByteBuffer]
                   [org.lwjgl.glfw GLFW]
                   [org.lwjgl.system MemoryUtil]
                   [org.lwjgl.stb STBImage])))

(defn get-image [fname callback]
  #?(:clj  (let [is            (io/input-stream (io/resource (str "public/" fname)))
                 bytes         (with-open [out (java.io.ByteArrayOutputStream.)]
                         (io/copy is out)
                         (.toByteArray out))
                 *width        (MemoryUtil/memAllocInt 1)
                 *height       (MemoryUtil/memAllocInt 1)
                 *components   (MemoryUtil/memAllocInt 1)
                 direct-buffer (doto (ByteBuffer/allocateDirect (alength bytes))
                                 (.put bytes)
                                 (.flip))
                 decoded-image (STBImage/stbi_load_from_memory
                                direct-buffer *width *height *components
                                STBImage/STBI_rgb_alpha)
                 image         {:data   decoded-image
                                :width  (.get *width)
                                :height (.get *height)}]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             (MemoryUtil/memFree *components)
             (callback image))
     :cljs (let [image (js/Image.)]
             (doto image
               (-> .-src (set! fname))
               (-> .-onload (set! #(callback {:data   image
                                              :width  image.width
                                              :height image.height})))))))

(defn get-width [game]
  #?(:clj  (let [*width  (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 _       (GLFW/glfwGetFramebufferSize (:context game) *width *height)
                 n       (.get *width)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             n)
     :cljs (-> game :context .-canvas .-clientWidth)))

(defn get-height [game]
  #?(:clj  (let [*width  (MemoryUtil/memAllocInt 1)
                 *height (MemoryUtil/memAllocInt 1)
                 _       (GLFW/glfwGetFramebufferSize (:context game) *width *height)
                 n       (.get *height)]
             (MemoryUtil/memFree *width)
             (MemoryUtil/memFree *height)
             n)
     :cljs (-> game :context .-canvas .-clientHeight)))
