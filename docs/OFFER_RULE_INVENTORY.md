# Offer (Discount/Promotion) Rule Inventory

An engineer-grade inventory of the discount rules the Broadleaf offer engine **actually implements**, derived by
reading the code in `core/broadleaf-framework/src/main/java/org/broadleafcommerce/core/offer/**`. Every claim below
cites the file and method it came from. Where a rule is pinned by an automated test, the test is named.

Paths are relative to `core/broadleaf-framework/src/main/java/org/broadleafcommerce/core/offer/`.
Characterization tests: `core/broadleaf-framework/src/test/java/org/broadleafcommerce/core/offer/service/processor/OfferEngineCharacterizationTest.java`.

---

## 1. How a discount is chosen, end to end

Entry point: `service/OfferServiceImpl.applyAndSaveOffersToOrder(List<Offer>, Order)`. In the standard checkout
pricing workflow that list comes from `OfferServiceImpl.buildOfferListForOrder(Order)`, called by
`pricing/service/workflow/OfferActivity.execute`.

1. **Short circuit.** If a thread-local `OfferContext` exists and `executePromotionCalculation` is false, the order is
   returned untouched (`OfferServiceImpl.applyAndSaveOffersToOrder`).
2. **Wrap the order.** A `PromotableOrder` mirror of the order is built; all pricing math happens on this mirror and is
   copied back at the end (`PromotableItemFactoryImpl.createPromotableOrder`).
3. **Global eligibility filtering**, in this fixed order
   (`processor/AbstractBaseProcessor.filterOffers`): out-of-date offers → time-period rule → request rule → customer
   rule. Filtering happens **before** any pricing math, so a filtered offer can never influence the outcome.
4. **Reset the subtotal** to the sum of item prices *before* adjustments
   (`ItemOfferProcessorImpl.filterOffers` → `PromotableOrder.setOrderSubTotalToPriceWithoutAdjustments`). All
   subsequent minimum-subtotal checks are therefore made against the un-discounted subtotal.
5. **Qualification, split by `OfferType`** (`ItemOfferProcessorImpl.filterOffers`):
   `ORDER` → `OrderOfferProcessorImpl.filterOrderLevelOffer`; `ORDER_ITEM` → `ItemOfferProcessorImpl.filterItemLevelOffer`.
   `FULFILLMENT_GROUP` offers are **not** handled here — they run in a separate later pass (section 8).
   Qualification means: the offer's MVEL rules match, and its qualifying/target item criteria can be satisfied by items
   currently in the cart.
6. **Score item offers.** `ItemOfferProcessorImpl.calculatePotentialSavings` computes, for each candidate item offer *as
   if it were the only offer*, its total potential savings, a per-use savings figure, and a weighted percent. This is
   only done when there is more than one candidate item offer.
7. **Sort item offers** with `discount/ItemOfferComparator` (priority ascending, then potential savings descending).
8. **Try permutations.** With more than one item offer, `determineBestPermutation` applies up to four orderings
   (as-sorted; sorted by per-use savings; sorted by weighted percent; and — only when the first offer is
   totalitarian/non-combinable — the list with totalitarian and non-combinable offers removed), and keeps the ordering
   that produces the **lowest subtotal**.
9. **Apply item offers** (`applyAllItemOffers` → `applyItemOffer`): re-check subtotal requirements, check
   combinability against already-applied adjustments, mark qualifier and target quantities, convert those marks into
   `OrderItemPriceDetailAdjustment`s.
10. **Sale vs retail resolution** per price detail
    (`discount/domain/PromotableOrderItemPriceDetailImpl.chooseSaleOrRetailAdjustments`): whichever of
    "sale price" / "retail price minus adjustments" is cheaper wins; adjustments that lose are discarded.
11. **Apply order offers** (`OrderOfferProcessorImpl.applyAllOrderOffers`) against the subtotal left by step 10.
12. **Order vs item conflict resolution** when a totalitarian offer is in play
    (`OrderOfferProcessorImpl.compareAndAdjustOrderAndItemOffers`): the smaller of "total order adjustments" and
    "total item adjustments" is thrown away entirely.
