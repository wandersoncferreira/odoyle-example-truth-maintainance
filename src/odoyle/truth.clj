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

    ::cold-temperatures
    [:what
     [id ::temperature temperature]
     :when
     (< temperature 30)]

    ::always-over-zero
    [:what
     [id ::local-temperature-location loc]
     [id ::local-temperature-low low]
     :when
     (> low 0)]

    ::records-facts
    [:what
     [id ::local-temperature-high hight]
     [id ::local-temperature-low low]
     [id ::local-temperature-location loc]]}))



(defn run-examples []
  (println "\n\nStart Example with O'Doyle")
  (let [initial-session (-> (reduce o/add-rule (o/->session) rules)
                            (o/insert 1 {::temperature -10 ::location "MCI"})
                            (o/insert 2 {::temperature 110 ::location "MCI"})
                            (o/insert 3 {::temperature 20 ::location "LHR"})
                            (o/insert 4 {::temperature 90 ::location "LHR"})
                            (o/fire-rules))]

    (println "Initial cold temperatures: "
             (o/query-all initial-session ::cold-temperatures))

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
               (o/query-all with-mods-session ::cold-temperatures))

      (println "New local temperature records: "
               (o/query-all with-mods-session ::records-facts))

      (println "New locations that have never been below 0: "
               (o/query-all with-mods-session ::always-over-zero))

      (let [with-retracted-session (-> with-mods-session
                                       (o/retract 5 ::temperature)
                                       (o/retract 5 ::location)
                                       (o/fire-rules))]

        (println "")
        (println "Now we retract the temperature of -5 at LHR")
        (println "Cold temperatures with this retraction: "
                 (o/query-all with-retracted-session ::cold-temperatures))

        (println "Local temperature records with this retraction: "
                 (o/query-all with-retracted-session ::records-facts))

        (println "Locations that have never been below zero with this retraction"
                 (o/query-all with-retracted-session ::always-over-zero)))))

  (println "\n=======================================")
  (println "End Example with O'Doyle\n\n"))

(comment

  (run-examples)

;; Start Example with O'Doyle
;; Initial cold temperatures:  [{:id 1, :temperature -10} {:id 3, :temperature 20}]
;; Initial local temperature records:  [{:id MCI, :hight 110, :low -10, :loc MCI} {:id LHR, :hight 90, :low 20, :loc LHR}]
;; Initial locations that have never been below 0:  [{:id LHR, :loc LHR, :low 20}]

;; Now add a temperature of -5 to LHR and a temperature of 115 to MCI
;; New cold temperatures:  [{:id 1, :temperature -10} {:id 3, :temperature 20} {:id 5, :temperature -5}]
;; New local temperature records:  [{:id MCI, :hight 115, :low -10, :loc MCI} {:id LHR, :hight 90, :low -5, :loc LHR}]
;; New locations that have never been below 0:  []

;; Now we retract the temperature of -5 at LHR
;; Cold temperatures with this retraction:  [{:id 1, :temperature -10} {:id 3, :temperature 20}]
;; Local temperature records with this retraction:  [{:id MCI, :hight 115, :low -10, :loc MCI} {:id LHR, :hight 90, :low 20, :loc LHR}]
;; Locations that have never been below zero with this retraction [{:id LHR, :loc LHR, :low 20}]

;; =======================================
;; End Example with O'Doyle


  )
