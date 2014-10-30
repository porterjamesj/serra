(ns serra.components
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]
            [serra.utils :as util]))

(defn update-button [[chan cursor f text] owner]
  "Renders a button (labeled `text') that puts the result of calling
`f' on `cursor' onto `chan' when clicked."
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
                          (when (not (empty? (util/target-val e)))
                            (put! life-updates (js/parseInt (util/target-val e) 10))))})
        (dom/progress #js {:value (:life player)
                           :max max-life})
        (om/build update-button [life-updates (:life player) dec "-"])
        (om/build update-button [life-updates (:life player) inc "+"])))))

(defn add-player-view [[players init-life chan] owner]
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

(defn players-view [[players init-life chan] owner]
  (reify
    om/IWillMount
    (will-mount [_]
        (go-loop []
          (let [new-player (<! chan)]
            (util/add-player players new-player)
            (recur))))
    om/IRender
    (render [_]
      (dom/div nil
        (apply dom/ul nil
          (om/build-all player-view
            (let [max-life (max init-life
                                (apply max (map :life players)))]
              (vec (map (fn [p] {:player p :max-life max-life})
                        players)))))))))

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

(defn serra-view [{:keys [players game-data]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:new-players-chan (chan)})
    om/IRenderState
    (render-state [_ state]
      (dom/div nil
        (om/build game-mode-view game-data)
        (om/build players-view [players
                                (util/initial-life (:commander? game-data))
                                (om/get-state owner :new-players-chan)])
        (om/build add-player-view [players
                                   (util/initial-life (:commander? game-data))
                                   (om/get-state owner :new-players-chan)])))))
