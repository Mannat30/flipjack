/*
 * Copyright 2026 Kamran Zafar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kamranzafar.flipjack;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors the README usage examples (with simplified types) and checks every table row.
 */
class ReadmeExamplesTest {

    static final class Features {
        static final String NEW_PRICING = "new-pricing";
        static final String PREMIUM = "premium";
        static final String PDF_EXPORT = "pdf-export";
        static final String FAST_CHECKOUT = "fast-checkout";
        static final String NEW_REFUNDS = "new-refunds";
        static final String ORDER_EMAILS = "order-emails";
        static final String CHECKOUT_V2 = "checkout-v2";
        static final String EXPRESS_SHIPPING = "express-shipping";
        static final String REGIONAL_TAX = "regional-tax";
    }

    // ---- class level: replace ----------------------------------------------------

    public interface PricingService {
        String quote(String cart);

        String discount(String cart);
    }

    public static class LegacyPricing implements PricingService {
        public String quote(String cart) {
            return "legacy quote";
        }

        public String discount(String cart) {
            return "legacy discount";
        }
    }

    @Toggleable(value = Features.NEW_PRICING, fallback = LegacyPricing.class)
    public static class NewPricing implements PricingService {
        public String quote(String cart) {
            return "new quote";
        }

        public String discount(String cart) {
            return "new discount";
        }
    }

    @Test
    void classLevelReplace() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        PricingService pricing = ToggledFeature.of(PricingService.class, new NewPricing(), new LegacyPricing(), toggles);

