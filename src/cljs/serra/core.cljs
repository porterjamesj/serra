(ns serra.core
  (:require [serra.components :refer [serra-view]]
            [om.core :as om :include-macros true]))

(defonce app-state (atom {:game-data {:commander? false}
                          :players []}))

(defn main []
  (om/root
   (fn [app owner]
     (reify om/IRender
       (render [_]
         (om/build serra-view app))))
  app-state
  {:target (. js/document (getElementById "app"))}))
