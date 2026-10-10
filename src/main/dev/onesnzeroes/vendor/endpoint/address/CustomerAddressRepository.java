package dev.onesnzeroes.vendor.endpoint.address;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Integer> {

    List<CustomerAddress> findByCustomerIdOrderByIdAsc(Integer customerId);
}
