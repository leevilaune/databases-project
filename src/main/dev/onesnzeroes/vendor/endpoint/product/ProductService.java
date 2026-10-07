package dev.onesnzeroes.vendor.endpoint.product;

import dev.onesnzeroes.vendor.endpoint.product.dto.ProductRequest;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategory;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategoryRepository;
import dev.onesnzeroes.vendor.endpoint.supplier.SupplierRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductService(ProductRepository productRepository,
                          ProductCategoryRepository productCategoryRepository,
                          SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.productCategoryRepository = productCategoryRepository;
        this.supplierRepository = supplierRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public Product createProduct(ProductRequest request) {
        Product product = new Product();
        applyRequest(product, request);
        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Integer id, ProductRequest request) {
        Product product = getProductById(id);
        applyRequest(product, request);
        return productRepository.save(product);
    }

    public void deleteProduct(Integer id) {
        Product product = getProductById(id);
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(resolveCategory(request.getCategoryId()));
        product.setSupplierId(resolveSupplierId(request.getSupplierId()));
    }

    private ProductCategory resolveCategory(Integer categoryId) {
        if (categoryId == null) {
            return null;
        }
        return productCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product category not found"));
    }

    private Integer resolveSupplierId(Integer supplierId) {
        if (supplierId != null && !supplierRepository.existsById(supplierId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier not found");
        }
        return supplierId;
    }
}
