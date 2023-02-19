# In the Wild

One-level-only platform game in Clojure(Script) using play-cljc.

Help the Ninja, lost in the wild, collect the trophies :trophy: and avoid the skulls :skull:.

## Game Engine and Libraries

The game library/engine used is [play-cljc](https://github.com/oakes/play-cljc) and [play-cljc-examples](https://github.com/oakes/play-cljc-examples) provided a great initial starting point, and boilerplate.

## Build and Run

To build the project, use the [Clojure CLI tool](https://clojure.org/guides/deps_and_cli).


To develop in a browser with live code reloading:

`clj -A:dev dev.clj`


To build a release version for the web:

`clj -A:prod prod.clj`


To develop the native version on each OS:

`clj -A:dev:linux dev.clj native`

`clj -A:dev:macos -J-XstartOnFirstThread dev.clj native`

`clj -A:dev:windows dev.clj native`


To build the native version as a jar file:

`clj -A:prod prod.clj uberjar`
