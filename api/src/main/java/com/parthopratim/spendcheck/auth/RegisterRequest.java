package com.parthopratim.spendcheck.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Self sign-up payload. The byte-level BCrypt cap and the email-exists check are enforced in the service. */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String password,
        @NotBlank @Size(max = 60, message = "must be at most 60 characters") String name,
        @NotNull @Min(1) @Max(31) Integer salaryDay,
        @NotBlank String inviteCode) {

    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", password=****, name=" + name
                + ", salaryDay=" + salaryDay + ", inviteCode=****]";
    }
}
