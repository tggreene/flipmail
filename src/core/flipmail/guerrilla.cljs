(ns flipmail.guerrilla
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [ajax.core :refer [GET POST json-response-format]]
            [cljs.core.async :refer [<! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [reagent.core :as r]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]]
            [camel-snake-kebab.extras :refer [transform-keys]]))

(def uri "http://api.guerrillamail.com/ajax.php")

(defn kebabify [m]
  (transform-keys ->kebab-case-keyword m))

(defn serial-get
  ([fn]
   (let [ch (chan)]
     (fn ch)
     ch))
  ([fn arg]
   (let [ch (chan)]
     (fn ch arg)
     ch)))


(defn get-email-address [receiver]
  (GET uri {:params {:f "get_email_address"}
            :handler #(go (>! receiver (kebabify %)))}))

(defn get-inbox [receiver]
  (GET uri {:params {:f "get_email_list" :offset 0}
            :handler #(go (>! receiver (kebabify %)))}))

(def check-email get-inbox)

(defn get-email-by-id [receiver id]
  (GET uri {:params {:f "fetch_email" :email_id id}
            :handler #(go (>! receiver (kebabify %)))}))

(defn get-full-inbox [receiver]
  (go
    (let [inbox-summary (<! (serial-get get-inbox))
          inbox (atom [])
          get-detail (fn [mail-item]
                       (go
                         (<! (serial-get get-email-by-id (:mail-id mail-item)))))]
      (doall
        (map get-detail inbox-summary)))))

(defn extend [receiver]
  (GET uri {:params {:f "extend"}
            :handler #(go (>! receiver (kebabify %)))}))
