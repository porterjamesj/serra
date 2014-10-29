(ns serra.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [put! chan <!]]))

(def app-state (atom {:commander? false
                      :players [{:name "James" :life 30}
                                {:name "Rachel" :life 40}]}))

(defn initial-life [commander?]
  (if commander? 40 20))

(defn -target-val [e]
  (.. e -target -value))

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
                          (when (not (empty? (-target-val e)))
                            (put! life-updates (js/parseInt (-target-val e) 10))))})
        (dom/progress #js {:value (:life player)
                           :max max-life})
        (om/build update-button [life-updates (:life player) dec "-"])
        (om/build update-button [life-updates (:life player) inc "+"])))))

(defn add-player [players name commander?]
  (om/transact! players #(conj % {:name name
                                  :life (initial-life commander?)})))

(defn add-player-view [{:keys [players commander?]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {})
    om/IRenderState
    (render-state [_ state]
      (let [name (:name state)
            taken (some #{name} (map :name players))]
        (dom/div nil
          (dom/p nil "Add new player")
          (dom/p nil "Name: ")
          (dom/input #js {:type "text"
                          :onChange
                          (fn [e] (om/set-state! owner :name (-target-val e)))
                          :onKeyPress
                          (fn [e] (when (= (. e -key) "Enter")
                                    (add-player players (:name state) commander?)))})
          (dom/button
            #js {:onClick
                 (fn [e] (add-player players (:name state) commander?))
                 :disabled taken}
            (if taken "Player already exists!" "Add")))))))

(defn players-view [{:keys [players commander?] :as app} owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (apply dom/ul nil
          (om/build-all player-view
            (let [max-life (max (initial-life commander?)
                                (apply max (map :life players)))]
              (vec (map (fn [p] {:player p
                                 :max-life max-life})
                   players)))))
        (om/build add-player-view app)))))

(om/root
 (fn [app owner]
   (reify om/IRender
     (render [_]
       (om/build players-view app))))
  app-state
  {:target (. js/document (getElementById "app"))})
