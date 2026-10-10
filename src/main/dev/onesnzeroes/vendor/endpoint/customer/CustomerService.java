package dev.onesnzeroes.vendor.endpoint.customer;

import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddressRepository;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.customer.dto.CustomerRequest;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    public PageResponse<Customer> getCustomers(String email, int page, int size) {
        PageRequest pageable = PageRequest.of(PageResponse.page(page), PageResponse.size(size), Sort.by("id"));
        return PageResponse.of(email == null || email.isBlank()
                ? customerRepository.findAll(pageable)
                : customerRepository.findByEmail(email.trim(), pageable));
    }

    public Customer getCustomer(Integer id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
    }

    public Customer createCustomer(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Integer id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        apply(customer, request);
        return customerRepository.save(customer);
    }

    public void deleteCustomer(Integer id) {
        customerRepository.delete(getCustomer(id));
    }

    public List<CustomerAddress> getAddresses(Integer customerId) {
        getCustomer(customerId);
        return addressRepository.findByCustomerIdOrderByIdAsc(customerId);
    }

    public CustomerAddress getAddress(Integer customerId, Integer addressId) {
        return addressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    public CustomerAddress addAddress(Integer customerId, AddressRequest request) {
        CustomerAddress address = new CustomerAddress();
        address.setCustomer(getCustomer(customerId));
        applyAddress(address, request);
        return addressRepository.save(address);
    }

    @Transactional
    public CustomerAddress updateAddress(Integer customerId, Integer addressId, AddressRequest request) {
        CustomerAddress address = getAddress(customerId, addressId);
        applyAddress(address, request);
        return addressRepository.save(address);
    }

    public void deleteAddress(Integer customerId, Integer addressId) {
        addressRepository.delete(getAddress(customerId, addressId));
    }

    private static void applyAddress(CustomerAddress address, AddressRequest request) {
        address.setStreetAddress(request.streetAddress());
        address.setPostalCode(request.postalCode());
        address.setCity(request.city());
        address.setCountry(request.country());
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.firstName().trim());
        customer.setLastName(request.lastName().trim());
        customer.setEmail(request.email().trim());
        customer.setPhone(request.phone());
    }
}
