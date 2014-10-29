(ns serra.utils
  (:require [om.core :as om :include-macros true]))

(defn initial-life [commander?]
  (if commander? 40 20))

(defn target-val [e]
  (.. e -target -value))

(defn add-player [players name commander?]
  (om/transact! players #(conj % {:name name
                                  :life (initial-life commander?)})))
