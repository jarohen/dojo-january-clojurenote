(ns clojurenote.cljs.app
  (:require [dommy.core :as d]
            [cljs.core.async :refer [<! put! >! chan timeout]]
            [clidget.widget :refer [defwidget] :include-macros true]
            [goog.events.KeyCodes :as kc])
  (:require-macros [dommy.macros :refer [node sel1]]
                   [cljs.core.async.macros :refer [go-loop go]]))

(enable-console-print!)

(defn action-button [{:keys [caption events-ch action task]}]
  (doto (node [:button ^:attrs {:style {:margin "0.4em"}
                                :class (name action)} caption])
    (d/listen! :click
        #(put! events-ch {:action action
                          :task task}))))

(defwidget todo-item [{} task events-ch]
  (node
   [:li task
    (action-button {:caption "[X]"
                    :action :deleted
                    :events-ch events-ch
                    :task task})
    (action-button {:caption "▲"
                    :action :up
                    :events-ch events-ch
                    :task task})
    (action-button {:caption "▼"
                    :action :down
                    :events-ch events-ch
                    :task task})]))


(defwidget todo-list [{:keys [todos]} events-ch]
  (node
   [:ul
    (for [task todos]
      (todo-item {} task events-ch))]))

(defn remove-item [todos task]
  (remove #(= task %) todos))

(defn new-item [events]
  (let [text-box (node [:input {:type "text"}])]
    (doto text-box
      (d/listen! :keyup
          (fn [e]
            (when (= (.-keyCode e) kc/ENTER)
              (put! events {:action :created
                            :task (d/value text-box)})
              (d/set-value! text-box nil)))))))

(defn move-item [coll dir task]
  (->> (let [[before [value & after]] (split-with #(not= task %) coll)]
         (case dir
           :up (concat (butlast before) [value (last before)] after)
           :down (concat before [(first after) value] (rest after))))
       vec))

(set! (.-onload js/window)
      (fn []
        (let [!todos (atom ["First Task"])
              events-ch (chan)]
          (d/replace-contents! (sel1 :#content)
                               (node [:div.container
                                      [:h2 {:style {:margin-top :1em}}
                                       "Clojure Note"]
                                      [:div (todo-list {:!todos !todos} events-ch)
                                       (new-item events-ch)]]))
          
          (go
            (<! (timeout 2000))
            (swap! !todos conj "hello world"))

          
          (go-loop []
            (when-let [{:keys [action task] :as event} (<! events-ch)]
              (case action
                :deleted
                (swap! !todos remove-item task)

                :created
                (swap! !todos conj task)

                :up (swap! !todos move-item :up task)
                :down (swap! !todos move-item :down task))
              (recur))))))

