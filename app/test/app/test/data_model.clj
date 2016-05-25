(ns app.test.data-model
  (:require [schema.core :as s]
            [app.data-model :refer [EmailAddress]]
            [clojure.test :refer [are deftest run-tests]]))

(def valid-emails '())

(def invalid-emails '())
(deftest should-validate-email
    (are [mail] (string? (s/validate EmailAddress mail))
              "pRettyandsimple@example.com"
              "very.common@example.com"
              "disposable.style.email.with+symbol@example.com"
              "other.email-with-dash@example.de"
              "x@exaMple.com"
              "example@s.solutions"
              "#!$%&'*+-/=?_~@example.org"
              "3xample@Strange-email-.some.land"
              " for@jiras-leading-white.com"
              "NUmb3rs4r34ll0wd@emails.nrw"
              "4343424234@telefon.net"))

(deftest should-not-validate-email
  (are [mail] (not (re-matches #"^\s*[a-zA-Z0-9!#$%^&*-=_+'?~/]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\s*$" mail))
              "Abc.example.com"
              "A@b@c@example.com"
              "ab(c)d,e:f;g<h>i[j\\k]l@example.com"
              "just\"not\"right@example.com"
              "this is\"not\\allowed@example.com"
              "this\\ still\"not\\allowed@example.com"))
