(ns in-the-wild.core-test
  (:require [clojure.test :refer :all]
            [in-the-wild.core :as sut]
            [play-cljc.gl.core :as pc]))

(deftest init-test
  (let [game   (pc/->game (:handle nil)) ;; Window is not needed
        _      (sut/init game)
        images (@sut/*assets :images)] ;; Wait if needed

    (testing "after the init, at least a dozen images should be available in assets"
      (is (>= (count images) 12)))))
