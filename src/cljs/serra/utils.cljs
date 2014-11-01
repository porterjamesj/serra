(ns serra.utils
  (:require [om.core :as om :include-macros true]))

(defn initial-life [commander?]
  (if commander? 40 20))

(defn target-val [e]
  (.. e -target -value))

(defn add-player! [players player]
  (om/transact! players #(conj % player)))

(defn remove-player! [players name]
  "Removes player with `name' from the players vector"
  (om/transact! players
                (fn [ps] (vec (filter #(not= name (:name %)) ps)))))

(defn opponent-info [name players damages]
  "Prep info about the opponents of `name'. Returns a map whose keys
  are the names of opponents and whose values are damages taken by
  those opponents"
  (let [names (map :name players)
        opponents (filter #(not= name %) names)]
    (zipmap opponents
            (map #(get damages [% name] 0) opponents))))
