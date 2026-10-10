package com.shopsmart.assistant.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentRouterTest {

    @Test
    void routesTheFourIntents() {
        assertEquals(IntentRouter.SHOPPING, IntentRouter.rules("I need a laptop under ₹100000 for gaming"));
        assertEquals(IntentRouter.POLICY, IntentRouter.rules("Can I return a laptop after 20 days?"));
        assertEquals(IntentRouter.ORDER, IntentRouter.rules("Where is order SS12345678?"));
        assertEquals(IntentRouter.SUPPORT, IntentRouter.rules("My headphones arrived broken"));
        assertEquals(IntentRouter.OTHER, IntentRouter.rules("hello"));
    }

    @Test
    void orderWithPolicyPartStaysOrder() {
        assertEquals(IntentRouter.ORDER, IntentRouter.rules("Where is my order SS12345678 and can I cancel it after shipment?"));
    }
}
