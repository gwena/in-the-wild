(defproject wild "0.1.0-SNAPSHOT"
  :license {:name "See the README.md Licensing section"}
  :repositories [["clojars" {:url           "https://clojars.org/repo"
                             :sign-releases false}]]
  :clean-targets ^{:protect false} ["target"]
  :main wild.start
  :aot [wild.start])
