(ns merchandising.governor
  "RetailMerchandisingGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every restock operation an advisor may propose for a shop. The
  governor never dispatches hardware itself and never changes a price
  tag above the shop's registered price-authorization ceiling.
  Modeled on cloud-itonami-isco-4311's bookkeeping.governor. Task
  twist: a proposed price-change amount is an arithmetic ceiling
  against the shop's registered price-authorization ceiling, and a
  restock cannot be logged complete until the shop's planogram
  compliance has been checked.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance   — the independent retailer/small grocery
                             chain must be registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never dispatches hardware and
                             never changes a price tag above the
                             registered authorization ceiling; it
                             only gates what the advisor may
                             restock/reprice).
    3. shop basis          — a restock proposal must cite a
                             REGISTERED shop belonging to this
                             client.
    4. price-authorization ceiling — the proposed price-change amount
                             must not exceed the shop's registered
                             `:max-price-authorization` (changing a
                             price tag beyond the shop's registered
                             ceiling is an unauthorized price change,
                             not routine merchandising).
    5. planogram-compliance checked — the shop must have
                             `:planogram-compliance-checked?` true
                             before any restock can be logged complete
                             (logging without a compliance check is an
                             unverified restock, not efficient
                             service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-authorization-price-change (no price-tag
                             change above the shop's registered
                             price-authorization ceiling without the
                             governor gate).
    7. :op :approve-inventory-write-off (writing off damaged/expired
                             inventory always requires human
                             sign-off).
    8. low confidence (< `confidence-floor`)."
  (:require [merchandising.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-authorization-price-change
                                     :approve-inventory-write-off})

(defn- hard-violations [{:keys [request proposal]} client-record sh]
  (let [{:keys [op price-change-amount]} proposal
        restock? (= :approve-restock-operation op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録上限超過の値札変更を直接実行しない）"})

      (and restock? (nil? sh))
      (conj {:rule :unknown-shop :detail "未登録 shop への補充提案は不可"})

      (and restock? sh (not= (:client-id sh) (:client-id request)))
      (conj {:rule :shop-wrong-client :detail "shop が別 client のもの"})

      (and restock? sh (number? price-change-amount) (> price-change-amount (:max-price-authorization sh)))
      (conj {:rule :price-change-exceeds-authorization
             :detail (str "値札変更額 " price-change-amount " > 登録済み権限上限 "
                          (:max-price-authorization sh) "（登録権限上限を超える値札変更は無許可価格変更であって通常のマーチャンダイジングではない）")})

      (and restock? sh (not (:planogram-compliance-checked? sh)))
      (conj {:rule :planogram-compliance-not-checked
             :detail "プラノグラム準拠チェック未完了の補充完了報告は未検証の補充であって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `merchandising.store/Store`. Pure — never
  mutates the store, never changes a price tag above the registered
  authorization ceiling."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        sh (some->> (:shop-id proposal) (store/shop store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record sh)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
