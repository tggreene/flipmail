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
            [camel-snake-kebab.extras :refer [transform-keys]]))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [message]
  (log "POPUP: got message:" message))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-let [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from POPUP!")
    (run-message-loop! background-port)))

; -- mail interaction

(def mail-atom (r/atom {}))

(def guerrilla-uri "http://api.guerrillamail.com/ajax.php")

(defn kebabify [m]
  (transform-keys ->kebab-case-keyword m))

(defn get-email-address [receiver]
  (GET guerrilla-uri {:params {:f "get_email_address"}
                      :handler #(go (>! receiver (kebabify %)))}))

(defn check-email [receiver]
  (GET guerrilla-uri {:params {:f "get_email_list" :offset 0}
                      :handler #(go (>! receiver (kebabify %)))}))

(defn fetch-email [receiver id]
  (GET guerrilla-uri {:params {:f "check_email" :email_id id}
                      :handler #(go (>! receiver (kebabify %)))}))

(defn show-current-email []
  (let [details-chan (chan)]
    (get-email-address details-chan)
    (go-loop []
      (let [details (<! details-chan)]
        (.log js/console details)
        (swap! mail-atom assoc :details details))
      (recur))))

(defn show-email []
  (let [emails-chan (chan)]
    (js/setInterval #(do
                      (log "Fetching Emails...")
                      (check-email emails-chan))
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
           [:div {:key id} (str from " " subject " | " excerpt)]) (:list emails))])

(defn app []
  (let [mail @mail-atom
        {:keys [email-addr email-timestamp alias sid-token]} (:details mail)
        emails (:emails mail)]
    [:div.container
     [:div.row [:div.four.column "Email:"][:div.eight.column {:style {:font-weight "bold"}} email-addr]]
     [:div.row [:div.four.column "Emails:"][:div.eight.column [email-component emails]]]]))



; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "POPUP: init")
  (connect-to-background-page!)
  (r/render-component [app] (js/document.getElementById "app"))
  (show-current-email)
  (show-email))
