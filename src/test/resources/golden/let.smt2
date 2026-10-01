; let is inlined; the second clause needs capture avoidance
(set-logic HORN)
(declare-fun P (Int Int) Bool)
(assert (forall ((x Int)) (let ((y (+ x 1))) (=> (> y 0) (P x y)))))
(assert (forall ((x Int)) (let ((y x)) (=> (P x y) (exists ((x Int)) (> x y))))))
(check-sat)