13. **Re-qualification pass.** If both order and item offers qualified, all order adjustments are removed, each order
    offer is re-tested against the *now-discounted* subtotal, and the survivors are re-applied
    (`ItemOfferProcessorImpl.applyAndCompareOrderAndItemOffers`, final block).
14. **Copy back and save.** `OrderOfferProcessorImpl.synchronizeAdjustmentsAndPrices`, duplicate-adjustment cleanup in
    `OfferServiceImpl.verifyAdjustments`, `order.setSubTotal(...)`, `order.finalizeItemPrices()`, `orderService.save`.
15. **Shipping/fulfillment offers run later and separately** —
    `OfferServiceImpl.applyAndSaveFulfillmentGroupOffersToOrder`, invoked from the fulfillment pricing activity.

---

## 2. Eligibility filters (run before any pricing math)

| # | Decision | Where | Inputs | Precedence / notes |
|---|---|---|---|---|
| 2.1 | Drop offers outside their active dates | `processor/AbstractBaseProcessor.removeOutOfDateOffers` | `offer.startDate`, `offer.endDate`, offer time zone from `OfferTimeZoneProcessor` | First filter to run. **Surprise:** an offer with a `null` startDate is removed (`if (start == null || start.after(current))`) — "no start date" means "never active", not "always active". Pinned by `testOffersOutsideTheirActiveDatesAreDroppedIncludingOffersWithNoStartDate`. |
| 2.2 | Drop offers whose TIME rule fails | `AbstractBaseProcessor.removeTimePeriodOffers` → `couldOfferApplyToTimePeriod` | MVEL rule keyed `OfferRuleType.TIME` in `offer.offerMatchRulesXref` | No rule ⇒ applies. |
| 2.3 | Drop offers whose REQUEST rule fails | `AbstractBaseProcessor.removeInvalidRequestOffers` → `couldOfferApplyToRequestDTO` | MVEL rule keyed `OfferRuleType.REQUEST`, `BroadleafRequestContext.getRequestDTO()` | No rule ⇒ applies. Outside a web request the `requestDTO` is null, so a request rule referencing it can silently fail. |
| 2.4 | Drop offers whose CUSTOMER rule fails | `AbstractBaseProcessor.removeInvalidCustomerOffers` → `couldOfferApplyToCustomer` | MVEL rule keyed `OfferRuleType.CUSTOMER`, the `Customer` | No rule ⇒ applies. |
| 2.5 | Per-customer usage cap | `service/OfferServiceImpl.verifyMaxCustomerUsageThreshold(Order, Offer)` | `offer.isLimitedUsePerCustomer()`, `offer.maxUsesPerCustomer`, `offer.maxUsesStrategyType` (`CUSTOMER` vs account), `offer.minimumDaysPerUsage`, `OfferAuditService` counts | **Surprise:** this is *not* part of the apply pipeline. It is only consulted in `buildOfferListForOrder` for **automatic/global** offers, and in `OrderServiceImpl.addOfferCode` for offer codes. Anything that calls `applyAndSaveOffersToOrder` with a hand-built list bypasses the per-customer cap entirely. |

---

## 3. Qualification rules (which offers become candidates)

