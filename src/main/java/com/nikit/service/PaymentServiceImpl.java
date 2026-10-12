package com.nikit.service;

import com.nikit.domain.PaymentMethod;
import com.nikit.domain.PaymentOrderStatus;
import com.nikit.modal.PaymentOrder;
import com.nikit.modal.User;
import com.nikit.repository.PaymentOrderRepository;
import com.nikit.response.PaymentResponse;
import com.razorpay.Payment;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentServiceImpl implements PaymentService{

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Value("${stripe.api.key}")
    private String stripeSecretKey;

    @Value("${razorpay.api.key}")
    private String apiKey;

    @Value("${razorpay.api.secret}")
    private String apiSecretkey;

    @Value("${app.frontend.url}")
    private String frontendUrl;


    @Override
    public PaymentOrder createOrder(User user, Long amount,
                                    PaymentMethod paymentMethod) {
        if (amount == null || amount <= 0){
            throw new IllegalArgumentException("Amount must be positive");
        }

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setUser(user);
        paymentOrder.setAmount(amount);
        paymentOrder.setPaymentMethod(paymentMethod);
        paymentOrder.setStatus(PaymentOrderStatus.PENDING);

        return paymentOrderRepository.save(paymentOrder);
    }

    @Override
    public PaymentOrder getPaymentOrderById(Long id) throws Exception {
        return paymentOrderRepository.findById(id)
                .orElseThrow(()->new IllegalArgumentException("Payment order not found"));
    }

    /**
     * Verifies the payment with the provider and marks the order SUCCESS/FAILED.
     * Returns true only the first time a genuinely paid order is processed.
     */
    @Override
    public Boolean proceedPaymentOrder(PaymentOrder paymentOrder, String paymentId) throws RazorpayException, StripeException {
        if (paymentOrder.getStatus() == null){
            paymentOrder.setStatus(PaymentOrderStatus.PENDING);
        }
        if (!paymentOrder.getStatus().equals(PaymentOrderStatus.PENDING)){
            return false;
        }
        if (paymentId == null || paymentId.isBlank()){
            throw new IllegalArgumentException("Missing payment id");
        }
        if (paymentOrderRepository.existsByProviderPaymentId(paymentId)){
            throw new IllegalArgumentException("Payment has already been used");
        }

        long expectedMinorUnits = paymentOrder.getAmount() * 100;
        boolean paid;

        if (paymentOrder.getPaymentMethod().equals(PaymentMethod.RAZORPAY)){
            RazorpayClient razorpay = new RazorpayClient(apiKey, apiSecretkey);
            Payment payment = razorpay.payments.fetch(paymentId);

            Number amount = payment.get("amount");
            String status = payment.get("status");

            paid = "captured".equals(status) && amount.longValue() == expectedMinorUnits;
        } else {
            Stripe.apiKey = stripeSecretKey;
            Session session = Session.retrieve(paymentId);

            paid = "paid".equals(session.getPaymentStatus())
                    && String.valueOf(paymentOrder.getId()).equals(session.getClientReferenceId())
                    && session.getAmountTotal() != null
                    && session.getAmountTotal() == expectedMinorUnits;
        }

        paymentOrder.setProviderPaymentId(paymentId);
        paymentOrder.setStatus(paid ? PaymentOrderStatus.SUCCESS : PaymentOrderStatus.FAILED);
        paymentOrderRepository.save(paymentOrder);
        return paid;
    }

    @Override
    public PaymentResponse createRazorpayPaymentLink(User user, Long amount, Long orderId) throws RazorpayException {
        // Razorpay expects the amount in the smallest currency unit (paise)
        long amountInPaise = amount*100;

        try {
            RazorpayClient razorpay = new RazorpayClient(apiKey, apiSecretkey);

            JSONObject paymentLinkRequest = new JSONObject();
            paymentLinkRequest.put("amount", amountInPaise);
            paymentLinkRequest.put("currency", "INR");

            JSONObject customer = new JSONObject();
            customer.put("name", user.getFullName());

            customer.put("email", user.getEmail());
            paymentLinkRequest.put("customer", customer);

            JSONObject notify = new JSONObject();
            notify.put("email", true);
            paymentLinkRequest.put("notify", notify);

            paymentLinkRequest.put("reminder_enable", true);

            paymentLinkRequest.put("callback_url", frontendUrl+"/wallet?order_id="+orderId);
            paymentLinkRequest.put("callback_method", "get");

            PaymentLink payment = razorpay.paymentLink.create(paymentLinkRequest);

            String paymentLinkUrl = payment.get("short_url");

            PaymentResponse res = new PaymentResponse();
            res.setPayment_url(paymentLinkUrl);

            return res;

        } catch (RazorpayException e){
            System.out.println("Error creating payment link" + e.getMessage());
            throw new RazorpayException(e.getMessage());
        }

    }

    @Override
    public PaymentResponse createStripePaymentLink(User user, Long amount, Long orderId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(String.valueOf(orderId))
                // Stripe substitutes {CHECKOUT_SESSION_ID} so the frontend can hand it back for verification
                .setSuccessUrl(frontendUrl+"/wallet?order_id="+orderId+"&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendUrl+"/wallet")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(amount*100)
                                .setProductData(SessionCreateParams
                                        .LineItem
                                        .PriceData
                                        .ProductData
                                        .builder()
                                        .setName("Top up wallet")
                                        .build()
                                ).build()
                        ).build()
                ).build();

        Session session = Session.create(params);
        PaymentResponse res = new PaymentResponse();
        res.setPayment_url(session.getUrl());

        return res;
    }
}
