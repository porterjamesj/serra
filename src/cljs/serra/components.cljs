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

(defn commander-damages-view [[player opp-info chan] owner]
  "Render a view of the damage taken by `player' from all opponents.
  `player' is a player cursor, `opp-info' is a map from opponent name
  to damage taken. `chan' is a channel to pass messages on when the
  player takes damage from a specific opponent."
  (reify
    om/IRender
    (render [_]
      (let [take-damage (fn [opp]
                          (put! chan [opp (:name @player)])
                          (om/transact! player :life dec))]
      (dom/div nil
        (dom/p nil "Commander damage from:")
        (apply dom/ul nil
               (map (fn [[opp dmg]] (dom/li nil
                                      (dom/span nil (str opp ": " dmg))
                                      (om/build click-button ["+1" take-damage opp])))
                 opp-info)))))))

(defn player-view [[player opp-info max-life
                    ;; chans
                    {:keys [commander-damage to-delete]}] owner]
  "Renders a view of `player', which is a map with :name and :life
  keys. `opp-info' is a map from opponent name to damage dealt by that
  opponent (or nil, which indicates that we aren't in commander mode)
  `max-life' is the maximum life that the progress bar should show.
  `chan' is a channel that the player's name should be passed on when
  it should be deleted."
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "player"}
        (dom/h2 nil (:name player))
        (dom/button
         #js {:onClick (fn [_] (put! to-delete (:name @player)))}
         "Delete")
        (dom/input
         #js {:type "text" :value (:life player)
              :onChange (fn [e]
                          (when (not (empty? (util/target-val e)))
                            (om/update! player :life
                                        (js/parseInt (util/target-val e) 10))))})
        (dom/progress #js {:value (str (:life player))
                           :max (str max-life)})
        (om/build click-button ["-" om/transact! player :life dec])
        (om/build click-button ["+" om/transact! player :life inc])
        (when (not (empty? opp-info)) ;; we're in commander mode
          (om/build commander-damages-view [player opp-info commander-damage]))))))

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
                           (when (not taken)
                             (put! chan maybe-player)
                             (om/set-state! owner :name "")))]
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

(defn players-view [[players damages commander? new-players]
                    owner]
  (reify
    om/IInitState
    (init-state [_]
      ;; to-delete gets sent string messages: the name of the player
      ;; to be deleted. commander-damage gets sent messages of the
      ;; form
      {:to-delete (chan)
       :commander-damage (chan)})
    om/IWillMount
    (will-mount [_]
      (go-loop []
        ;; loop for adding new players
        (let [new-player (<! new-players)]
          (util/add-player! players new-player)
          (recur)))
      (go-loop []
        ;; loop for deleting outgoing players
        (let [name (<! (om/get-state owner :to-delete))]
          (util/remove-player! players damages name)
          (recur)))
      ;; loop for applying commander damage
      (go-loop []
        ;; loop for deleting outgoing players
        (let [pair (<! (om/get-state owner :commander-damage))]
          (util/apply-damage! damages pair)
          (recur))))
    om/IRenderState
    (render-state [_ state]
      (let [max-life (max (util/initial-life commander?)
                          (apply max (map :life players)))
            opp-infos (map (fn [p]
                             (when commander?
                               (util/opponent-info (:name p) players damages)))
                        players)
            args     (map vector players opp-infos
                       (repeat max-life)
                       (repeat state))]
        (if (empty? players)
          (dom/p #js {:className "empty"} "No players.")
          (apply dom/div #js {:className "players-container"}
                 (om/build-all player-view args)))))))

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
      {:new-players (chan)})
    om/IRenderState
    (render-state [_ state]
      (dom/div nil
        (dom/h1 nil "serra")
        (om/build game-mode-view game-data)
        (om/build players-view [players
                                damages
                                (:commander? game-data)
                                (om/get-state owner :new-players)])
        (om/build add-player-view [players
                                   (util/initial-life (:commander? game-data))
                                   (om/get-state owner :new-players)])))))