| # | Decision | Where | Inputs | Precedence / notes |
|---|---|---|---|---|
| 3.1 | Order-level offer may not be `FIX_PRICE` | `processor/OrderOfferProcessorImpl.filterOrderLevelOffer` | `offer.discountType` | Logged at WARN and silently ignored — the order simply isn't discounted. Pinned by `testFixedPriceOfferSetsTheUnitPriceWhileAFixedPriceOrderOfferIsIgnoredEntirely`. |
| 3.2 | Order rule evaluation | `OrderOfferProcessorImpl.couldOfferApplyToOrder(...)` | MVEL `OfferRuleType.ORDER` rule with vars `order`, `offer`, and optionally `orderItem`/`fulfillmentGroup` | An order offer qualifies if the rule matches the order, **or** any single order item, **or** any fulfillment group. No rule ⇒ qualifies. |
| 3.3 | Item-level qualification | `ItemOfferProcessorImpl.filterItemLevelOffer` | Same MVEL rule evaluated per discountable item, then per (item, fulfillment group) pair | Offers with **no** qualifying/target criteria take a "legacy" path where every matching item is recorded as a candidate target directly. |
| 3.4 | Qualifying item criteria | `AbstractBaseProcessor.couldOfferApplyToOrderItems` → `checkForItemRequirements` | `offer.qualifyingItemCriteriaXref` (MVEL match rule + required quantity) | No qualifying criteria ⇒ qualifier matched by default. Quantities across matching items are summed; requirement is `matchedQuantity >= criteria.quantity`. |
| 3.5 | Target item criteria | `AbstractBaseProcessor.couldOfferApplyToOrderItems` | `offer.targetItemCriteriaXref`, or `offer.offerPriceData` when `useListForDiscounts` is on | With `useListForDiscounts`, matching is by product id / SKU id / product external id / SKU external id (`PromotableOfferUtilityImpl.itemMatchesOfferPriceData`) and the target criteria are ignored. |
| 3.6 | Qualifying item subtotal | `AbstractBaseProcessor.meetsItemQualifierSubtotal` | `offer.qualifyingItemSubTotal` | **Surprise:** an `ORDER_ITEM` offer that sets a qualifying-item subtotal but has *no* qualifying criteria is rejected outright (WARN). Also the comparison here is strict `greaterThan`, whereas the order-level subtotal checks in `OfferServiceUtilitiesImpl` use `greaterThanOrEqual` — an exactly-on-the-threshold cart behaves differently depending on which check applies. |
| 3.7 | Order minimum subtotal | `service/OfferServiceUtilitiesImpl.orderMeetsSubtotalRequirements` | `offer.orderMinSubTotal`, `order.subTotal` | Null or non-positive threshold ⇒ no requirement. Checked at *apply* time (steps 9/11), not at qualification time. Pinned by `testAnOrderOfferIsSkippedWhenTheCartIsBelowItsMinimumSubtotal`. |
| 3.8 | Qualifier / target subtotal requirements | `OfferServiceUtilitiesImpl.orderMeetsQualifyingSubtotalRequirements`, `orderMeetsTargetSubtotalRequirements` | `offer.qualifyingItemSubTotal`, `offer.targetMinSubTotal`, prices **before** adjustments | Sum the matching items' pre-adjustment prices and require `subtotal >= minSubtotal`. |

---

## 4. Ordering and precedence between competing offers

| # | Decision | Where | Inputs | Notes |
|---|---|---|---|---|
| 4.1 | **Priority is the master sort key** | `discount/ItemOfferComparator`, `discount/ItemOfferQtyOneComparator`, `discount/ItemOfferWeightedPercentComparator`, `discount/OrderOfferComparator` | `offer.priority` (Integer) | **Lower number = applied first.** All four comparators compare priority first, so priority cannot be overridden by a bigger discount. Pinned by `testTheLowerPriorityNumberWinsEvenWhenTheOtherOfferWouldSaveMore`. |
| 4.2 | Tie-break: biggest saving | `ItemOfferComparator`, `OrderOfferComparator` | `potentialSavings` computed by `ItemOfferProcessorImpl.calculatePotentialSavings` | Equal priority ⇒ larger potential savings first. Pinned by `testAtEqualPriorityTheAmountOffOfferBeatsThePercentOffOfferOnThisCart`. |
| 4.3 | Alternate tie-breaks used for permutations | `ItemOfferQtyOneComparator` (savings for a single use), `ItemOfferWeightedPercentComparator` (weighted percent; nulls sort last) | `potentialSavingsQtyOne`, `weightedPercentSaved` | `weightedPercentSaved` is the raw percent value for a plain `PERCENT_OFF` offer, a computed weighted percentage when the offer has qualifiers restricted to `NONE`, and otherwise a savings-over-subtotal percentage (`calculatePotentialSavings`). |
| 4.4 | Best-permutation search | `ItemOfferProcessorImpl.determineBestPermutation` / `buildItemOfferPermutations` / `removeDuplicatePermutations` | The three comparators above | The engine tries several orderings and keeps the one with the lowest resulting subtotal — i.e. within a priority band it is *customer-favourable*, but it does **not** brute force all n! orderings, so a better combination can be missed (comment in `buildItemOfferPermutations` says as much). |
| 4.5 | Order-vs-item conflict | `OrderOfferProcessorImpl.compareAndAdjustOrderAndItemOffers` | `calculateOrderAdjustmentTotal()`, `calculateItemAdjustmentTotal()` | Triggered when a totalitarian offer is applied on either side. `orderAdjustmentTotal >= itemAdjustmentTotal` ⇒ item adjustments are dropped; otherwise order adjustments are dropped. **Ties favour the order-level offer.** Pinned by `testATotalitarianOrderOfferAndAnItemOfferNeverBothSurvive`. |
| 4.6 | Re-qualification after item discounts | `ItemOfferProcessorImpl.applyAndCompareOrderAndItemOffers` (final block) | `couldOfferApplyToOrder` re-evaluated against the discounted subtotal | Order offers that no longer qualify after item discounts are dropped. The code comments an acknowledged edge case: an order promotion killed this way might have been the better deal overall. |

