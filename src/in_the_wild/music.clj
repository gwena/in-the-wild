(ns in-the-wild.music
  (:require [edna.core :as edna]
            [clojure.java.io :as io]))

(def music
  (-> (io/resource "public/music/aeriths-theme.edn")
      (slurp)
      (read-string)))

(defn build-for-clj []
  (-> (edna/export! music {:type :wav})
      .toByteArray
      io/input-stream))

(def edna->data-uri
  (memoize edna/edna->data-uri))

(defmacro build-for-cljs []
  (edna->data-uri music))
