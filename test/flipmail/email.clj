(ns flipmail.email
  (:require [postal.core :as post]))

(defn test-email [addr]
  (post/send-message {:host "localhost"
                      :from "test@test.test"
                      :to addr
                      :subject "Test"
                      :body "<a href="">click me</a>"}))
