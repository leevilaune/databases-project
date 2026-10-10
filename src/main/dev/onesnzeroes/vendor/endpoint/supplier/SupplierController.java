package dev.onesnzeroes.vendor.endpoint.supplier;

import dev.onesnzeroes.vendor.endpoint.address.SupplierAddress;
import dev.onesnzeroes.vendor.endpoint.address.dto.AddressRequest;
import dev.onesnzeroes.vendor.endpoint.common.DeleteResponse;
import dev.onesnzeroes.vendor.endpoint.supplier.dto.SupplierRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService supplierService;

    public SupplierController(SupplierService supplierService) {
        this.supplierService = supplierService;
    }

    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }

    @GetMapping("/{id}")
    public Supplier getSupplier(@PathVariable Integer id) {
        return supplierService.getSupplierById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Supplier createSupplier(@Valid @RequestBody SupplierRequest request) {
        return supplierService.createSupplier(request);
    }

    @PutMapping("/{id}")
    public Supplier updateSupplier(@PathVariable Integer id, @Valid @RequestBody SupplierRequest request) {
        return supplierService.updateSupplier(id, request);
    }

    @DeleteMapping("/{id}")
    public DeleteResponse deleteSupplier(@PathVariable Integer id) {
        supplierService.deleteSupplier(id);
        return new DeleteResponse("Supplier deleted successfully", id);
    }

    @GetMapping("/{id}/address")
    public SupplierAddress getAddress(@PathVariable Integer id) {
        return supplierService.getAddress(id);
    }

    @PutMapping("/{id}/address")
    public SupplierAddress putAddress(@PathVariable Integer id, @Valid @RequestBody AddressRequest request) {
        return supplierService.putAddress(id, request);
    }

    @DeleteMapping("/{id}/address")
    public DeleteResponse deleteAddress(@PathVariable Integer id) {
        SupplierAddress address = supplierService.getAddress(id);
        supplierService.deleteAddress(id);
        return new DeleteResponse("Supplier address deleted successfully", address.getId());
    }
}
