(ns app.data-model
  (:require [schema.core :as s]
            [schema.coerce :as coerce]
            [schema.utils :as s-util]
            [plumbing.core :refer [safe-get]]
    #?@(:clj  [
            [clj-time.core :as time]
            [clj-time.format :as format]
            [clj-time.coerce :as time-coerce]]
        :cljs [[cljs-time.core :as time]
               [cljs-time.format :as format]
               [cljs-time.coerce :as time-coerce]])
            [clojure.set :as set])
  )

(def IDate "Cross-platform Date" #?(:clj java.util.Date
                                    :cljs js/Date))

(defn date? [x]
  #?(:clj (instance? java.util.Date x)
     :cljs (= js/Date (type x))))

(def LocalDate #?(:clj org.joda.time.LocalDate
                  :cljs js/goog.date.Date))

(def IDouble "Cross-platform Double, which maps to Number in JavaScript, but doesn't weaken the JVM type."
  #?(:clj java.lang.Double
     :cljs js/Number))

(s/defschema PositiveInt (s/constrained s/Int pos? "Positive number"))

(s/defschema NonNegativeNumber (s/constrained s/Num (fn [n] (>= n 0)) "Non-negative number"))

(s/defschema NonEmptyString (s/constrained s/Str seq "Non-empty string"))

