(in-ns 'csneps.core.build)

(declare lattice-insert submit-assertion-to-channels build-quantterm-channels adjust-type whquestion-term?)


(defn unassert
  "Unasserts the proposition prop in the given context and all ancestor contexts."
  [prop & [ctx]]
  ;; Currently there is no belief revision,
  ;;    so propositions derived using prop might still be asserted,
  ;;    and prop, itself, might be rederivable.
  (let [cntx (or ctx (ct/currentContext))
        p (build prop :Proposition {} #{})]
    (loop [context (ct/asserted? p cntx)]
      (when context
        (ct/remove-from-context p context)
        (recur (ct/asserted? p cntx))))))

(defn variable-parse-and-build
  "Given a top-level build expression, checks that expression for
   variable terms syntax (e.g., every, some). These terms are built and
   a new build expression is created with just the variable-labels
   (e.g., x in (every x ...)). This and a substitution between
   variable-labels and the AI and IO is provided to build. Also asserts into
   the Base KB the restriction sets. Returns the built expression."
  [expr type properties]
  (let [[new-expr vars substitution] (check-and-build-variables expr)]
    (doseq [v (seq vars)]
      (doseq [rst (seq (@restriction-set v))]
        (when-not (whquestion-term? rst)                    ;; It doesn't make sense to assert a WhQuestion.
          (assert rst (ct/find-context 'OntologyCT))))
      (build-quantterm-channels v))
    ;(when (= (syntactic-type-of v) :csneps.core/Arbitrary) (lattice-insert v)))
    (build new-expr type substitution properties)))

(defn unassert-hyp
  [hyps cntxt]
  (loop [current-count 0]
    (when (< current-count (count hyps))
      (println "["current-count"]: " (get hyps current-count))
      (recur (inc current-count))
      )
    )

  (let [choice (Integer/parseInt (read-line))]
    (unassert (get hyps choice) cntxt)
    (let [coll hyps
          i choice]
      (concat (subvec coll 0 i)
              (subvec coll (inc i)))
      )
    )


  )

(defn unassert-cycle
  "Present user the propositions to be unasserted, remove the chosen one from the list, cycle through until they quit"
  [hyps cntxt]
  (println "Press 'c' to remove a hypothesis or press 'q' to quit. The hypotheses are: ")
  (let [current-choice (read-line)]
    (case current-choice
      "c" (let [new-hyps (vec (unassert-hyp hyps cntxt))]
            (if (> (count new-hyps) 0)
              (unassert-cycle new-hyps cntxt)
              )
            )
      "q" (println "good bye!")


      )

    )
  )

(defn begin-belief-revision
  "Gather hypotheses in the current context into a vector so that they can be quickly cycled through for unassertion."
  [cntxt]
  (unassert-cycle (vec (ct/hyps-br cntxt)) cntxt)
  )

(defn build-variable
  "This function should only be called when a single variable needs to be built
   independent of an assert. It is in assert because variable nodes need to assert
   their restriction sets. Returns the variable node built."
  [var-expr]
  (let [[new-expr vars substitution] (check-and-build-variables var-expr)]
    (doseq [rst (seq (@restriction-set (first vars)))]
      (assert rst (ct/find-context 'OntologyCT)))
    (build-quantterm-channels (first vars))
    (first vars)))


(defn check-contradiction
  "Raise a warning if the newly asserted proposition, p
   constitutes a contradiction in the given context."
  [p context]
  (let [negs (findfrom p (slot/find-slot 'nor))]
    (doseq [n negs]
      (when (ct/asserted? n context)
        (println "Warning:" p "and" n "contradict! Entering manual belief revision...")
        (begin-belief-revision context)
        )))

  (let [negs (findto p (slot/find-slot 'nor))]
    (doseq [n negs]
      (when (ct/asserted? n context)
        (println "Warning:" p "and" n "contradict! Entering manual belief revision...")
        (begin-belief-revision context)
        ))))

(defmulti assert
          (fn [expr context] [(type-of expr)]))

(defmethod assert
  [clojure.lang.Symbol] [expr context]
  (assert (build expr :Proposition {} #{}) context))

(defmethod assert
  [java.lang.Integer] [expr context]
  (assert (build expr :Proposition {} #{}) context))

(defmethod assert
  [java.lang.Double] [expr context]
  (assert (build expr :Proposition {} #{}) context))

(defmethod assert
  [java.lang.String] [expr context]
  (assert (build expr :Proposition {} #{}) context))

(defmethod assert
  [clojure.lang.PersistentList] [expr context]
  (assert (variable-parse-and-build expr :Proposition #{}) context))

(defmethod assert
  [clojure.lang.Cons] [expr context]
  (assert (variable-parse-and-build (seq expr) :Proposition #{}) context))

(defmethod assert
  [clojure.lang.PersistentVector] [expr context]
  (assert (variable-parse-and-build (seq expr) :Proposition #{}) context))

(defmethod assert
  [clojure.lang.PersistentHashSet] [expr context]
  (set (map #(assert % context) expr)))

(defn assert-term
  [expr context]
  (clojure.core/assert (not (whquestion-term? expr)) "Cannot assert a WhQuestion.")

  (let [ct (csneps.core.contexts/find-context context)]
    (when-not (ct/asserted? expr ct)
      (ct/hypothesize expr ct)
      (adjust-type expr (st/semantic-type-of expr) :Proposition)
      (submit-assertion-to-channels expr))
    (check-contradiction expr ct))
  expr)

(defmethod assert
  ;[:Proposition] [expr context origintag]
  [:csneps.core/Term] [expr context]
  (clojure.core/assert (not (whquestion-term? expr)) "Cannot assert a WhQuestion.")
  (let [ct (csneps.core.contexts/find-context context)]
    (when-not (ct/asserted? expr ct)
      (ct/hypothesize expr ct)
      (adjust-type expr (st/semantic-type-of expr) :Proposition)
      (submit-assertion-to-channels expr))
    (check-contradiction expr ct))
  expr)


(defn add-to-context
  "Adds the term to the context's hyps."
  [term ctx]
  (ct/hypothesize (build term :Proposition {} #{}) ctx))