package com.example.ecommerce.payment.stripe;

import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;

final class StripePaymentSupport {

    private StripePaymentSupport() {}

    static String extractLast4(PaymentIntent intent) {
        PaymentMethod paymentMethod = intent.getPaymentMethodObject();
        if (paymentMethod != null && paymentMethod.getCard() != null) {
            return paymentMethod.getCard().getLast4();
        }
        return null;
    }
}
