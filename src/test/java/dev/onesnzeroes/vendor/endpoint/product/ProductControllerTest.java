package dev.onesnzeroes.vendor.endpoint.product;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.product.dto.ProductRequest;
import dev.onesnzeroes.vendor.endpoint.productcategory.ProductCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    private static String body(String name, String price, String stock, Integer categoryId, Integer supplierId) {
        return "{\"name\":" + (name == null ? "null" : "\"" + name + "\"")
                + ",\"description\":\"test product\""
                + ",\"price\":" + price
                + ",\"stockQuantity\":" + stock
                + ",\"categoryId\":" + categoryId
                + ",\"supplierId\":" + supplierId + "}";
    }

    private static String valid() {
        return body("Test Product", "49.90", "10", 3, 5);
    }

    @Test
    @DisplayName("POST /products creates the product -> 201")
    void create() throws Exception {
        ProductCategory category = TestData.category(3, "Tools");
        when(productService.createProduct(any())).thenReturn(TestData.product(8, "Test Product", "49.90", 10, category, 5));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.category.id").value(3))
                .andExpect(jsonPath("$.supplierId").value(5));

        ArgumentCaptor<ProductRequest> captor = ArgumentCaptor.forClass(ProductRequest.class);
        verify(productService).createProduct(captor.capture());
        assertEquals(new BigDecimal("49.90"), captor.getValue().getPrice());
        assertEquals(10, captor.getValue().getStockQuantity());
        assertEquals(3, captor.getValue().getCategoryId());
        assertEquals(5, captor.getValue().getSupplierId());
    }

    @Test
    @DisplayName("POST /products with blank name -> 400")
    void createWithBlankName() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", "49.90", "10", 3, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("name")));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("POST /products with negative price -> 400")
    void createWithNegativePrice() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Product", "-1", "10", 3, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("price")));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("POST /products with negative stock -> 400")
    void createWithNegativeStock() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Product", "49.90", "-1", 3, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("stockQuantity")));
        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("POST /products without price and stock -> 400 listing both fields")
    void createWithoutPriceAndStock() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Product\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("price")))
                .andExpect(jsonPath("$.detail").value(containsString("stockQuantity")));
    }

    @Test
    @DisplayName("POST /products with unknown category -> 400")
    void createWithUnknownCategory() throws Exception {
        when(productService.createProduct(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product category not found"));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Product", "49.90", "10", 999999, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Product category not found"));
    }

    @Test
    @DisplayName("POST /products with unknown supplier -> 400")
    void createWithUnknownSupplier() throws Exception {
        when(productService.createProduct(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supplier not found"));

        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Product", "49.90", "10", 3, 999999)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Supplier not found"));
    }

    @Test
    @DisplayName("GET /products lists all products")
    void listAll() throws Exception {
        when(productService.getProducts(null, null)).thenReturn(List.of(
                TestData.product(1, "A", "1.00", 1, null, null),
                TestData.product(8, "Test Product", "49.90", 10, null, 5)));

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(8));
    }

    @Test
    @DisplayName("GET /products?supplierId= lists only that supplier's products")
    void listBySupplier() throws Exception {
        when(productService.getProducts(eq(5), isNull()))
                .thenReturn(List.of(TestData.product(8, "Test Product", "49.90", 10, null, 5)));

        mvc.perform(get("/api/products").param("supplierId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].supplierId").value(5));
    }

    @Test
    @DisplayName("GET /products?categoryId= lists only that category's products")
    void listByCategory() throws Exception {
        ProductCategory category = TestData.category(3, "Tools");
        when(productService.getProducts(isNull(), eq(3)))
                .thenReturn(List.of(TestData.product(8, "Test Product", "49.90", 10, category, 5)));

        mvc.perform(get("/api/products").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category.id").value(3));
    }

    @Test
    @DisplayName("GET /products?supplierId=&categoryId= passes both filters")
    void listBySupplierAndCategory() throws Exception {
        when(productService.getProducts(5, 3)).thenReturn(List.of());

        mvc.perform(get("/api/products").param("supplierId", "5").param("categoryId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        verify(productService).getProducts(5, 3);
    }

    @Test
    @DisplayName("GET /products/{id} returns the product with its category")
    void getOne() throws Exception {
        ProductCategory category = TestData.category(3, "Tools");
        when(productService.getProductById(8)).thenReturn(TestData.product(8, "Test Product", "49.90", 10, category, 5));

        mvc.perform(get("/api/products/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.category.name").value("Tools"));
    }

    @Test
    @DisplayName("PUT /products/{id} updates the product")
    void update() throws Exception {
        when(productService.updateProduct(eq(8), any()))
                .thenReturn(TestData.product(8, "Test Product", "59.9", 20, null, 5));

        mvc.perform(put("/api/products/8").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Test Product", "59.9", "20", null, 5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(59.9))
                .andExpect(jsonPath("$.stockQuantity").value(20));
    }

    @Test
    @DisplayName("GET /products/{unknown id} -> 404")
    void getUnknown() throws Exception {
        when(productService.getProductById(999999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        mvc.perform(get("/api/products/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product not found"));
    }

    @Test
    @DisplayName("DELETE /products/{unknown id} -> 404")
    void deleteUnknown() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"))
                .when(productService).deleteProduct(999999);

        mvc.perform(delete("/api/products/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /products/{id} used in an order -> 409 (foreign key)")
    void deleteUsedInOrder() throws Exception {
        doThrow(TestData.foreignKeyViolation()).when(productService).deleteProduct(8);

        mvc.perform(delete("/api/products/8"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /products/{id} deletes the product")
    void deleteProduct() throws Exception {
        mvc.perform(delete("/api/products/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product deleted successfully"))
                .andExpect(jsonPath("$.id").value(8));
        verify(productService).deleteProduct(8);
    }
}
