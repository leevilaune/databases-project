package dev.onesnzeroes.vendor.endpoint.supplier;

import dev.onesnzeroes.vendor.TestData;
import dev.onesnzeroes.vendor.endpoint.address.SupplierAddress;
import dev.onesnzeroes.vendor.endpoint.address.SupplierAddressRepository;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.supplier.dto.SupplierRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierAddressRepository addressRepository;

    private SupplierService service;

    @BeforeEach
    void setUp() {
        service = new SupplierService(supplierRepository, addressRepository);
    }

    private static SupplierRequest request(String name) {
        SupplierRequest request = new SupplierRequest();
        request.setName(name);
        request.setContactName("Maija");
        request.setPhone("0401234567");
        request.setEmail("vendor@example.com");
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
    void getAllSuppliersReturnsRepositoryRows() {
        List<Supplier> suppliers = List.of(TestData.supplier(1, "A"), TestData.supplier(2, "B"));
        when(supplierRepository.findAll()).thenReturn(suppliers);

        assertSame(suppliers, service.getAllSuppliers());
    }

    @Test
    void getSupplierByIdReturnsSupplier() {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(supplier));

        assertSame(supplier, service.getSupplierById(5));
    }

    @Test
    void getSupplierByIdUnknownIs404() {
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier not found", () -> service.getSupplierById(999));
    }

    @Test
    void createSupplierTrimsTheNameAndCopiesFields() {
        when(supplierRepository.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        Supplier created = service.createSupplier(request("  Test Vendor  "));

        assertEquals("Test Vendor", created.getName());
        assertEquals("Maija", created.getContactName());
        assertEquals("0401234567", created.getPhone());
        assertEquals("vendor@example.com", created.getEmail());
        assertNull(created.getId());
    }

    @Test
    void updateSupplierChangesTheExistingRow() {
        Supplier existing = TestData.supplier(5, "Old");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(existing));
        when(supplierRepository.save(existing)).thenReturn(existing);

        SupplierRequest request = request("New");
        request.setContactName("Updated Contact");
        Supplier updated = service.updateSupplier(5, request);

        assertSame(existing, updated);
        assertEquals("New", updated.getName());
        assertEquals("Updated Contact", updated.getContactName());
    }

    @Test
    void updateUnknownSupplierIs404AndSavesNothing() {
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier not found",
                () -> service.updateSupplier(999, request("X")));
        verify(supplierRepository, never()).save(any());
    }

    @Test
    void deleteSupplierDeletesTheRow() {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(supplier));

        service.deleteSupplier(5);

        verify(supplierRepository).delete(supplier);
    }

    @Test
    void deleteUnknownSupplierIs404() {
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier not found", () -> service.deleteSupplier(999));
        verify(supplierRepository, never()).delete(any());
    }

    @Test
    void getAddressOfUnknownSupplierIs404() {
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier not found", () -> service.getAddress(999));
    }

    @Test
    void getAddressWhenThereIsNoneIs404() {
        when(supplierRepository.findById(5)).thenReturn(Optional.of(TestData.supplier(5, "Test Vendor")));
        when(addressRepository.findBySupplierId(5)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier has no address", () -> service.getAddress(5));
    }

    @Test
    void putAddressCreatesANewAddressForTheSupplier() {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(supplier));
        when(addressRepository.findBySupplierId(5)).thenReturn(Optional.empty());
        when(addressRepository.save(any(SupplierAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierAddress address = service.putAddress(5, addressRequest("Testikatu 1", "Espoo"));

        assertSame(supplier, address.getSupplier());
        assertNull(address.getId());
        assertEquals("Testikatu 1", address.getStreetAddress());
        assertEquals("Espoo", address.getCity());
        assertEquals("00100", address.getPostalCode());
        assertEquals("Finland", address.getCountry());
    }

    @Test
    void putAddressAgainReplacesTheSameRow() {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        SupplierAddress existing = TestData.supplierAddress(7, supplier, "Testikatu 1", "Espoo");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(supplier));
        when(addressRepository.findBySupplierId(5)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(SupplierAddress.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierAddress address = service.putAddress(5, addressRequest("Uusikatu 2", "Helsinki"));

        ArgumentCaptor<SupplierAddress> captor = ArgumentCaptor.forClass(SupplierAddress.class);
        verify(addressRepository).save(captor.capture());
        assertSame(existing, captor.getValue());
        assertEquals(7, address.getId());
        assertEquals("Uusikatu 2", address.getStreetAddress());
        assertEquals("Helsinki", address.getCity());
    }

    @Test
    void putAddressOfUnknownSupplierIs404() {
        when(supplierRepository.findById(999)).thenReturn(Optional.empty());

        TestData.assertStatus(HttpStatus.NOT_FOUND, "Supplier not found",
                () -> service.putAddress(999, addressRequest("Uusikatu 2", "Helsinki")));
        verify(addressRepository, never()).save(any());
    }

    @Test
    void deleteAddressDeletesTheRow() {
        Supplier supplier = TestData.supplier(5, "Test Vendor");
        SupplierAddress existing = TestData.supplierAddress(7, supplier, "Uusikatu 2", "Helsinki");
        when(supplierRepository.findById(5)).thenReturn(Optional.of(supplier));
        when(addressRepository.findBySupplierId(5)).thenReturn(Optional.of(existing));

        service.deleteAddress(5);

        verify(addressRepository).delete(existing);
    }
}