---

## 5. Stackability and combinability

Two *different* switches are routinely confused. Both are read straight off `Offer`.

| # | Concept | Field | Where enforced | Effect |
|---|---|---|---|---|
| 5.1 | **"Stackable"** (admin label) | `offer.offerItemTargetRuleType` (`OfferItemRestrictionRuleType`) | `admin/.../OfferCustomPersistenceHandler.buildOfferItemTargetRuleTypeProperty` maps the admin `stackableWithOtherOffers` checkbox to `QUALIFIER_TARGET` (checked) or `NONE` (unchecked); enforced in `discount/domain/PromotableOrderItemPriceDetailImpl.getQuantityAvailableToBeUsedAsTarget` / `getQuantityAvailableToBeUsedAsQualifier` via `restrictTarget` / `restrictQualifier` | Decides whether a *unit* that already received a discount (or was used as a qualifier) may be reused. `NONE` = cannot be reused at all; `QUALIFIER` = reusable as qualifier only; `TARGET` = reusable as target only; `QUALIFIER_TARGET` = reusable as both. There is a `StackabilityType` enum in `service/type/` but **nothing in the codebase references it** — it is dead configuration. Pinned by `testTwoStackableOffersBothDiscountTheSameUnitsButNonStackableOffersDoNot`. |
| 5.2 | **Combinable** | `offer.combinableWithOtherOffers` | Items: `service/OfferServiceUtilitiesImpl.itemOfferCanBeApplied` and `PromotableOrderItemPriceDetailImpl.getQuantityAvailableToBeUsedAsTarget`; Orders: `discount/domain/PromotableOrderImpl.canApplyOrderOffer` + the `break` in `OrderOfferProcessorImpl.applyAllOrderOffers` | A non-combinable offer may only be applied if it is the *first* adjustment of its kind, and once applied no further offers of that kind are applied. Pinned by `testTwoCombinableOrderOffersCompoundWhileANonCombinableOneAppliesAlone`. |
| 5.3 | **Totalitarian** | `offer.totalitarianOffer` | `OfferServiceUtilitiesImpl.itemOfferCanBeApplied`, `PromotableOrderImpl.isTotalitarianOfferApplied`, `OrderOfferProcessorImpl.applyAllOrderOffers` | Behaves like non-combinable *and* additionally forces the order-vs-item comparison in 4.5. |
| 5.4 | Compounding of order offers | `OrderOfferProcessorImpl.applyAllOrderOffers` + `PromotableOrder.calculateSubtotalWithAdjustments` | — | Two combinable order offers **compound**: the second is computed on the subtotal left by the first (10% + 10% on $129.95 = $24.70, not $25.99). Pinned by `testTwoCombinableOrderOffersCompoundWhileANonCombinableOneAppliesAlone`. |

---

## 6. Discount arithmetic

All in `discount/domain/PromotableOfferUtilityImpl.computeAdjustmentValue` unless noted.

