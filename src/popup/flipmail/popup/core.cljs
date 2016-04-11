(ns flipmail.popup.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! >! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [dommy.core :as dommy :refer-macros [sel sel1]]
            [ajax.core :refer [GET POST json-response-format]]
            [reagent.core :as r]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [flipmail.guerrilla :as mail]))

; a message loop

(def mail-atom (r/atom {}))

(defn process-message! [message]
  (log "POPUP: got message:")
  (let [message (js->clj message :keywordize-keys true)]
    (log message)
    (when (= (:type message) "mail")
      (reset! mail-atom message)
      (log @mail-atom))))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-let [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port (clj->js {:client-type "popup"}))
    (run-message-loop! background-port)))

; mail interaction

(defn show-current-email []
  (let [details-chan (chan)]
    (mail/get-email-address details-chan)
    (go-loop []
      (let [details (<! details-chan)]
        (.log js/console details)
        (swap! mail-atom assoc :details details))
      (recur))))

(defn show-email []
  (let [emails-chan (chan)]
    (js/setInterval #(do
                      (log "Fetching Emails...")
                      (mail/check-email emails-chan))
                    5000)
    (go-loop []
      (let [emails (<! emails-chan)]
        (.log js/console emails)
        (swap! mail-atom assoc :emails emails)))))

(defn email-component [emails]
  [:div
   (map #(let [id (:mail-id %)
               subject (:mail-subject %)
               from (:mail-from %)
               excerpt (:mail-excerpt %)]
           [:div {:key id} (str from " " subject " | " excerpt)]) emails)])

(defn app []
  (let [{:keys [email inbox]} @mail-atom]
    [:div.container
     [:div.row [:div.four.column "Email:"][:div.eight.column {:style {:font-weight "bold"}} email]]
     [:div.row [:div.four.column "Inbox:"][:div.eight.column [email-component inbox]]]]))

; main entry point

(defn init! []
  (log "POPUP: init")
  (connect-to-background-page!)
  (r/render-component [app] (js/document.getElementById "app")))
