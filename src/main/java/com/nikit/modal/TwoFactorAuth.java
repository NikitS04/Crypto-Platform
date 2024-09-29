package com.nikit.modal;

import com.nikit.domain.VerificationType;
import lombok.Data;

@Data

public class TwoFactorAuth {
    private boolean isEnabled = false;
    private VerificationType sendTo;
}
