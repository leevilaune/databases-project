package dev.onesnzeroes.vendor.endpoint.supplier;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.address.SupplierAddress;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.supplier.dto.SupplierRequest;
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

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    private static final String VALID_SUPPLIER = """
            {"name":"Test Vendor","contactName":"Maija","phone":"0401234567","email":"vendor@example.com"}
            """;
    private static final String VALID_ADDRESS = """
            {"streetAddress":"Uusikatu 2","postalCode":"00100","city":"Helsinki","country":"Finland"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SupplierService supplierService;

    @Test
    @DisplayName("POST /suppliers without a name -> 400")
    void createWithoutName() throws Exception {
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactName\":\"Maija\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("name")));
        verifyNoInteractions(supplierService);
    }

    @Test
    @DisplayName("POST /suppliers with an invalid e-mail -> 400")
    void createWithInvalidEmail() throws Exception {
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Vendor\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("email")));
        verifyNoInteractions(supplierService);
    }

    @Test
    @DisplayName("POST /suppliers with a too long phone -> 400")
    void createWithTooLongPhone() throws Exception {
        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Vendor\",\"phone\":\"" + "1".repeat(31) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("phone")));
    }

    @Test
    @DisplayName("POST /suppliers creates the supplier -> 201")
    void create() throws Exception {
        when(supplierService.createSupplier(any())).thenReturn(TestData.supplier(5, "Test Vendor"));

        mvc.perform(post("/api/suppliers").contentType(MediaType.APPLICATION_JSON).content(VALID_SUPPLIER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Test Vendor"));

        ArgumentCaptor<SupplierRequest> captor = ArgumentCaptor.forClass(SupplierRequest.class);
        verify(supplierService).createSupplier(captor.capture());
        assertEquals("Test Vendor", captor.getValue().getName());
        assertEquals("Maija", captor.getValue().getContactName());
        assertEquals("vendor@example.com", captor.getValue().getEmail());
    }

    @Test
    @DisplayName("GET /suppliers lists suppliers")
    void list() throws Exception {
        when(supplierService.getAllSuppliers())
                .thenReturn(List.of(TestData.supplier(1, "A"), TestData.supplier(5, "Test Vendor")));

        mvc.perform(get("/api/suppliers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].id").value(5));
    }

    @Test
    @DisplayName("GET /suppliers/{id} returns the supplier")
    void getOne() throws Exception {
        when(supplierService.getSupplierById(5)).thenReturn(TestData.supplier(5, "Test Vendor"));

        mvc.perform(get("/api/suppliers/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Test Vendor"));
    }

    @Test
    @DisplayName("PUT /suppliers/{id} updates the supplier")
    void update() throws Exception {
        Supplier updated = TestData.supplier(5, "Test Vendor");
        updated.setContactName("Updated Contact");
        updated.setPhone("0507654321");
        when(supplierService.updateSupplier(eq(5), any())).thenReturn(updated);

        mvc.perform(put("/api/suppliers/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Vendor\",\"contactName\":\"Updated Contact\",\"phone\":\"0507654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactName").value("Updated Contact"))
                .andExpect(jsonPath("$.phone").value("0507654321"));
    }

    @Test
    @DisplayName("GET /suppliers/{id}/address without an address -> 404")
    void getMissingAddress() throws Exception {
        when(supplierService.getAddress(5))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier has no address"));

        mvc.perform(get("/api/suppliers/5/address"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Supplier has no address"));
    }

    @Test
    @DisplayName("PUT /suppliers/{id}/address creates or replaces the address")
    void putAddress() throws Exception {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierService.putAddress(eq(5), any()))
                .thenReturn(TestData.supplierAddress(7, supplier, "Uusikatu 2", "Helsinki"));

        mvc.perform(put("/api/suppliers/5/address").contentType(MediaType.APPLICATION_JSON).content(VALID_ADDRESS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.streetAddress").value("Uusikatu 2"))
                .andExpect(jsonPath("$.supplier").doesNotExist());

        ArgumentCaptor<AddressRequest> captor = ArgumentCaptor.forClass(AddressRequest.class);
        verify(supplierService).putAddress(eq(5), captor.capture());
        assertEquals("Helsinki", captor.getValue().getCity());
    }

    @Test
    @DisplayName("GET /suppliers/{id}/address returns it")
    void getAddress() throws Exception {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierService.getAddress(5)).thenReturn(TestData.supplierAddress(7, supplier, "Uusikatu 2", "Helsinki"));

        mvc.perform(get("/api/suppliers/5/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetAddress").value("Uusikatu 2"));
    }

    @Test
    @DisplayName("PUT /suppliers/{id}/address without a city -> 400")
    void putAddressWithoutCity() throws Exception {
        mvc.perform(put("/api/suppliers/5/address").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streetAddress\":\"Uusikatu 2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("city")));
        verifyNoInteractions(supplierService);
    }

    @Test
    @DisplayName("DELETE /suppliers/{id} with an address or products -> 409 (foreign key)")
    void deleteWithChildren() throws Exception {
        doThrow(TestData.foreignKeyViolation()).when(supplierService).deleteSupplier(5);

        mvc.perform(delete("/api/suppliers/5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("foreign key")));
    }

    @Test
    @DisplayName("GET /suppliers/{unknown id} -> 404")
    void getUnknown() throws Exception {
        when(supplierService.getSupplierById(999999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

        mvc.perform(get("/api/suppliers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Supplier not found"));
    }

    @Test
    @DisplayName("PUT /suppliers/{unknown id} -> 404")
    void updateUnknown() throws Exception {
        when(supplierService.updateSupplier(eq(999999), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));

        mvc.perform(put("/api/suppliers/999999").contentType(MediaType.APPLICATION_JSON).content(VALID_SUPPLIER))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /suppliers/{id}/address deletes the address")
    void deleteAddress() throws Exception {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierService.getAddress(5)).thenReturn(TestData.supplierAddress(7, supplier, "Uusikatu 2", "Helsinki"));

        mvc.perform(delete("/api/suppliers/5/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Supplier address deleted successfully"))
                .andExpect(jsonPath("$.id").value(7));
        verify(supplierService).deleteAddress(5);
    }

    @Test
    @DisplayName("DELETE /suppliers/{id} deletes the supplier")
    void deleteSupplier() throws Exception {
        mvc.perform(delete("/api/suppliers/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Supplier deleted successfully"))
                .andExpect(jsonPath("$.id").value(5));
        verify(supplierService).deleteSupplier(5);
    }
}
