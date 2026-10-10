package dev.onesnzeroes.vendor.endpoint.supplier.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SupplierRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 100) String contactName,
        @Size(max = 30) String phone,
        @Email @Size(max = 255) String email
) {
}
