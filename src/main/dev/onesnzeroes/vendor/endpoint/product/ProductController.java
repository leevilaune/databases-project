package dev.onesnzeroes.vendor.endpoint.product;

import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import dev.onesnzeroes.vendor.endpoint.product.dto.ProductRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getProducts(@RequestParam(required = false) Integer supplierId,
                                     @RequestParam(required = false) Integer categoryId) {
        return productService.getProducts(supplierId, categoryId);
    }

    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Integer id) {
        return productService.getProductById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@Valid @RequestBody ProductRequest request) {
        return productService.createProduct(request);
    }

    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Integer id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteProduct(@PathVariable Integer id) {
        productService.deleteProduct(id);
        return new DeleteResponse("Product deleted successfully", id);
    }
}
