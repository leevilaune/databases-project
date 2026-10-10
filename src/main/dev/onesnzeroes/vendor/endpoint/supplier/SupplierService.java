package dev.onesnzeroes.vendor.endpoint.supplier;

import dev.onesnzeroes.vendor.endpoint.address.SupplierAddress;
import dev.onesnzeroes.vendor.endpoint.address.SupplierAddressRepository;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.supplier.dto.SupplierRequest;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierAddressRepository addressRepository;

    public SupplierService(SupplierRepository supplierRepository, SupplierAddressRepository addressRepository) {
        this.supplierRepository = supplierRepository;
        this.addressRepository = addressRepository;
    }

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public Supplier getSupplierById(Integer id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier not found"));
    }

    public Supplier createSupplier(SupplierRequest request) {
        Supplier supplier = new Supplier();
        apply(supplier, request);
        return supplierRepository.save(supplier);
    }

    @Transactional
    public Supplier updateSupplier(Integer id, SupplierRequest request) {
        Supplier supplier = getSupplierById(id);
        apply(supplier, request);
        return supplierRepository.save(supplier);
    }

    public void deleteSupplier(Integer id) {
        supplierRepository.delete(getSupplierById(id));
    }

    public SupplierAddress getAddress(Integer supplierId) {
        getSupplierById(supplierId);
        return addressRepository.findBySupplierId(supplierId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Supplier has no address"));
    }

    @Transactional
    public SupplierAddress putAddress(Integer supplierId, AddressRequest request) {
        Supplier supplier = getSupplierById(supplierId);
        SupplierAddress address = addressRepository.findBySupplierId(supplierId).orElseGet(() -> {
            SupplierAddress a = new SupplierAddress();
            a.setSupplier(supplier);
            return a;
        });
        address.setStreetAddress(request.getStreetAddress());
        address.setPostalCode(request.getPostalCode());
        address.setCity(request.getCity());
        address.setCountry(request.getCountry());
        return addressRepository.save(address);
    }

    public void deleteAddress(Integer supplierId) {
        addressRepository.delete(getAddress(supplierId));
    }

    private static void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName().trim());
        supplier.setContactName(request.getContactName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
    }
}
