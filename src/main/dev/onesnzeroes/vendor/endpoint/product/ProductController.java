package dev.onesnzeroes.vendor.endpoint.product;

import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    public ProductController(ProductRepository productRepository, ProductCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Integer id) {
        return productRepository.findById(id).orElseThrow();
    }

    @PostMapping("/categories")
    public ProductCategory createCategory(@RequestBody ProductCategory category) {
        category.getProducts().forEach(product -> product.setCategory(category));
        return categoryRepository.save(category);
    }

    @GetMapping("/categories/{id}")
    public ProductCategory getCategory(@PathVariable Integer id) {
        return categoryRepository.findById(id).orElseThrow();
    }

    @DeleteMapping("/categories/{id}")
    @Transactional
    public void deleteCategory(@PathVariable Integer id) {
        ProductCategory category = categoryRepository.findById(id).orElseThrow();
        category.getProducts().forEach(product -> product.setCategory(null));
        productRepository.saveAll(category.getProducts());
        categoryRepository.delete(category);
    }
}
