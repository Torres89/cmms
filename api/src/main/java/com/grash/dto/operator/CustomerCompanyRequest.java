package com.grash.dto.operator;

import com.grash.model.enums.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerCompanyRequest {

    @NotBlank
    @Size(max = 255)
    private String companyName;

    @Min(0)
    private int employeesCount;

    private Language language;

    @NotBlank
    @Email
    private String adminEmail;

    @NotBlank
    private String adminFirstName;

    @NotBlank
    private String adminLastName;

    private String adminPhone;

    /**
     * A temporary password the operator hands over at commissioning. The
     * customer changes it from their profile once they are in.
     */
    @NotBlank
    @Size(min = 8, max = 128)
    private String adminPassword;
}