(s/defschema EmailAddress (s/constrained NonEmptyString
                                         (fn [s] (re-matches #"[^@]+@[^@]+" s))
                                         "Email address"))

(s/defschema NumberHoursWorkedPerDay (s/constrained IDouble (fn [n] (<= 0 n 24))))

;;;;;;;;;;; DATA MODEL MAPPINGS ;;;;;;;;;;;;;;;;;


;;;;;;;;;;; JIRA -> DB ;;;;;;;;;;;;;;;
(def jira-invoicing-types->datomic-invoicing {"Nicht abrechenbar" :invoicing/not-billable
                                              "Nach Aufwand (T&M)" :invoicing/time-monthly
                                              "Festpreis einmalig" :invoicing/fixed-price
                                              "Individuell nach Vertrag" :invoicing/individual-by-contract
                                              "Support" :invoicing/support
                                              "Bereits abgerechnet" :invoicing/already-paid
                                              "UHD" :invoicing/user-help-desk})

(def jira-issue-type->datomic-issue-type {"Quote" :ticket.type/quote
                                          "Administrative Time" :ticket.type/admin
                                          "Quote item" :ticket.type/quote-item})

(def jira-worklog-attributes->db-attributes {:worklog_id       :worklog/id
                                             :work_description :worklog/description
                                             :username         :worklog/user
                                             :hours            :worklog/hours
                                             :work_date        :worklog/work-date
                                             :issue_id         :worklog/ticket})

(def jira-user-attributes {:name          :user/jira-username
                           :emailAddress :user/email
                           :displayName :user/display-name})


;;;;;;;;;;;; JIRA MODEL ;;;;;;;;;;;;;;;;;;

(s/defschema JiraInvoicingType (apply s/enum (keys jira-invoicing-types->datomic-invoicing)))

(s/defschema JiraIssueType (apply s/enum (keys jira-issue-type->datomic-issue-type)))

(s/defschema JiraWorkLog
  "The schema of Jira worklog tags as returned by the Tempo Plugin"
  {; :billing_key                        (s/maybe NonEmptyString)
   :issue_id         PositiveInt
   :issue_key        NonEmptyString
   ; :hash_value                         s/Str
   :username         NonEmptyString
   ;; daily rate
   ; (s/optional-key :customField_10084) IDouble
   ;; date format "2015-10-23 00:00:00"
   ; :work_date_time                     Date
   :work_description (s/maybe s/Str)
   ; :reporter                           NonEmptyString
   ;; date "2015-10-23"
   :work_date        IDate
   :hours            IDouble
   ;; remaining hours?
   ; (s/optional-key :customField_10406) Double
   ; :activity_id                        (s/maybe NonEmptyString)
   ; :activity_name                      (s/maybe NonEmptyString)
   ;; unique over all worklogs -> identity of entry
   :worklog_id       PositiveInt
   ;; same as username
   ; :staff_id                           NonEmptyString
   ;; some date? "2014-08-01 00:00:00.0"
   ; (s/optional-key :customField_10501) NonEmptyString
   ; :external_hours                     Double
   ; :billing_attributes
   ; :external_tstamp
   ; :parent_key
   ; :external_result
   ; :external_id
   ;; allow other fields
   s/Keyword         s/Any})


(s/defschema JiraIssue
  "Represents a Jira issue"
  {:id       PositiveInt
   :key      NonEmptyString
   :fields   {:summary           (s/maybe NonEmptyString)
              :components        [{:id       PositiveInt
                                   :name     NonEmptyString
                                   s/Keyword s/Any}]
              :customfield_12300 {:value    JiraInvoicingType
                                  :id       PositiveInt
                                  s/Keyword s/Any}
              ;; daily rate
              (s/optional-key :customField_10084) NonNegativeNumber
              :issuetype         {:name     JiraIssueType
                                  :id       PositiveInt
                                  s/Keyword s/Any}
              s/Keyword          s/Any}
   s/Keyword s/Any})

(s/defschema JiraUser
  {:name NonEmptyString
   :emailAddress EmailAddress
   :displayName NonEmptyString
   s/Keyword s/Any})

(s/defschema JiraCustomer "Actually a Jira component, but represents as a customer."
  {:id   PositiveInt
   :name NonEmptyString})

(s/defschema JiraTeam
  {:id s/Int
   :name NonEmptyString
   ;:mission :summary :lead :leadUser
   s/Keyword s/Any})

(s/defschema JiraTeamMember
  {:id         s/Int
   :membership {(s/optional-key :dateFromANSI) IDate
                s/Keyword       s/Any}
   :member {:name NonEmptyString
            s/Keyword s/Any}
   s/Keyword   s/Any})

(s/defschema DbInvoicingType (apply s/enum (vals jira-invoicing-types->datomic-invoicing)))

(s/defschema DbIssueType (apply s/enum (vals jira-issue-type->datomic-issue-type)))

(s/defschema DomainWorklog {:worklog/id                           PositiveInt
                            (s/optional-key :worklog/description) s/Str
                            ;                            :worklog/user
                            :worklog/hours                        NumberHoursWorkedPerDay
                            :worklog/work-date                    LocalDate
                            :worklog/ticket                       PositiveInt
                            s/Keyword                             s/Any})

(s/defschema DomainTicket {:ticket/id                         PositiveInt
                           :ticket/key                        NonEmptyString
                           (s/optional-key :ticket/title)     NonEmptyString
                           (s/optional-key :ticket/customer)  PositiveInt
                           (s/optional-key :ticket/type)      DbIssueType
                           (s/optional-key :ticket/invoicing) DbInvoicingType
                           s/Keyword                          s/Any})

(s/defschema DomainCustomer {:customer/id   PositiveInt
                             :customer/name NonEmptyString
                             s/Keyword      s/Any})

(s/defschema DomainUser {;; :user/id is assigned on the first login -> only optional here to simplify REPLing on fresh users
                         (s/optional-key :user/id)         s/Uuid
                         :user/jira-username               NonEmptyString
                         :user/email                       EmailAddress
                         :user/display-name                NonEmptyString
                         (s/optional-key :user/start-date) LocalDate
                         s/Keyword                         s/Any})

;;;;;;;;;;;; transformations
(def datetime-regex #"\d{4}-\d{2}-\d{2}")

(def timestamp-formatter (format/formatter "yyyy-MM-dd"))

(defn read-instant-date [date-string]
  (-> (format/parse timestamp-formatter date-string)
      (time-coerce/to-date)))

(defn datetime-matcher [schema]
  (when (= IDate schema)
    (coerce/safe
      (fn [x]
        (if (and (string? x) (re-matches datetime-regex x))
          (read-instant-date x)
          x)))))

(def date-and-string-coercer
  (coerce/first-matcher [datetime-matcher coerce/string-coercion-matcher]))

(def jira-worklog-coercer
  (coerce/coercer JiraWorkLog
                  date-and-string-coercer))

(def jira-issue-coercer
  (coerce/coercer JiraIssue
                  date-and-string-coercer))

(def jira-team-member-coercer
  (coerce/coercer JiraTeamMember
                  date-and-string-coercer))

(defn local-date-coercer [schema]
  (when (= LocalDate schema)
    (coerce/safe
      (fn [x]
        (if (date? x)
          (time-coerce/to-local-date x)
          x)))))

(def domain-worklog-coercer
  (coerce/coercer DomainWorklog
                  local-date-coercer))

(def domain-user-coercer
  (coerce/coercer DomainUser
                  local-date-coercer))

(defn throw-on-invalid-schema-error [x]
  (if (s-util/error? x)
    (throw (ex-info "schema violation" {:error x}))
    x))

(defn keep-known-fields [schema m]
  (select-keys m (keys schema)))

(defn keep-known-worklog-fields [worklog]
  (keep-known-fields JiraWorkLog worklog))

(defn ignore-nil-mappings [m]
  (into {}
        (remove (comp nil? val))
        m))

(def to-jira-issue (comp throw-on-invalid-schema-error jira-issue-coercer))

(def to-jira-worklog (comp ignore-nil-mappings
                           keep-known-worklog-fields
                           throw-on-invalid-schema-error
                           jira-worklog-coercer))

(def to-jira-team-member (comp throw-on-invalid-schema-error
                               jira-team-member-coercer))

(def to-domain-worklog (comp throw-on-invalid-schema-error
                             domain-worklog-coercer))

(defn to-domain-ticket [ticket]
  (s/validate DomainTicket ticket)
  ticket)

(defn to-domain-customer [customer]
  (s/validate DomainCustomer customer)
  customer)

(defn jira-user->domain-user [jira-user]
  (-> jira-user
      (set/rename-keys jira-user-attributes)
      (select-keys (vals jira-user-attributes))))

(def to-domain-user (comp throw-on-invalid-schema-error
                          domain-user-coercer))