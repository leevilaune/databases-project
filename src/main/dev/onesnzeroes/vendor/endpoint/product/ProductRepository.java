package dev.onesnzeroes.vendor.endpoint.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findBySupplierIdOrderByNameAsc(Integer supplierId);
    List<Product> findByCategoryIdOrderByNameAsc(Integer categoryId);
    List<Product> findBySupplierIdAndCategoryIdOrderByNameAsc(Integer supplierId, Integer categoryId);
    List<Product> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Integer> ids);
}
