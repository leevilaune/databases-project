package dev.onesnzeroes.vendor.endpoint.productcategory;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.productcategory.dto.ProductCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock
    private ProductCategoryRepository repository;

    private ProductCategoryService service;

    @BeforeEach
    void setUp() {
        service = new ProductCategoryService(repository);
    }

    private static ProductCategoryRequest request(String name, String description) {
        ProductCategoryRequest request = new ProductCategoryRequest();
        request.setName(name);
        request.setDescription(description);
        return request;
    }

    @Test
    void getAllCategoriesReturnsRepositoryRows() {
        List<ProductCategory> categories = List.of(TestData.category(1, "Books"));
        when(repository.findAll()).thenReturn(categories);

        assertSame(categories, service.getAllCategories());
    }

    @Test
    void getCategoryByIdUnknownIs404() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Product category not found", () -> service.getCategoryById(999));
    }

    @Test
    void createCategoryCopiesFields() {
        when(repository.save(any(ProductCategory.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductCategory created = service.createCategory(request("Tools", "Hand tools"));

        assertEquals("Tools", created.getName());
        assertEquals("Hand tools", created.getDescription());
    }

    @Test
    void updateCategoryChangesTheExistingRow() {
        ProductCategory existing = TestData.category(3, "Old");
        when(repository.findById(3)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        ProductCategory updated = service.updateCategory(3, request("New", null));

        assertSame(existing, updated);
        assertEquals("New", updated.getName());
        assertEquals(null, updated.getDescription());
    }

    @Test
    void updateUnknownCategoryIs404() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Product category not found",
                () -> service.updateCategory(999, request("x", null)));
        verify(repository, never()).save(any());
    }

    @Test
    void deleteCategoryDeletesTheRow() {
        ProductCategory existing = TestData.category(3, "Tools");
        when(repository.findById(3)).thenReturn(Optional.of(existing));

        service.deleteCategory(3);

        verify(repository).delete(existing);
    }

    @Test
    void deleteUnknownCategoryIs404() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, null, () -> service.deleteCategory(999));
        verify(repository, never()).delete(any());
    }
}
