# In the Wild

One-level-only platform game in Clojure(Script) using play-cljc.

Help the apprentice Ninja, lost in the wild, collect the hoverboard power-packs and avoid the falling weapons.

## Game Engine and Libraries

The game library/engine used is [play-cljc](https://github.com/oakes/play-cljc) and [play-cljc-examples](https://github.com/oakes/play-cljc-examples) provided a great initial starting point, and boilerplate.

## Build and Run

To build the project, use the [Clojure CLI tool](https://clojure.org/guides/deps_and_cli).


To develop in a browser with live code reloading:

```
clj -M:dev
```


To build a release version for the web:

```
clj -M:prod
```


To develop the native version:

```
clj -M:dev native
clj -M:dev:macos native # Warning for macos

```


To build the native version as a jar file:

```
clj -M:prod uberjar
```
