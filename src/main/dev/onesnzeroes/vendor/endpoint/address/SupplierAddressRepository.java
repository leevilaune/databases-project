package dev.onesnzeroes.vendor.endpoint.address;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierAddressRepository extends JpaRepository<SupplierAddress, Integer> {

    Optional<SupplierAddress> findBySupplierId(Integer supplierId);
}