| # | Rule | Detail |
|---|---|---|
| 6.1 | `AMOUNT_OFF` | Adjustment = `offer.value` **per unit**, not per order line and not per order. Pinned by `testAtEqualPriorityTheAmountOffOfferBeatsThePercentOffOfferOnThisCart` ($10 off × 5 units = $50). |
| 6.2 | `PERCENT_OFF` | `currentPrice × value / 100`, divided at scale 5 then rounded with `PromotionRounding` (`BroadleafCurrencyUtils`/`BankersRounding` defaults). Rounding is applied **per unit**, so 10% off a $19.99 item is $2.00, and the order-level total is the sum of rounded per-unit values ($13.00 on the $129.95 test cart, not $12.99). |
| 6.3 | `FIX_PRICE` | Adjustment = `currentPrice − offer.value`; i.e. the value is the resulting price, not the discount. Pinned by `testFixedPriceOfferSetsTheUnitPriceWhileAFixedPriceOrderOfferIsIgnoredEntirely`. |
| 6.4 | Never negative | If the computed adjustment exceeds the current price it is capped at the current price. `OfferServiceUtilitiesImpl.applyOrderItemAdjustment` additionally refuses adjustments with a negative value. |
| 6.5 | Sale price participation | `computeSalesAdjustmentValue` returns zero for an item offer with `applyDiscountToSalePrice == false`, so such an offer can only ever beat the retail price. |
| 6.6 | Tiered (`AdvancedOffer`) values | `PromotableOfferUtilityImpl.computeDiscountVariables` picks the tier by sorted `minQuantity`. **Surprise:** if no tier matches, `FIX_PRICE` falls back to an arbitrarily large value and other types fall back to zero — a misconfigured tier silently produces no discount rather than an error. |
| 6.7 | List-price discounts | When `offer.useListForDiscounts` is set and an `OfferPriceData` row matches the item, the discount type/value come from the `OfferPriceData`, overriding the offer's own type/value. |

---

## 7. Quantity, qualifier and target mechanics (item offers)

| # | Decision | Where | Notes |
|---|---|---|---|
| 7.1 | Repeat application | `ItemOfferProcessorImpl.markQualifiersAndTargets` | Loops applying the promotion again and again until qualifiers or targets run out. Number of applications is therefore data-driven, not configured. |
| 7.2 | Which units are chosen | `OfferServiceUtilitiesImpl.markQualifiersForCriteria`, `markTargetsForCriteria`, `sortTargetItemDetails` | Both qualifiers and targets are sorted **most expensive first** (`getPromotableItemComparator`). The most expensive eligible unit is consumed as the qualifier *and* the discount goes to the most expensive eligible target. |
| 7.3 | Qualifiers are consumed, not rewarded | `PromotableOrderItemPriceDetailImpl.getQuantityAvailableToBeUsedAsTarget` (rules 4 & 5) | A unit used as a qualifier is not available as a target unless the restriction rule allows reuse. Pinned by `testAnOfferWithAQualifierOnlyDiscountsTheTargetAndOnlyWhenTheQualifyingItemIsInTheCart`. |
| 7.4 | Max uses per order | `discount/domain/PromotableCandidateItemOfferImpl.calculateMaximumNumberOfUses`, `OfferImpl.isUnlimitedUsePerOrder` | `maxUsesPerOrder == 0` means unlimited. The effective cap is the *minimum* of the offer cap and what the target criteria can support. Pinned by `testMaxUsesPerOrderLimitsHowManyUnitsAnItemOfferCanDiscount`. |
| 7.5 | Related qualifiers/targets | `ItemOfferProcessorImpl.markRelatedQualifiersAndTargets`, `OfferServiceUtilitiesImpl.findRelatedQualifierRoot` | With `offer.requiresRelatedTargetAndQualifiers`, qualifier and target must belong to the same parent/child `OrderItem` tree. |
| 7.6 | Add-on items | `ItemOfferProcessorImpl.calculatePotentialSavingsForOrderItem` | An add-on order item yields zero savings unless `offer.applyToChildItems` is true. |
| 7.7 | Price-detail splitting | `ItemOfferProcessorImpl.applyItemQualifiersAndTargets` → `splitDetailsIfNecessary`, then `mergePriceDetails` | A line of quantity *n* is split so that discounted and undiscounted units can carry different prices, then like details are merged again. This is why an order item can end up with several `OrderItemPriceDetail` rows after pricing. |

