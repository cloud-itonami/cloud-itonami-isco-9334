(ns merchandising.advisor
  "Merchandising Advisor — the advisor named in this repository's
  README, proposing a retail-merchandising operation (a restock
  operation, approve an over-authorization price change, approve an
  inventory write-off) from a store planogram, inventory feed and
  restock schedule. Swappable mock/llm; the advisor ONLY proposes —
  `merchandising.governor` checks the price-authorization ceiling and
  planogram-compliance completion independently and always escalates
  over-authorization-price-change and inventory-write-off decisions.
  Modeled on cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-restock-operation|:approve-over-authorization-price-change|:approve-inventory-write-off
               :effect :propose :shop-id str :price-change-amount
               number :stake kw :confidence n :rationale str}. The
  price-authorization ceiling and planogram-compliance state live on
  the registered shop record itself (see `merchandising.store`), not
  on the proposal.")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake shop-id price-change-amount] :as request}]
  {:op op
   :effect :propose
   :shop-id shop-id
   :price-change-amount price-change-amount
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a retail-merchandising advisor. Given a request, propose an
   :op, the :shop-id and :price-change-amount, an honest :confidence
   and a :stake. Never propose a price-change amount beyond the shop's
   registered authorization ceiling, or a restock completion for a
   shop whose planogram compliance hasn't been checked — the governor
   checks both against the registered shop record. Over-authorization
   price changes and inventory write-offs always require human
   sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
