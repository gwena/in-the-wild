(ns in-the-wild.core-test
  (:require [clojure.test :refer :all]
            [in-the-wild.core :as sut]
            [in-the-wild.start :as start]
            [play-cljc.gl.core :as pc]))

(deftest init-test
  (let [window (start/->window)
        game   (pc/->game (:handle window))
        _      (sut/init game)
        images (@sut/*assets :images)
        ] ;; Wait if needed

    (testing "after the init, at least a dozen images should be available in assets"
      (is (>= (count images) 12)))))
