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

(def mail-details (r/atom {}))

(def guerrilla-uri "http://api.guerrillamail.com/ajax.php")

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

(defn kebabify [m]
  (transform-keys ->kebab-case-keyword m))

(defn get-email-address [receiver]
  (GET guerrilla-uri {:params {:f "get_email_address"}
                      :handler #(go (>! receiver (kebabify %)))}))

(defn check-email [receiver]
  (GET guerrilla-uri {:params {:f "check_email"}
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
        (reset! mail-details details))
      (recur))))

(defn app []
  (let [{:keys [email-addr email-timestamp alias sid-token]} @mail-details]
    [:div.container
     [:div.row [:div.four.column "Email:"][:div.eight.column {:style "font-weight: bold"} email-addr]]
     [:div.row [:div.four.column "Time:"][:div.eight.column {:style "font-weight: bold" email-timestamp}]]
     [:div.row [:div.four.column "Alias:"][:div.eight.column [:strong alias]]]
     [:div.row [:div.four.column "SID:"][:div.eight.column [:strong sid-token]]]]))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "POPUP: init")
  (connect-to-background-page!)
  (r/render-component [app] (js/document.getElementById "app"))
  (show-current-email))