---

## 8. Sale price vs retail price

`discount/domain/PromotableOrderItemPriceDetailImpl.chooseSaleOrRetailAdjustments`:

- If the item is on sale, the engine compares "sale price minus sale-eligible adjustments" against "retail price minus
  all adjustments" and keeps the cheaper one.
- If the winning result is **not lower** than the plain sale price, **all adjustments on that detail are cleared** and
  the customer just gets the sale price. Pinned by `testARetailOnlyOfferIsDiscardedForItemsAlreadyOnABetterSalePrice`.
- If sale adjustments win, retail-only adjustments are removed (`removeRetailOnlyAdjustments`); zero-value adjustments
  are always removed (`removeZeroDollarAdjustments`).
- `OfferServiceUtilitiesImpl.adjustmentIsNotGoodEnoughToBeApplied` blocks a retail-only discount up front when an
  existing sale price is already better.

Consequence worth stating to a business audience: a promotion can be fully computed, applied, and then silently
discarded because the item's sale price was better. The adjustment never reaches the order.

---

## 9. Fulfillment-group (shipping) offers

Handled entirely in `processor/FulfillmentGroupOfferProcessorImpl`, driven by
`OfferServiceImpl.applyAndSaveFulfillmentGroupOffersToOrder`, i.e. **after** item and order offers have been priced.

| # | Decision | Where | Notes |
|---|---|---|---|
| 9.1 | Qualification order | `filterFulfillmentGroupLevelOffer` | Order/item MVEL rules first, then the fulfillment-group rule, then item qualifying criteria. |
| 9.2 | Which items may qualify a shipping offer | `getQualifyGroupAcrossAllOrderItems` | Reads system property `promotion.fulfillmentgroup.qualifyAcrossAllOrderItems`, default `false` ⇒ only items inside that fulfillment group can qualify it. Undocumented outside the code. |
| 9.3 | Selection and capping | `applyAllFulfillmentGroupOffers` | Candidates are grouped per offer, sorted by priority then discounted amount, capped by `maxUsesPerOrder`, filtered by qualifying/subtotal requirements, then the surviving "potentials" are sorted by total savings descending **and then re-sorted by priority ascending** (the second sort is stable, so priority wins and savings only break ties). |
| 9.4 | Combinability | `removeTrailingNotCombinableFulfillmentGroupOffers` | Non-combinable shipping offers after the first are dropped. |
| 9.5 | Shipping vs order/item conflict | `compareAndAdjustFulfillmentGroupOffers` | When a totalitarian offer is involved: if order+item savings are **greater than or equal to** shipping savings, the shipping adjustments are removed; otherwise the order/item adjustments are removed. Again ties do not favour shipping. |

---

## 10. Post-processing safety nets

| # | Decision | Where | Notes |
|---|---|---|---|
| 10.1 | Duplicate-adjustment scrub | `OfferServiceImpl.verifyAdjustments` | Two adjustments from the same offer id on one price detail: the later one is deleted. Run both before and after save — the fact that this exists implies collisions were observed in practice. |
| 10.2 | Copy back to the order | `OrderOfferProcessorImpl.synchronizeAdjustmentsAndPrices` | Order adjustments, item adjustments and fulfillment groups are synchronised from the promotable mirror onto the persistent entities. |
| 10.3 | Final subtotal | `OfferServiceImpl.applyAndSaveOffersToOrder` | `order.setSubTotal(order.calculateSubTotal())` then `order.finalizeItemPrices()`. |

---

## 11. Behaviour that is surprising or undocumented

