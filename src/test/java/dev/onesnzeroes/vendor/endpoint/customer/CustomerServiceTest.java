package dev.onesnzeroes.vendor.endpoint.customer;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddress;
import dev.onesnzeroes.vendor.endpoint.address.CustomerAddressRepository;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.common.PageResponse;
import dev.onesnzeroes.vendor.endpoint.customer.dto.CustomerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerAddressRepository addressRepository;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, addressRepository);
    }

    private static CustomerRequest request(String firstName, String lastName, String email) {
        CustomerRequest request = new CustomerRequest();
        request.setFirstName(firstName);
        request.setLastName(lastName);
        request.setEmail(email);
        request.setPhone("0401234567");
        return request;
    }

    private static AddressRequest addressRequest(String street, String city) {
        AddressRequest request = new AddressRequest();
        request.setStreetAddress(street);
        request.setPostalCode("00100");
        request.setCity(city);
        request.setCountry("Finland");
        return request;
    }

    @Test
    void getCustomersWithoutEmailPagesAllCustomersById() {
        Customer customer = TestData.customer(1, "A", "A", "a@example.com");
        Page<Customer> page = new PageImpl<>(List.of(customer), PageRequest.of(0, 3), 10);
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(page);

        PageResponse<Customer> result = service.getCustomers(null, 0, 3);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(3, captor.getValue().getPageSize());
        assertEquals(Sort.by("id"), captor.getValue().getSort());
        assertEquals(List.of(customer), result.getContent());
        assertEquals(10, result.getTotalElements());
        assertEquals(4, result.getTotalPages());
        verify(customerRepository, never()).findByEmail(any(), any());
    }

    @Test
    void getCustomersWithBlankEmailIgnoresTheFilter() {
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.getCustomers("   ", 0, 20);

        verify(customerRepository, never()).findByEmail(any(), any());
    }

    @Test
    void getCustomersByEmailTrimsTheEmail() {
        when(customerRepository.findByEmail(eq("testi@example.com"), any(Pageable.class))).thenReturn(Page.empty());

        service.getCustomers("  testi@example.com ", 0, 20);

        verify(customerRepository).findByEmail(eq("testi@example.com"), any(Pageable.class));
        verify(customerRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getCustomersClampsPageAndSize() {
        when(customerRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.getCustomers(null, -5, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(customerRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void getCustomerUnknownIs404() {
        when(customerRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Customer not found", () -> service.getCustomer(999));
    }

    @Test
    void createCustomerTrimsNamesAndEmail() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        Customer created = service.createCustomer(request(" Testi ", " Asiakas", "testi@example.com  "));

        assertEquals("Testi", created.getFirstName());
        assertEquals("Asiakas", created.getLastName());
        assertEquals("testi@example.com", created.getEmail());
        assertEquals("0401234567", created.getPhone());
    }

    @Test
    void updateCustomerChangesTheExistingRow() {
        Customer existing = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        when(customerRepository.findById(12)).thenReturn(Optional.of(existing));
        when(customerRepository.save(existing)).thenReturn(existing);

        CustomerRequest request = request("Testi", "Updated", "testi@example.com");
        request.setPhone("0507654321");
        Customer updated = service.updateCustomer(12, request);

        assertSame(existing, updated);
        assertEquals("Updated", updated.getLastName());
        assertEquals("0507654321", updated.getPhone());
    }

    @Test
    void deleteCustomerDeletesTheRow() {
        Customer existing = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        when(customerRepository.findById(12)).thenReturn(Optional.of(existing));

        service.deleteCustomer(12);

        verify(customerRepository).delete(existing);
    }

    @Test
    void getAddressesChecksTheCustomerFirst() {
        when(customerRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Customer not found", () -> service.getAddresses(999));
        verify(addressRepository, never()).findByCustomerIdOrderByIdAsc(anyInt());
    }

    @Test
    void getAddressesListsTheCustomersAddresses() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        List<CustomerAddress> addresses = List.of(TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki"));
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerIdOrderByIdAsc(12)).thenReturn(addresses);

        assertSame(addresses, service.getAddresses(12));
    }

    @Test
    void getAddressReturnsTheCustomersOwnAddress() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        CustomerAddress address = TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki");
        when(addressRepository.findById(30)).thenReturn(Optional.of(address));

        assertSame(address, service.getAddress(12, 30));
    }

    @Test
    void getAnotherCustomersAddressIs404() {
        Customer other = TestData.customer(1, "Muu", "Asiakas", "muu@example.com");
        when(addressRepository.findById(1)).thenReturn(Optional.of(TestData.customerAddress(1, other, "Muukatu 1", "Turku")));

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Address not found", () -> service.getAddress(12, 1));
    }

    @Test
    void getUnknownAddressIs404() {
        when(addressRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Address not found", () -> service.getAddress(12, 999));
    }

    @Test
    void addAddressLinksItToTheCustomer() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        when(customerRepository.findById(12)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any(CustomerAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerAddress address = service.addAddress(12, addressRequest("Testikatu 1", "Helsinki"));

        assertSame(customer, address.getCustomer());
        assertEquals("Testikatu 1", address.getStreetAddress());
        assertEquals("Helsinki", address.getCity());
        assertEquals("00100", address.getPostalCode());
        assertEquals("Finland", address.getCountry());
    }

    @Test
    void addAddressToUnknownCustomerIs404() {
        when(customerRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Customer not found",
                () -> service.addAddress(999, addressRequest("Testikatu 1", "Helsinki")));
        verify(addressRepository, never()).save(any());
    }

    @Test
    void updateAddressChangesTheExistingRow() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        CustomerAddress existing = TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki");
        when(addressRepository.findById(30)).thenReturn(Optional.of(existing));
        when(addressRepository.save(existing)).thenReturn(existing);

        CustomerAddress updated = service.updateAddress(12, 30, addressRequest("Testikatu 2", "Tampere"));

        assertSame(existing, updated);
        assertEquals("Testikatu 2", updated.getStreetAddress());
        assertEquals("Tampere", updated.getCity());
    }

    @Test
    void updateAnotherCustomersAddressIs404() {
        Customer other = TestData.customer(1, "Muu", "Asiakas", "muu@example.com");
        when(addressRepository.findById(1)).thenReturn(Optional.of(TestData.customerAddress(1, other, "Muukatu 1", "Turku")));

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Address not found",
                () -> service.updateAddress(12, 1, addressRequest("Testikatu 2", "Tampere")));
        verify(addressRepository, never()).save(any());
    }

    @Test
    void deleteAddressDeletesTheRow() {
        Customer customer = TestData.customer(12, "Testi", "Asiakas", "testi@example.com");
        CustomerAddress existing = TestData.customerAddress(30, customer, "Testikatu 1", "Helsinki");
        when(addressRepository.findById(30)).thenReturn(Optional.of(existing));

        service.deleteAddress(12, 30);

        verify(addressRepository).delete(existing);
    }
}
