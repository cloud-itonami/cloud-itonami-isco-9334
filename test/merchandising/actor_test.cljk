(ns merchandising.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [merchandising.actor :as actor]
            [merchandising.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Retail"})
    (store/register-shop! st {:shop-id "SH-1" :client-id "client-1"
                              :name "shop-042"
                              :max-price-authorization 50
                              :planogram-compliance-checked? true})
    st))

(deftest commits-a-within-authorization-checked-restock
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-restock-operation :stake :low
                 :shop-id "SH-1" :price-change-amount 20}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-authorization-restock
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-restock-operation :stake :low
                 :shop-id "SH-1" :price-change-amount 500}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-authorization-price-change-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-authorization-price-change :stake :low
                 :shop-id "SH-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
