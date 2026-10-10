package dev.onesnzeroes.vendor.endpoint.customer;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.customer.dto.CustomerRequest;
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

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    private static final String VALID_CUSTOMER = """
            {"firstName":"Testi","lastName":"Asiakas","email":"testi@example.com","phone":"0401234567"}
            """;
    private static final String VALID_ADDRESS = """
            {"streetAddress":"Testikatu 1","postalCode":"00100","city":"Helsinki","country":"Finland"}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CustomerService customerService;

    private static Customer customer() {
        return TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
    }

    @Test
    @DisplayName("POST /customers creates the customer -> 201")
    void create() throws Exception {
        when(customerService.createCustomer(any())).thenReturn(customer());

        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(VALID_CUSTOMER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.email").value("testi@example.com"));

        ArgumentCaptor<CustomerRequest> captor = ArgumentCaptor.forClass(CustomerRequest.class);
        verify(customerService).createCustomer(captor.capture());
        assertEquals("Testi", captor.getValue().getFirstName());
        assertEquals("Asiakas", captor.getValue().getLastName());
    }

    @Test
    @DisplayName("POST /customers with an invalid e-mail -> 400")
    void createWithInvalidEmail() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Testi\",\"lastName\":\"Asiakas\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("email")));
        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("POST /customers without names and e-mail -> 400 listing every field")
    void createEmpty() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("firstName")))
                .andExpect(jsonPath("$.detail").value(containsString("lastName")))
                .andExpect(jsonPath("$.detail").value(containsString("email")));
    }

    @Test
    @DisplayName("GET /customers is paginated")
    void listPaginated() throws Exception {
        List<Customer> page = List.of(
                TestData.customer(1, "A", "A", "a@example.com"),
                TestData.customer(2, "B", "B", "b@example.com"),
                TestData.customer(3, "C", "C", "c@example.com"));
        when(customerService.getCustomers(isNull(), eq(0), eq(3))).thenReturn(PageResponse.of(page, 0, 3, 10));

        mvc.perform(get("/api/customers").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(10))
                .andExpect(jsonPath("$.totalPages").value(4));
    }

    @Test
    @DisplayName("GET /customers uses page 0 and size 20 by default")
    void listDefaults() throws Exception {
        when(customerService.getCustomers(null, 0, 20)).thenReturn(PageResponse.of(List.of(), 0, 20, 0));

        mvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
        verify(customerService).getCustomers(null, 0, 20);
    }

    @Test
    @DisplayName("GET /customers?email= finds the customer")
    void findByEmail() throws Exception {
        when(customerService.getCustomers("testi@example.com", 0, 20))
                .thenReturn(PageResponse.of(List.of(customer()), 0, 20, 1));

        mvc.perform(get("/api/customers").param("email", "testi@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(12));
    }

    @Test
    @DisplayName("GET /customers/{id} returns the customer")
    void getOne() throws Exception {
        when(customerService.getCustomer(12)).thenReturn(customer());

        mvc.perform(get("/api/customers/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12));
    }

    @Test
    @DisplayName("PUT /customers/{id} updates the customer")
    void update() throws Exception {
        Customer updated = customer();
        updated.setLastName("Updated");
        updated.setPhone("0507654321");
        when(customerService.updateCustomer(eq(12), any())).thenReturn(updated);

        mvc.perform(put("/api/customers/12").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Testi\",\"lastName\":\"Updated\",\"email\":\"testi@example.com\",\"phone\":\"0507654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Updated"))
                .andExpect(jsonPath("$.phone").value("0507654321"));
    }

    @Test
    @DisplayName("POST /customers/{id}/addresses adds an address -> 201")
    void addAddress() throws Exception {
        when(customerService.addAddress(eq(12), any()))
                .thenReturn(TestData.customerAddress(30, customer(), "Testikatu 1", "Helsinki"));

        mvc.perform(post("/api/customers/12/addresses").contentType(MediaType.APPLICATION_JSON).content(VALID_ADDRESS))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.customer").doesNotExist());
    }

    @Test
    @DisplayName("POST /customers/{id}/addresses without a city -> 400")
    void addAddressWithoutCity() throws Exception {
        mvc.perform(post("/api/customers/12/addresses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streetAddress\":\"Testikatu 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("city")));
        verifyNoInteractions(customerService);
    }

    @Test
    @DisplayName("POST /customers/{unknown id}/addresses -> 404")
    void addAddressToUnknownCustomer() throws Exception {
        when(customerService.addAddress(eq(999999), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        mvc.perform(post("/api/customers/999999/addresses").contentType(MediaType.APPLICATION_JSON).content(VALID_ADDRESS))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /customers/{id}/addresses lists it")
    void listAddresses() throws Exception {
        when(customerService.getAddresses(12))
                .thenReturn(List.of(TestData.customerAddress(30, customer(), "Testikatu 1", "Helsinki")));

        mvc.perform(get("/api/customers/12/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("PUT /customers/{id}/addresses/{addressId} updates it")
    void updateAddress() throws Exception {
        CustomerAddress updated = TestData.customerAddress(30, customer(), "Testikatu 2", "Tampere");
        updated.setPostalCode("33100");
        when(customerService.updateAddress(eq(12), eq(30), any())).thenReturn(updated);

        mvc.perform(put("/api/customers/12/addresses/30").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"streetAddress\":\"Testikatu 2\",\"postalCode\":\"33100\",\"city\":\"Tampere\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Tampere"))
                .andExpect(jsonPath("$.postalCode").value("33100"));
    }

    @Test
    @DisplayName("GET /customers/{id}/addresses/{addressId} returns it")
    void getAddress() throws Exception {
        when(customerService.getAddress(12, 30))
                .thenReturn(TestData.customerAddress(30, customer(), "Testikatu 2", "Tampere"));

        mvc.perform(get("/api/customers/12/addresses/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.streetAddress").value("Testikatu 2"));
    }

    @Test
    @DisplayName("GET another customer's address through this customer -> 404")
    void getOtherCustomersAddress() throws Exception {
        when(customerService.getAddress(12, 1))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        mvc.perform(get("/api/customers/12/addresses/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Address not found"));
    }

    @Test
    @DisplayName("DELETE /customers/{id} with an address or orders -> 409 (foreign key)")
    void deleteWithChildren() throws Exception {
        doThrow(TestData.foreignKeyViolation()).when(customerService).deleteCustomer(12);

        mvc.perform(delete("/api/customers/12"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DELETE an address that orders ship to -> 409 (foreign key)")
    void deleteAddressInUse() throws Exception {
        doThrow(TestData.foreignKeyViolation()).when(customerService).deleteAddress(12, 30);

        mvc.perform(delete("/api/customers/12/addresses/30"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /customers/{unknown id} -> 404")
    void getUnknown() throws Exception {
        when(customerService.getCustomer(999999))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));

        mvc.perform(get("/api/customers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Customer not found"));
    }

    @Test
    @DisplayName("DELETE /customers/{id}/addresses/{addressId} deletes the address")
    void deleteAddress() throws Exception {
        mvc.perform(delete("/api/customers/12/addresses/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Address deleted successfully"))
                .andExpect(jsonPath("$.id").value(30));
        verify(customerService).deleteAddress(12, 30);
    }

    @Test
    @DisplayName("DELETE /customers/{id} deletes the customer")
    void deleteCustomer() throws Exception {
        mvc.perform(delete("/api/customers/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Customer deleted successfully"))
                .andExpect(jsonPath("$.id").value(12));
        verify(customerService).deleteCustomer(12);
    }
}
