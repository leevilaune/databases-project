package dev.onesnzeroes.vendor.endpoint.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    @Override
    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(Integer id);

    @EntityGraph(attributePaths = "category")
    List<Product> findBySupplierIdOrderByNameAsc(Integer supplierId);

    @EntityGraph(attributePaths = "category")
    List<Product> findByCategoryIdOrderByNameAsc(Integer categoryId);

    @EntityGraph(attributePaths = "category")
    List<Product> findBySupplierIdAndCategoryIdOrderByNameAsc(Integer supplierId, Integer categoryId);

    @EntityGraph(attributePaths = "category")
    List<Product> findAllByOrderByNameAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Integer> ids);
}
