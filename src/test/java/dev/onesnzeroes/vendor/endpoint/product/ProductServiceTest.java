package dev.onesnzeroes.vendor.endpoint.product;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.product.dto.ProductRequest;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategory;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategoryRepository;
import dev.onesnzeroes.vendor.endpoint.supplier.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private SupplierRepository supplierRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(productRepository, categoryRepository, supplierRepository);
    }

    private static ProductRequest request(Integer categoryId, Integer supplierId) {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setDescription("test product");
        request.setPrice(new BigDecimal("49.90"));
        request.setStockQuantity(10);
        request.setCategoryId(categoryId);
        request.setSupplierId(supplierId);
        return request;
    }

    @Test
    void getProductsWithoutFiltersListsAllByName() {
        List<Product> products = List.of(TestData.product(1, "A", "1.00", 1, null, null));
        when(productRepository.findAllByOrderByNameAsc()).thenReturn(products);

        assertSame(products, service.getProducts(null, null));
    }

    @Test
    void getProductsBySupplier() {
        List<Product> products = List.of(TestData.product(1, "A", "1.00", 1, null, 5));
        when(productRepository.findBySupplierIdOrderByNameAsc(5)).thenReturn(products);

        assertSame(products, service.getProducts(5, null));
    }

    @Test
    void getProductsByCategory() {
        List<Product> products = List.of(TestData.product(1, "A", "1.00", 1, TestData.category(3, "Tools"), null));
        when(productRepository.findByCategoryIdOrderByNameAsc(3)).thenReturn(products);

        assertSame(products, service.getProducts(null, 3));
    }

    @Test
    void getProductsBySupplierAndCategory() {
        List<Product> products = List.of();
        when(productRepository.findBySupplierIdAndCategoryIdOrderByNameAsc(5, 3)).thenReturn(products);

        assertSame(products, service.getProducts(5, 3));
    }

    @Test
    void getProductByIdUnknownIs404() {
        when(productRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Product not found", () -> service.getProductById(999));
    }

    @Test
    void createProductResolvesCategoryAndSupplier() {
        ProductCategory category = TestData.category(3, "Tools");
        when(categoryRepository.findById(3)).thenReturn(Optional.of(category));
        when(supplierRepository.existsById(5)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.createProduct(request(3, 5));

        assertEquals("Test Product", created.getName());
        assertEquals("test product", created.getDescription());
        assertEquals(new BigDecimal("49.90"), created.getPrice());
        assertEquals(10, created.getStockQuantity());
        assertSame(category, created.getCategory());
        assertEquals(5, created.getSupplierId());
    }

    @Test
    void createProductWithoutCategoryAndSupplierSkipsTheLookups() {
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product created = service.createProduct(request(null, null));

        assertNull(created.getCategory());
        assertNull(created.getSupplierId());
        verifyNoInteractions(categoryRepository, supplierRepository);
    }

    @Test
    void createProductWithUnknownCategoryIs400() {
        when(categoryRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Product category not found",
                () -> service.createProduct(request(999, 5)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createProductWithUnknownSupplierIs400() {
        when(categoryRepository.findById(3)).thenReturn(Optional.of(TestData.category(3, "Tools")));
        when(supplierRepository.existsById(999)).thenReturn(false);

        TestData.assertStatus(HttpStatus.BAD_REQUEST, "Supplier not found",
                () -> service.createProduct(request(3, 999)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateProductChangesTheExistingRow() {
        Product existing = TestData.product(8, "Old", "10.00", 1, TestData.category(3, "Tools"), 5);
        when(productRepository.findById(8)).thenReturn(Optional.of(existing));
        when(supplierRepository.existsById(5)).thenReturn(true);
        when(productRepository.save(existing)).thenReturn(existing);

        ProductRequest request = request(null, 5);
        request.setPrice(new BigDecimal("59.9"));
        request.setStockQuantity(20);
        Product updated = service.updateProduct(8, request);

        assertSame(existing, updated);
        assertEquals("Test Product", updated.getName());
        assertEquals(new BigDecimal("59.9"), updated.getPrice());
        assertEquals(20, updated.getStockQuantity());
        assertNull(updated.getCategory());
    }

    @Test
    void updateUnknownProductIs404() {
        when(productRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Product not found", () -> service.updateProduct(999, request(null, null)));
        verify(productRepository, never()).save(any());
        verify(supplierRepository, never()).existsById(anyInt());
    }

    @Test
    void deleteProductDeletesTheRow() {
        Product existing = TestData.product(8, "Test Product", "49.90", 10, null, 5);
        when(productRepository.findById(8)).thenReturn(Optional.of(existing));

        service.deleteProduct(8);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteUnknownProductIs404() {
        when(productRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Product not found", () -> service.deleteProduct(999));
        verify(productRepository, never()).delete(any());
    }
}
