(ns serra.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [serra.utils :as util]))

(defn click-button [[text f & args] owner]
  "Renders a button (labeled `text') that calls `f' on `args' when clicked."
  (reify
    om/IRender
    (render [_]
      (dom/button
        #js {:onClick (fn [e] (apply f args))} text))))

(defn commander-damage-view [[player opp-info] owner]
  "Render a view of the damage taken by `player' from all opponents.
  `player' is a player cursor, `opp-info' is a map from opponent name to
  damage taken."
  (reify
    om/IRender
    (render [_]
      (. js/console log (clj->js opp-info))
      (dom/div nil
        (dom/p nil "Commander damage from:")
        (apply dom/ul nil
               (map (fn [[opp dmg]] (dom/li nil (str opp ": " dmg))) opp-info))))))

(defn player-view [[player opp-info min-life chan] owner]
  "Renders a view of `player', which is a map with :name and :life
  keys. max-life is the min life that the progress bar should show.
  `chan' is a channel that the player's name should be passed on when
  it should be deleted."
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "player"}
        (dom/h2 nil (:name player))
        (dom/button
         #js {:onClick (fn [_] (put! chan (:name @player)))}
         "Delete")
        (dom/input
         #js {:type "text" :value (:life player)
              :onChange (fn [e]
                          (when (not (empty? (util/target-val e)))
                            (om/update! player :life
                                        (js/parseInt (util/target-val e) 10))))})
        (dom/progress #js {:value (str (:life player))
                           :max (str min-life)})
        (om/build click-button ["-" om/transact! player :life dec])
        (om/build click-button ["+" om/transact! player :life inc])
        (when opp-info ;; we're in commander mode
          (om/build commander-damage-view [player opp-info]))))))

(defn add-player-view [[players init-life chan] owner]
  "Render a view for adding a new player. `players' is the players
  that exist so far, `init-life' is how much life the new player
  should start with, and `chan' is a channel newly created players
  should be send out on."
  (reify
    om/IInitState
    (init-state [_] {:name ""})
    om/IRenderState
    (render-state [_ state]
      (let [name (:name state)
            empty (= name "")
            taken (some #{name} (map :name players))
            maybe-player {:name name :life init-life}
            push-n-clear (fn []
                           (put! chan maybe-player)
                           (om/set-state! owner :name ""))]
        (dom/div nil
          (dom/p nil "Add new player")
          (dom/p nil "Name: ")
          (dom/input #js {:type "text"
                          :onChange
                          (fn [e] (om/set-state! owner :name (util/target-val e)))
                          :onKeyPress
                          (fn [e] (when (= (. e -key) "Enter") (push-n-clear)))
                          :value name})
          (dom/button
            #js {:onClick push-n-clear
                 :disabled (or taken empty)}
            (if taken "Player already exists!" "Add")))))))

(defn players-view [[players damages
                     commander? {:keys [new-players to-delete]}] owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go-loop []
        (let [new-player (<! new-players)]
          (util/add-player! players new-player)
          (recur)))
      (go-loop []
        (let [name (<! to-delete)]
          (util/remove-player! players name)
          (recur))))
    om/IRender
    (render [_]
      (let [init-life (util/initial-life commander?)]
        (dom/div nil
          (apply dom/ul nil
                 (om/build-all player-view
                   (let [max-life (max init-life
                                       (apply max (map :life players)))]
                     (vec (map (fn [p] [p
                                        (when commander?
                                          (util/opponent-info (:name p) players damages))
                                        max-life
                                        to-delete]) players))))))))))

(defn game-mode-view [{:keys [commander?] :as gd} owner]
  ;; irked that I have to wrap the value in a map just to
  ;; get a cursor
  (reify
    om/IRender
    (render [_]
      (dom/input #js {:type "checkbox"
                      :checked commander?
                      :onClick (fn [e] (om/transact! gd :commander? not))}
                 "commander"))))

(defn serra-view [{:keys [players damages game-data]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:chans {:new-players (chan)
               :to-delete (chan)}})
    om/IRenderState
    (render-state [_ state]
      (dom/div nil
        (om/build game-mode-view game-data)
        (om/build players-view [players
                                damages
                                (:commander? game-data)
                                (om/get-state owner :chans)])
        (om/build add-player-view [players
                                   (util/initial-life (:commander? game-data))
                                   (om/get-state owner [:chans :new-players])])))))
