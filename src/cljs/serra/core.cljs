(ns serra.core
  (:require [serra.components :refer [serra-view]]
            [om.core :as om :include-macros true]))

(def app-state (atom {:commander? false
                      :players [{:name "James" :life 30}
                                {:name "Rachel" :life 40}]}))

(defn main []
  (om/root
   (fn [app owner]
     (reify om/IRender
       (render [_]
         (om/build serra-view app))))
  app-state
  {:target (. js/document (getElementById "app"))}))
