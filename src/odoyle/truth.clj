(ns odoyle.truth
  (:require [odoyle.rules :as o]))

(def rules
  (o/ruleset
   {::temperature-record
    [:what
     [id ::temperature temperature]
     [id ::location location]
     :then-finally
     (->> (o/query-all o/*session* ::temperature-record)
          (o/insert o/*session* ::derived ::all-temperatures)
          o/reset!)]

    ::insert-local-temperatures
    [:what
     [::derived ::all-temperatures all-temperatures]
     :then
     (let [temp-groups (group-by :location all-temperatures)]
       (o/reset!
        (reduce
         (fn [session [location temp-maps]]
           (let [temp-values (map :temperature temp-maps)]
             (-> session
                 (o/insert location {::local-temperature-high (apply max temp-values)
                                     ::local-temperature-low (apply min temp-values)
                                     ::local-temperature-location location}))))
         o/*session*
         temp-groups)))]

    ::insert-cold-temperature
    [:what
     [id ::temperature temperature]
     :when
     (< temperature 30)
     :then
     (o/insert! (inc id) ::cold-temperature temperature)]

    ::insert-always-over-zero
    [:what
     [id ::local-temperature-location loc]
     [id ::local-temperature-low low]
     :when
     (> low 0)
     :then
     (o/insert! id ::over-zero-location loc)]

    ::always-over-zero
    [:what
     [id ::over-zero-location over-zero-loc]]


    ::records-facts
    [:what
     [id ::local-temperature-high hight]
     [id ::local-temperature-low low]
     [id ::local-temperature-location loc]]

    ::cold
    [:what
     [id ::cold-temperature cold-temperature]]}))



(defn run-examples []
  (println "\n\nStart Example with O'Doyle")
  (let [initial-session (-> (reduce o/add-rule (o/->session) rules)
                            (o/insert 1 {::temperature -10 ::location "MCI"})
                            (o/insert 2 {::temperature 110 ::location "MCI"})
                            (o/insert 3 {::temperature 20 ::location "LHR"})
                            (o/insert 4 {::temperature 90 ::location "LHR"})
                            (o/fire-rules))]

    (println "Initial cold temperatures: "
             (o/query-all initial-session ::cold))

    (println "Initial local temperature records: "
             (o/query-all initial-session ::records-facts))

    (println "Initial locations that have never been below 0: "
             (o/query-all initial-session ::always-over-zero))

    (println "")
    (println "Now add a temperature of -5 to LHR and a temperature of 115 to MCI")

    (let [with-mods-session (-> initial-session
                                (o/insert 5 {::temperature -5 ::location "LHR"})
                                (o/insert 6 {::temperature 115 ::location "MCI"})
                                (o/fire-rules))]

      (println "New cold temperatures: "
               (o/query-all with-mods-session ::cold))

      (println "New local temperature records: "
               (o/query-all with-mods-session ::records-facts))

      (println "New locations that have never been below 0: "  ;; this should return empty with the new events, but it returns the same as `initial-session`
               (o/query-all with-mods-session ::always-over-zero))

      (let [with-retracted-session (-> with-mods-session
                                       (o/retract 5 ::temperature)
                                       (o/retract 5 ::location)
                                       (o/fire-rules))]

        (println "")
        (println "Now we retract the temperature of -5 at LHR")
        (println "Cold temperatures with this retraction: "
                 (o/query-all with-retracted-session ::cold)) ;; still displays the -5 cold temperature in the response

        (println "Local temperature records with this retraction: "
                 (o/query-all with-retracted-session ::records-facts)) ;; this should rollback to previous lowest temperature of 20 instead of -5 after retraction

        (println "Locations that have never been below zero with this retraction" ;; return LHR correctly, but only because it did not removed the value in `with-mods-session`
                 (o/query-all with-retracted-session ::always-over-zero)))))

  (println "\n=======================================")
  (println "End Example with O'Doyle\n\n"))

(comment

  (run-examples)

;; Start Example with O'Doyle
;; Initial cold temperatures:  [{:id 2, :cold-temperature -10} {:id 4, :cold-temperature 20}]
;; Initial local temperature records:  [{:id MCI, :hight 110, :low -10, :loc MCI} {:id LHR, :hight 90, :low 20, :loc LHR}]
;; Initial locations that have never been below 0:  [{:id LHR, :over-zero-loc LHR}]

;; Now add a temperature of -5 to LHR and a temperature of 115 to MCI
;; New cold temperatures:  [{:id 2, :cold-temperature -10} {:id 4, :cold-temperature 20} {:id 6, :cold-temperature -5}]
;; New local temperature records:  [{:id MCI, :hight 115, :low -10, :loc MCI} {:id LHR, :hight 90, :low -5, :loc LHR}]
;; New locations that have never been below 0:  [{:id LHR, :over-zero-loc LHR}]

;; Now we retract the temperature of -5 at LHR
;; Cold temperatures with this retraction:  [{:id 2, :cold-temperature -10} {:id 4, :cold-temperature 20} {:id 6, :cold-temperature -5}]
;; Local temperature records with this retraction:  [{:id MCI, :hight 115, :low -10, :loc MCI} {:id LHR, :hight 90, :low 20, :loc LHR}]
;; Locations that have never been below zero with this retraction [{:id LHR, :over-zero-loc LHR}]

;; =======================================
;; End Example with O'Doyle

  )
