(ns in-the-wild.core-test
  (:require [clojure.test :refer :all]
            [in-the-wild.core :as sut]
            [in-the-wild.start :as start]
            [play-cljc.gl.core :as pc]))

(deftest init-test
  (let [window (start/->window)
        game   (pc/->game (:handle window))
        _      (sut/init game)
        assets @sut/*assets
        images (:images assets)
        state  @sut/*state] ;; Wait if needed

    (testing "after the init, at least a dozen images are available in assets"
      (is (>= (count images) 12)))

    (testing "after the init, a font is available (to improve)"
      (is (some? (type (:dynamic-entity assets)))))

    (testing "after the init, a font is available (to improve)"
      (is (zero? (:score state)))
      (is (empty? (:killers state)))
      (is (empty? (:rewards state))))))
