package dev.onesnzeroes.vendor.endpoint.productcategory;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.productcategory.dto.ProductCategoryRequest;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(ProductCategoryController.class)
class ProductCategoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductCategoryService productCategoryService;

    @Test
    @DisplayName("POST /productcategories creates the category -> 201")
    void create() throws Exception {
        when(productCategoryService.createCategory(any())).thenReturn(TestData.category(3, "Tools"));

        mvc.perform(post("/api/productcategories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tools\",\"description\":\"Created by test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Tools"));

        ArgumentCaptor<ProductCategoryRequest> captor = ArgumentCaptor.forClass(ProductCategoryRequest.class);
        verify(productCategoryService).createCategory(captor.capture());
        assertEquals("Created by test", captor.getValue().getDescription());
    }

    @Test
    @DisplayName("POST /productcategories without name -> 400")
    void createWithoutName() throws Exception {
        mvc.perform(post("/api/productcategories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("name")));
        verifyNoInteractions(productCategoryService);
    }

    @Test
    @DisplayName("POST /productcategories with a too long name -> 400")
    void createWithTooLongName() throws Exception {
        mvc.perform(post("/api/productcategories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(productCategoryService);
    }

    @Test
    @DisplayName("GET /productcategories lists categories")
    void list() throws Exception {
        when(productCategoryService.getAllCategories())
                .thenReturn(List.of(TestData.category(1, "Books"), TestData.category(3, "Tools")));

        mvc.perform(get("/api/productcategories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].name").value("Tools"));
    }

    @Test
    @DisplayName("GET /productcategories/{id} returns the category")
    void getOne() throws Exception {
        when(productCategoryService.getCategoryById(3)).thenReturn(TestData.category(3, "Tools"));

        mvc.perform(get("/api/productcategories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Tools"));
    }

    @Test
    @DisplayName("PUT /productcategories/{id} updates the category")
    void update() throws Exception {
        var updated = TestData.category(3, "Tools-upd");
        updated.setDescription("Updated by test.sh");
        when(productCategoryService.updateCategory(eq(3), any())).thenReturn(updated);

        mvc.perform(put("/api/productcategories/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tools-upd\",\"description\":\"Updated by test.sh\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Tools-upd"))
                .andExpect(jsonPath("$.description").value("Updated by test.sh"));
    }

    @Test
    @DisplayName("GET /productcategories/{unknown id} -> 404")
    void getUnknown() throws Exception {
        when(productCategoryService.getCategoryById(999999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product category not found"));

        mvc.perform(get("/api/productcategories/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product category not found"));
    }

    @Test
    @DisplayName("PUT /productcategories/{unknown id} -> 404")
    void updateUnknown() throws Exception {
        when(productCategoryService.updateCategory(eq(999999), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Product category not found"));

        mvc.perform(put("/api/productcategories/999999").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /productcategories/{id} with products in it -> 409 (foreign key)")
    void deleteWithProducts() throws Exception {
        doThrow(TestData.foreignKeyViolation()).when(productCategoryService).deleteCategory(3);

        mvc.perform(delete("/api/productcategories/3"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE /productcategories/{id} deletes the category")
    void deleteCategory() throws Exception {
        mvc.perform(delete("/api/productcategories/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product category deleted successfully"))
                .andExpect(jsonPath("$.id").value(3));
        verify(productCategoryService).deleteCategory(3);
    }
}
