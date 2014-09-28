(ns serra.core
    (:require [serra.dev :refer [is-dev?]]
              [om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]))

(def app-state (atom {:players [{:name "James" :life 30}
                                {:name "Rachel" :life 40}]}))

(defn players-view [{:keys [players max-life]} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/ul nil
        (om/build-all player-view
          (vec (map (fn [p] {:player p :max-life max-life}) players)))))))

(defn player-view [{:keys [player max-life]} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div {:className "player"}
        (dom/h2 nil (:name player))
        (dom/div nil (:life player))
        (dom/input #js {:type "range"
                        :value (:life player)
                        :max max-life})))))

(om/root
 (fn [{:keys [players] :as app} owner]
   (reify om/IRender
     (render [_]
       (om/build players-view {:players players
                               :max-life (apply max (map :life players))}))))
  app-state
  {:target (. js/document (getElementById "app"))})
