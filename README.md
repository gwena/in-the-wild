# In the Wild

One-level-only platform game in Clojure(Script) using play-cljc.

![Alt text](doc/in-the-wild-ninja.png?raw=true "Apprentice Ninja")

Help the apprentice Ninja, lost in the wild, collect the hoverboard power-packs and avoid the falling weapons.

## Game Libraries

The game library used is [play-cljc](https://github.com/oakes/play-cljc), _A library for making games that run in both OpenGL and WebGL_, by Zach Oakes. For the other libraries used with play-cljc, check the section [Companion Libraries](https://github.com/oakes/play-cljc#companion-libraries).

The repo [play-cljc-examples](https://github.com/oakes/play-cljc-examples) provided a good starting point, and some of the boilerplate code is still used.

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

## Play

[play online version](http://gwena.github.io/in-the-wild)

## Controls

* <kbd>:arrow_left:</kbd> - Left
* <kbd>:arrow_right:</kbd> - Right
* <kbd>:arrow_up:</kbd> - Jump
* <kbd>:arrows_counterclockwise:</kbd> - Restart game (in the browser)
* <kbd>spacebar</kbd> - Pause game
* <kbd>q</kbd> - Quick restart

## Origins

In April 2019 at the Dutch Clojure Days in Amsterdam, I attended the interesting and entertaining talk _How I Supercharged Learning Clojure through Gamification_ by [@ladymeyy](https://twitter.com/ladymeyy). Thought it would be fun to write a little game in Clojure (with my limited experience at the time). Two weeks later, I was away for the Easter weekend and during my daughter's afternoon naps, I wrote this little game.

The title, _in the wild_, is a reference to the sections of the same name in the enjoyable book from Russ Olsen _Getting Clojure_.

## Graphics, Music, Font

See [License](./LICENSE.md)
