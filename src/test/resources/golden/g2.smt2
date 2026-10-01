(set-logic HORN)
(declare-fun Q (Int Int) Bool)
(declare-fun P (Int) Bool)
(assert (forall ((x Int) (y Int)) (=> (and (P x) (> x 0)) (Q x y))))
