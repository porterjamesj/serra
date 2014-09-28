(ns serra.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [serra.dev :refer [is-dev?]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

(def app-state (atom {:players [{:name "James" :life 30}
                                {:name "Rachel" :life 40}]}))

(defn players-view [{:keys [players max-life]} owner]
  (reify
    om/IRender
    (render [_]
      (apply dom/ul nil
        (om/build-all player-view
          (vec (map (fn [p] {:player p
                             :max-life (max 20 (apply max (map :life players)))})
                    players)))))))

(defn -target-val [e]
  (.. e -target -value))

(defn player-view [{:keys [player max-life]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:life-updates (chan)})
    om/IWillMount
    (will-mount [_]
      (let [updates (om/get-state owner :life-updates)]
        (go-loop []
          (let [n (<! updates)]
            (om/update! player :life n)
            (recur)))))
    om/IRenderState
    (render-state [_ {:keys [life-updates]}]
      (dom/div {:className "player"}
        (dom/h2 nil (:name player))
        (dom/input #js {:type "text" :value (:life player)
                        :onChange (fn [e] (put! life-updates (-target-val e)))})
        (dom/progress #js {:value (:life player)
                           :max max-life})
        (dom/button #js {:onClick
                         (fn [e] (put! life-updates (dec (:life @player))))} "-")
        (dom/button #js {:onClick
                         (fn [e] (put! life-updates (inc (:life @player))))} "+")))))

(om/root
 (fn [{:keys [players] :as app} owner]
   (reify om/IRender
     (render [_]
       (om/build players-view {:players players}))))
  app-state
  {:target (. js/document (getElementById "app"))})
