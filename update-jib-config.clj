#!/usr/bin/env bb

(-> (slurp "jib.edn")
    (read-string)
    (update-in
     [:target-image :image-name]
     #(format "%s:%s" 
              %1
              (str/trim (slurp "version.txt"))))
    ((fn [d] (println (pr-str d)))))
