package dev.onesnzeroes.vendor.endpoint.productcategory;

import dev.onesnzeroes.vendor.endpoint.productcategory.dto.ProductCategoryRequest;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductCategoryService {

    private final ProductCategoryRepository productCategoryRepository;

    public ProductCategoryService(ProductCategoryRepository productCategoryRepository) {
        this.productCategoryRepository = productCategoryRepository;
    }

    public List<ProductCategory> getAllCategories() {
        return productCategoryRepository.findAll();
    }

    public ProductCategory getCategoryById(Integer id) {
        return productCategoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product category not found"));
    }

    public ProductCategory createCategory(ProductCategoryRequest request) {
        ProductCategory category = new ProductCategory();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return productCategoryRepository.save(category);
    }

    @Transactional
    public ProductCategory updateCategory(Integer id, ProductCategoryRequest request) {
        ProductCategory category = getCategoryById(id);
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        return productCategoryRepository.save(category);
    }

    public void deleteCategory(Integer id) {
        ProductCategory category = getCategoryById(id);
        productCategoryRepository.delete(category);
    }
}
