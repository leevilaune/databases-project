package dev.onesnzeroes.vendor.endpoint.productcategory;

import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import dev.onesnzeroes.vendor.endpoint.productcategory.dto.ProductCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/productcategories")
public class ProductCategoryController {

    private final ProductCategoryService productCategoryService;

    public ProductCategoryController(ProductCategoryService productCategoryService) {
        this.productCategoryService = productCategoryService;
    }

    @GetMapping
    public List<ProductCategory> getAllCategories() {
        return productCategoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    public ProductCategory getCategory(@PathVariable Integer id) {
        return productCategoryService.getCategoryById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCategory createCategory(@Valid @RequestBody ProductCategoryRequest request) {
        return productCategoryService.createCategory(request);
    }

    @PutMapping("/{id}")
    public ProductCategory updateCategory(@PathVariable Integer id, @Valid @RequestBody ProductCategoryRequest request) {
        return productCategoryService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteCategory(@PathVariable Integer id) {
        productCategoryService.deleteCategory(id);
        return new DeleteResponse("Product category deleted successfully", id);
    }
}
