package com.nikit.modal;

import com.nikit.domain.PaymentMethod;
import com.nikit.domain.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data

public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Long amount;

    private PaymentOrderStatus status;

    private PaymentMethod paymentMethod;

    @ManyToOne
    private User user;

    // Provider-side payment / checkout session id, unique so one payment can't be credited twice
    @Column(unique = true)
    private String providerPaymentId;
}
