package dev.onesnzeroes.vendor.endpoint.productcategory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductCategoryRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private String description;

    public ProductCategoryRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
