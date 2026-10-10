package dev.onesnzeroes.vendor.endpoint.customer;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.customer.dto.CustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<Customer> getCustomers(@RequestParam(required = false) String email,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return customerService.getCustomers(email, page, size);
    }

    @GetMapping("/{id}")
    public Customer getCustomer(@PathVariable Integer id) {
        return customerService.getCustomer(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @PutMapping("/{id}")
    public Customer updateCustomer(@PathVariable Integer id, @Valid @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteCustomer(@PathVariable Integer id) {
        customerService.deleteCustomer(id);
        return new DeleteResponse("Customer deleted successfully", id);
    }

    @GetMapping("/{id}/addresses")
    public List<CustomerAddress> getAddresses(@PathVariable Integer id) {
        return customerService.getAddresses(id);
    }

    @GetMapping("/{id}/addresses/{addressId}")
    public CustomerAddress getAddress(@PathVariable Integer id, @PathVariable Integer addressId) {
        return customerService.getAddress(id, addressId);
    }

    @PostMapping("/{id}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerAddress addAddress(@PathVariable Integer id, @Valid @RequestBody AddressRequest request) {
        return customerService.addAddress(id, request);
    }

    @PutMapping("/{id}/addresses/{addressId}")
    public CustomerAddress updateAddress(@PathVariable Integer id, @PathVariable Integer addressId,
                                         @Valid @RequestBody AddressRequest request) {
        return customerService.updateAddress(id, addressId, request);
    }

    @DeleteMapping("/{id}/addresses/{addressId}")
    public DeleteResponse deleteAddress(@PathVariable Integer id, @PathVariable Integer addressId) {
        customerService.deleteAddress(id, addressId);
        return new DeleteResponse("Address deleted successfully", addressId);
    }
}
