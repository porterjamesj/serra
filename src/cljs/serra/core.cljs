(ns serra.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

(def app-state (atom {:game-type :standard ;; :standard or :commander
                      :players [{:name "James" :life 30}
                                {:name "Rachel" :life 40}]}))

(defn initial-life [game-type]
  (if (= game-type :commander)
    40
    20))

(defn -target-val [e]
  (.. e -target -value))

(defn update-button [[chan cursor f text] owner]
  (reify
    om/IRender
    (render [_]
      (dom/button
        #js {:onClick (fn [e] (put! chan (f cursor)))} text))))

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
        (dom/input
         #js {:type "text" :value (:life player)
              :onChange (fn [e]
                          (when (not (empty? (-target-val e)))
                            (put! life-updates (js/parseInt (-target-val e) 10))))})
        (dom/progress #js {:value (:life player)
                           :max max-life})
        (om/build update-button [life-updates (:life player) dec "-"])
        (om/build update-button [life-updates (:life player) inc "+"])))))

(defn add-player [players name]
  ;; TODO validate that name is unused
  (om/transact! players #(conj % {:name name
                                  :life 20})))

(defn add-player-view [players owner]
  (reify
    om/IInitState
    (init-state [_]
      {})
    om/IRenderState
    (render-state [_ state]
      (dom/div nil
        (dom/p nil "Add new player")
        (dom/p nil "Name: ")
        (dom/input #js {:type "text"
                        :onChange
                        (fn [e] (om/set-state! owner :name (-target-val e)))})
        (dom/button #js {:onClick
                         (fn [e] (add-player players (:name state)))} "Add")))))

(defn players-view [players owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (apply dom/ul nil
          (om/build-all player-view
            (vec (map (fn [p] {:player p
                               :max-life (max 20 (apply max (map :life players)))})
                      players))))
        (om/build add-player-view players)))))

(om/root
 (fn [{:keys [players] :as app} owner]
   (reify om/IRender
     (render [_]
       (om/build players-view players))))
  app-state
  {:target (. js/document (getElementById "app"))})
