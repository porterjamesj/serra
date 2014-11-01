(ns serra.core
  (:require [serra.components :refer [serra-view]]
            [om.core :as om :include-macros true]))

(def app-state (atom
                ;; game-data basically is for keeping track of whether
                ;; we are in commander mode or not
                {:game-data {:commander? false}
                 ;; the list of players. a player is a map with
                 ;; keys :name and :life
                 :players []
                 ;; damages is a map for keeping track of who has
                 ;; dealt damage to who (for commander mode). keys are
                 ;; vectors of player names, [from to], and values are
                 ;; how much damage has been dealt
                 :damages {}}))

(defn main []
  (om/root
   (fn [app owner]
     (reify om/IRender
       (render [_]
         (om/build serra-view app))))
  app-state
  {:target (. js/document (getElementById "app"))}))
