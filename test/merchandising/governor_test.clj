(ns merchandising.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [merchandising.store :as store]
            [merchandising.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Retail"})
    (store/register-shop! st {:shop-id "SH-1" :client-id "client-1"
                              :name "shop-042"
                              :max-price-authorization 50
                              :planogram-compliance-checked? true})
    st))

(defn- restock-op [amount]
  {:op :approve-restock-operation :effect :propose :shop-id "SH-1"
   :price-change-amount amount :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-authorization-and-checked
  (let [st (fresh-store)
        v (governor/check req {} (restock-op 20) st)]
    (is (:ok? v))))

(deftest ok-at-exact-authorization-boundary
  (testing "the price-authorization ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (restock-op 50) st)]
      (is (:ok? v)))))

(deftest hard-on-price-change-exceeds-authorization
  (testing "changing a price tag beyond the shop's registered ceiling is an unauthorized price change, not routine merchandising"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (restock-op 500) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :price-change-exceeds-authorization (:rule %)) (:violations v))))))

(deftest hard-on-planogram-compliance-not-checked
  (testing "logging a restock complete without a planogram compliance check is an unverified restock, not efficient service"
    (let [st (store/mem-store)]
      (store/register-client! st {:client-id "client-1" :name "Kobo Retail"})
      (store/register-shop! st {:shop-id "SH-1" :client-id "client-1"
                                :name "shop-042"
                                :max-price-authorization 50
                                :planogram-compliance-checked? false})
      (let [v (governor/check req {} (assoc (restock-op 20) :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :planogram-compliance-not-checked (:rule %)) (:violations v)))))))

(deftest hard-on-unknown-shop
  (let [st (fresh-store)
        v (governor/check req {} (assoc (restock-op 20) :shop-id "SH-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-shop (:rule %)) (:violations v)))))

(deftest hard-on-foreign-shop
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (restock-op 20) st)]
      (is (:hard? v))
      (is (some #(= :shop-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (restock-op 20) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (restock-op 20) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-authorization-price-change-even-at-high-confidence
  (testing "no price-tag change above the shop's registered price-authorization ceiling without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-authorization-price-change :effect :propose
                                    :shop-id "SH-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-inventory-write-off-even-at-high-confidence
  (testing "writing off damaged/expired inventory always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-inventory-write-off :effect :propose
                                    :shop-id "SH-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (restock-op 20) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