1. **A `null` start date disables an offer** (2.1) — the natural reading ("no start date = always on") is wrong.
2. **`StackabilityType` is dead code.** "Stackable" in the admin is stored as `offerItemTargetRuleType` (5.1).
3. **`AMOUNT_OFF` on an item offer is per unit** (6.1) — "$10 off" on a 5-unit cart is $50.
4. **`FIX_PRICE` order offers are silently ignored**, only logged at WARN (3.1).
5. **Percent-off rounds per unit**, so the order-level discount is not `round(subtotal × pct)` (6.2).
6. **Ties in every order-vs-item / shipping-vs-order comparison favour the non-item side** (4.5, 9.5).
7. **Priority beats value unconditionally** (4.1) — a mis-set priority silently suppresses a better promotion.
8. **The engine does not exhaustively search offer combinations** (4.4); it evaluates at most four orderings.
9. **Per-customer usage limits are enforced outside the apply pipeline** (2.5).
10. **Mixed strict/non-strict subtotal comparisons** (`greaterThan` in `meetsItemQualifierSubtotal` vs
    `greaterThanOrEqual` in `OfferServiceUtilitiesImpl`) mean an exactly-on-threshold cart can go either way (3.6).
11. **Misconfigured tiers fail silently to a zero (or absurdly large) discount** (6.6).
12. **A fully computed discount can be discarded at the last moment** by the sale-price comparison (section 8).

---

## 12. Ambiguities I could not resolve from the code alone

1. **Intended semantics of a null start date** — is 2.1 deliberate or a latent bug? Nothing in the code or comments says.
2. **Why ties favour order-level offers** (4.5) — no comment or test explains the `>=`; it may be arbitrary.
3. **`StackabilityType`** — whether it is deprecated-but-kept for data compatibility or simply forgotten.
4. **`OfferProrationType`** — declared on `domain/AdvancedOffer`, but I found no code in this repository that reads it
   while pricing; proration presumably lives in a commercial module.
5. **Extension-manager overrides** — `OfferServiceExtensionManager` can stop item-offer processing, change potential
   savings, and alter sale/retail selection. In this open-source repo no handler is registered, so the effective rules
   above hold only for a deployment with no custom handlers.
6. **`useQtyOnlyTierCalculation`** (`PromotableCandidateItemOfferImpl.calculateTargetQuantityForTieredOffer`) — the two
   branches produce different target quantities; the business intent of each is not documented.
7. **Time zone selection** — `OfferTimeZoneProcessor` is an injected strategy; which time zone production actually uses
   (and hence exact activation boundaries) cannot be determined from this module.
8. **`minimumDaysPerUsage`** — it is passed to `OfferAuditService.countUsesByCustomer`, but the interpretation lives in
   the DAO/audit implementation and is not stated in the offer module.
9. **Interaction between `useListForDiscounts` (`OfferPriceData`) and target criteria** — the code short-circuits to
   fixed targets, but whether an offer is *supposed* to be configured with both is unclear.

---

## 13. Coverage status of the offer package

Measured with the repository's own JaCoCo configuration, over
`org.broadleafcommerce.core.offer.**` in `core/broadleaf-framework`, running only the offer test suite
(`-Dtest='*Offer*Test'`):

| Scope | Lines before | Lines after | Branches before | Branches after |
|---|---|---|---|---|
| `org.broadleafcommerce.core.offer.**` | 47.75% | 48.02% | 38.10% | 39.04% |
| `...offer.service.processor` | 78.55% | 79.12% | 63.43% | 65.12% |
| `...offer.service` | 42.70% | 43.02% | 36.34% | 38.95% |
| `...offer.service.discount.domain` | 73.83% | 74.09% | 60.54% | 61.07% |

The new tests are deliberately end-to-end through `OfferServiceImpl.applyAndSaveOffersToOrder`, so the coverage they
add is concentrated in the decision branches that actually move money: `AbstractBaseProcessor` 58.9% → 61.1% branches,
`ItemOfferProcessorImpl` 66.8% → 69.1%, `OfferServiceUtilitiesImpl` 45.2% → 48.9%, `OrderOfferProcessorImpl`
60.6% → 62.0%, `PromotableOrderItemPriceDetailImpl` 78.9% → 80.1%.
