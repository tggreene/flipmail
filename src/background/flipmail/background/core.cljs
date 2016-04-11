(ns flipmail.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! chan]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols :refer [post-message! get-sender]]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.runtime :as runtime]
            [flipmail.background.storage :refer [test-storage!]]
            [flipmail.guerrilla :as mail]))

(def clients (atom []))

; clients manipulation

(defn add-client! [client]
  (log "BACKGROUND: client connected" (get-sender client))
  (swap! clients conj client))

(defn remove-client! [client]
  (log "BACKGROUND: client disconnected" (get-sender client))
  (let [remove-item (fn [coll item] (remove #(identical? item %) coll))]
    (swap! clients remove-item client)))

; mail stuff

(def setup-mail
  (memoize (fn [email-chan inbox-chan interval]
             (mail/get-email-address email-chan)
             (mail/get-full-inbox inbox-chan)
             (js/setInterval #(do
                                (log "Fetching emails...")
                                (mail/get-full-inbox inbox-chan))
                             interval))))

(defn start-mail [client]
  (let [mail-atom (atom {:type "mail"
                         :email nil
                         :inbox []})
        post-mail (fn []
                   (let [mail (clj->js @mail-atom)]
                     (log "Sending mail...")
                     (log mail)
                     (when (and (some? mail) (some? client))
                       (post-message! client mail))))
        email-chan (chan)
        inbox-chan (chan)
        interval 10000]
    (log "It's happening!")
    (add-watch mail-atom :mail-watch post-mail)
    (go-loop []
      (let [email (<! email-chan)]
        (log "email = " email)
        (swap! mail-atom assoc :email (:email-addr email))))
    (go-loop []
      (let [inbox (<! inbox-chan)]
        (log "inbox = " inbox)
        (swap! mail-atom assoc :inbox (:list inbox))))
    (setup-mail email-chan inbox-chan interval)
    (post-mail)))

; client event loop

(defn run-client-message-loop! [client]
  (go-loop []
    (when-let [message (js->clj (<! client) :keywordize-keys true)]
      (when (= (:client-type message) "popup")
        (start-mail client))
      (recur))
    (remove-client! client)))

; event handlers

(defn handle-client-connection! [client]
  (add-client! client)
  (run-client-message-loop! client))

; main event loop

(defn process-chrome-event [event-num event]
  (log (gstring/format "BACKGROUND: got chrome event (%05d)" event-num) event)
  (let [[event-id event-args] event]
    (case event-id
      ::runtime/on-connect (apply handle-client-connection! event-args)
      nil)))

(defn run-chrome-event-loop! [chrome-event-channel]
  (go-loop [event-num 1]
    (when-let [event (<! chrome-event-channel)]
      (process-chrome-event event-num event)
      (recur (inc event-num)))))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; main entry point

(defn init! []
  (boot-chrome-event-loop!))
