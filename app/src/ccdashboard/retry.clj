(ns ccdashboard.retry)

(defn retryable
  "Returns a function that retries f up to max-attempts, with exponentially
   increasing delay, when pred returns truthy for a raised exception.
   If max-attempts has been exceeded failure-handler (if any)
   is invoked with the exception and arguments of f, in that order.
   failure-handler must return an exception."
  [f pred & {:keys [failure-handler
                    max-attempts
                    backoff-millis]
             :or {failure-handler (fn [ex & _] ex)
                  max-attempts 5
                  backoff-millis 1000}}]
  (letfn [(retrying
            [args attempt]
            (let [[status result]
                  (try [::success (apply f args)]
                       (catch Exception e
                         (if (pred e)
                           ;; in most cases f should succeed, so only
                           ;; test for max-attempts after an error occurred
                           (if (< attempt max-attempts)
                             ;; increase waiting time exponentially
                             (do (Thread/sleep (+ (rand-int 1000)
                                                  (* (Math/pow 2 attempt)
                                                     backoff-millis)))
                                 ;; can't recur from within a catch, so signal a retry
                                 [::retry])
                             [::failure (apply failure-handler e args)])
                           ;; this is an unexpected exception so re-throw directly
                           (throw e))))]
              (case status
                ::success result
                ::retry (recur args (inc attempt))
                ::failure (throw result))))]
    (fn [& args]
      (retrying args 1))))
