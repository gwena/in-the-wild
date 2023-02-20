(defmulti task first)

(defmethod task :default
  [[task-name]]
  (println "Unknown task:" task-name)
  (System/exit 1))

(require '[figwheel.main :as figwheel])

(defmethod task nil
  [_]
  (figwheel/-main "--build" "dev"))

(defmethod task "native"
  [_]
  (require '[in-the-wild.start-dev])
  ((resolve 'in-the-wild.start-dev/start)))

(defmethod task "repl"
  [_]
  (clojure.main/repl :init #(doto 'in-the-wild.start-dev require in-ns)))

(task *command-line-args*)
