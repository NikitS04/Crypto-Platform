package com.nikit.modal;

import com.nikit.domain.VerificationType;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.GenericGenerators;

@Entity
@Data
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String otp;

    @OneToOne
    private User user;

    private String email;

    private String mobile;

    private VerificationType verificationType;

}
