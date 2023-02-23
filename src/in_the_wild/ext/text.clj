(ns in-the-wild.ext.text
  (:require [play-cljc.text :as text]))

(def bitmap-size 512)
(def bitmaps {:blox-brk (text/->bitmap bitmap-size bitmap-size)})
(def font-height 64)
(def baked-fonts {:blox-brk (text/->baked-font "ttf/blox-brk.regular.ttf" font-height (:blox-brk bitmaps))})

(defn load-font-clj [font-key callback]
  (callback (font-key bitmaps) (font-key baked-fonts)))

(defmacro load-font-cljs [font-key callback]
  (let [{:keys [width height] :as bitmap} (font-key bitmaps)]
    `(let [image# (js/Image. ~width ~height)]
       (doto image#
         (-> .-src (set! ~(text/bitmap->data-uri bitmap)))
         (-> .-onload (set! #(~callback {:data image# :width ~width :height ~height} ~(font-key baked-fonts))))))))