        toggles.enable(Features.NEW_PRICING);
        assertEquals("new quote", pricing.quote("c"));
        assertEquals("new discount", pricing.discount("c"));
        toggles.disable(Features.NEW_PRICING);
        assertEquals("legacy quote", pricing.quote("c"));
        assertEquals("legacy discount", pricing.discount("c"));
    }

    // ---- class level: decorator --------------------------------------------------

    public interface Greeting {
        String greet(String name);
    }

    public static class BasicGreeting implements Greeting {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    @Toggleable(value = Features.PREMIUM, fallback = BasicGreeting.class)
    public static class PremiumGreeting implements Greeting {
        private final Greeting basic;

        public PremiumGreeting(BasicGreeting basic) {
            this.basic = basic;
        }

        public String greet(String name) {
            return basic.greet(name) + ", welcome back!";
        }
    }

    @Test
    void classLevelDecorator() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        BasicGreeting basic = new BasicGreeting();
        Greeting greeting = ToggledFeature.of(Greeting.class, new PremiumGreeting(basic), basic, toggles);

        toggles.enable(Features.PREMIUM);
        assertEquals("Hello Sam, welcome back!", greeting.greet("Sam"));
        toggles.disable(Features.PREMIUM);
        assertEquals("Hello Sam", greeting.greet("Sam"));
    }

    // ---- class level: no fallback ------------------------------------------------

    public interface ReportExporter {
        Optional<String> exportPdf(String report);
    }

    @Toggleable(value = Features.PDF_EXPORT, whenDisabled = WhenDisabled.NO_OP)
    public static class PdfExporter implements ReportExporter {
        public Optional<String> exportPdf(String report) {
            return Optional.of("pdf");
        }
    }

    public static class PlainPdfExporter implements ReportExporter {
        public Optional<String> exportPdf(String report) {
            return Optional.of("pdf");
        }
    }

    @Test
    void classLevelNoFallback() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        ReportExporter noOp = ToggledFeature.of(ReportExporter.class, new PdfExporter(), null, toggles);
        assertEquals(Optional.empty(), noOp.exportPdf("r"));

        ReportExporter throwing = ToggledFeature.builder(ReportExporter.class)
                .feature(Features.PDF_EXPORT).enabled(new PlainPdfExporter()).toggles(toggles).build();
        assertThrows(FeatureDisabledException.class, () -> throwing.exportPdf("r"));

        ReportExporter builderNoOp = ToggledFeature.builder(ReportExporter.class)
                .feature(Features.PDF_EXPORT).enabled(new PlainPdfExporter())
                .whenDisabled(WhenDisabled.NO_OP).toggles(toggles).build();
        assertEquals(Optional.empty(), builderNoOp.exportPdf("r"));

        toggles.enable(Features.PDF_EXPORT);
        assertEquals(Optional.of("pdf"), noOp.exportPdf("r"));
        assertEquals(Optional.of("pdf"), throwing.exportPdf("r"));
    }

    // ---- method level --------------------------------------------------------------

    public interface Orders {
        String checkout(String cart);

        String refund(String id);

        void sendConfirmation(String id);

        String find(String id);
    }

    public static class LegacyOrders implements Orders {
        public String checkout(String cart) {
            return "legacy checkout";
        }

        public String refund(String id) {
            return "legacy refund";
        }

        public void sendConfirmation(String id) {
        }

        public String find(String id) {
            return "legacy find";
        }
    }

    public static class OrderService implements Orders {
        final List<String> sent = new ArrayList<>();

        @Toggleable(value = Features.FAST_CHECKOUT, fallbackMethod = "standardCheckout")
        public String checkout(String cart) {
            return "fast checkout";
        }

        private String standardCheckout(String cart) {
            return "standard checkout";
        }

        @Toggleable(value = Features.NEW_REFUNDS, fallback = LegacyOrders.class)
        public String refund(String id) {
            return "new refund";
        }

        @Toggleable(value = Features.ORDER_EMAILS, whenDisabled = WhenDisabled.NO_OP)
        public void sendConfirmation(String id) {
            sent.add(id);
        }

        public String find(String id) {
            return "find";
        }
    }

    @Test
    void methodLevel() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        OrderService service = new OrderService();
        Orders orders = ToggledFeature.builder(Orders.class)
                .enabled(service)
                .fallbackResolver(FallbackResolver.of(new LegacyOrders()))
                .toggles(toggles)
                .build();

        assertEquals("standard checkout", orders.checkout("c"));
        assertEquals("legacy refund", orders.refund("1"));
        orders.sendConfirmation("1");
        assertEquals(List.of(), service.sent);
        assertEquals("find", orders.find("1"));

        toggles.enable(Features.FAST_CHECKOUT).enable(Features.NEW_REFUNDS).enable(Features.ORDER_EMAILS);
        assertEquals("fast checkout", orders.checkout("c"));
        assertEquals("new refund", orders.refund("1"));
        orders.sendConfirmation("1");
        assertEquals(List.of("1"), service.sent);
        assertEquals("find", orders.find("1"));
    }

    public interface Reports {
        @Toggleable(value = Features.PDF_EXPORT, whenDisabled = WhenDisabled.NO_OP)
        Optional<String> pdf(String id);

        String csv(String id);
    }

    public static class ReportService implements Reports {
        public Optional<String> pdf(String id) {
            return Optional.of("pdf");
        }

        public String csv(String id) {
            return "csv";
        }
    }

    @Test
    void methodLevelOnInterface() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        Reports reports = ToggledFeature.builder(Reports.class).enabled(new ReportService()).toggles(toggles).build();
        assertEquals(Optional.empty(), reports.pdf("1"));
        assertEquals("csv", reports.csv("1"));
        toggles.enable(Features.PDF_EXPORT);
        assertEquals(Optional.of("pdf"), reports.pdf("1"));
    }

    // ---- both levels -----------------------------------------------------------------

    public interface Checkout {
        String pay(String cart);

        String ship(String order);
    }

    public static class CheckoutV1 implements Checkout {
        public String pay(String cart) {
            return "v1 pay";
        }

        public String ship(String order) {
            return "v1 ship";
        }
    }

    @Toggleable(value = Features.CHECKOUT_V2, fallback = CheckoutV1.class)
    public static class CheckoutV2 implements Checkout {
        public String pay(String cart) {
            return "v2 pay";
        }

        @Toggleable(value = Features.EXPRESS_SHIPPING, fallbackMethod = "standardShipping")
        public String ship(String order) {
            return "v2 express";
        }

        private String standardShipping(String order) {
            return "v2 standard";
        }
    }

    @Test
    void bothLevels() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        Checkout checkout = ToggledFeature.of(Checkout.class, new CheckoutV2(), new CheckoutV1(), toggles);

        toggles.disable(Features.CHECKOUT_V2).enable(Features.EXPRESS_SHIPPING);
        assertEquals("v1 pay", checkout.pay("c"));
        assertEquals("v1 ship", checkout.ship("o"));
        toggles.disable(Features.CHECKOUT_V2).disable(Features.EXPRESS_SHIPPING);
        assertEquals("v1 ship", checkout.ship("o"));

        toggles.enable(Features.CHECKOUT_V2).disable(Features.EXPRESS_SHIPPING);
        assertEquals("v2 pay", checkout.pay("c"));
        assertEquals("v2 standard", checkout.ship("o"));

        toggles.enable(Features.EXPRESS_SHIPPING);
        assertEquals("v2 pay", checkout.pay("c"));
        assertEquals("v2 express", checkout.ship("o"));
    }

    // ---- per-user toggles --------------------------------------------------------

    public static class UserTargetedToggles implements FeatureToggles {
        private final Map<String, Set<String>> usersByFeature;

        public UserTargetedToggles(Map<String, Set<String>> usersByFeature) {
            this.usersByFeature = usersByFeature;
        }

        @Override
        public boolean isEnabled(String feature) {
            return false;
        }

        @Override
        public boolean isEnabled(String feature, FeatureContext context) {
            return context.subject()
                    .map(user -> usersByFeature.getOrDefault(feature, Set.of()).contains(user))
                    .orElse(false);
        }
    }

    @Test
    void perUserToggles() {
        ThreadLocal<FeatureContext> currentUser = ThreadLocal.withInitial(() -> FeatureContext.EMPTY);
        PricingService pricing = ToggledFeature.builder(PricingService.class)
                .enabled(new NewPricing())
                .fallback(new LegacyPricing())
                .toggles(new UserTargetedToggles(Map.of(Features.NEW_PRICING, Set.of("alice"))))
                .context(currentUser::get)
                .build();

        currentUser.set(FeatureContext.of("alice").with("tenant", "acme"));
        assertEquals("new quote", pricing.quote("c"));
        currentUser.set(FeatureContext.of("bob"));
        assertEquals("legacy quote", pricing.quote("c"));
        currentUser.set(FeatureContext.EMPTY);
        assertEquals("legacy quote", pricing.quote("c"));
    }

    // ---- inside a method: toggle one step ----------------------------------------

    public interface TaxRule {
        long tax(String cart);
    }

    public static class CheckoutService {
        private final TaxRule tax;

        public CheckoutService(FeatureToggles toggles) {
            this.tax = ToggledFeature.either(TaxRule.class, Features.REGIONAL_TAX,
                    this::regionalTax, this::flatTax, toggles);
        }

        public long total(String cart) {
            return 100 + tax.tax(cart);
        }

        private long regionalTax(String cart) {
            return 20;
        }

        private long flatTax(String cart) {
            return 10;
        }
    }

    @Test
    void stepInsideAMethod() {
        InMemoryFeatureToggles toggles = new InMemoryFeatureToggles();
        CheckoutService checkout = new CheckoutService(toggles);

        toggles.enable(Features.REGIONAL_TAX);
        assertEquals(120, checkout.total("c"));
        toggles.disable(Features.REGIONAL_TAX);
        assertEquals(110, checkout.total("c"));
        assertEquals(Set.of(Features.REGIONAL_TAX), ToggledFeature.featuresUsedBy(checkout));
    }
}
