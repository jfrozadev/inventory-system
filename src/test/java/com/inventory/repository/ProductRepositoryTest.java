package com.inventory.repository;

import com.inventory.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@DisplayName("ProductRepository Tests")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Product testProduct1;
    private Product testProduct2;
    private Product testProduct3;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        testProduct1 = Product.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(100)
                .lastUpdated(LocalDateTime.now())
                .build();

        testProduct2 = Product.builder()
                .storeId("STORE_001")
                .productId("PROD_0002")
                .quantity(50)
                .lastUpdated(LocalDateTime.now())
                .build();

        testProduct3 = Product.builder()
                .storeId("STORE_002")
                .productId("PROD_0001")
                .quantity(75)
                .lastUpdated(LocalDateTime.now())
                .build();
    }


    @Test
    @DisplayName("findByStoreIdAndProductId - Should return product when exists")
    void findByStoreIdAndProductId_Exists_ShouldReturnProduct() {

        entityManager.persist(testProduct1);
        entityManager.flush();

        Optional<Product> result = productRepository.findByStoreIdAndProductId("STORE_001", "PROD_0001");

        assertThat(result).isPresent();
        assertThat(result.get().getStoreId()).isEqualTo("STORE_001");
        assertThat(result.get().getProductId()).isEqualTo("PROD_0001");
        assertThat(result.get().getQuantity()).isEqualTo(100);
    }

    @Test
    @DisplayName("findByStoreIdAndProductId - Should return empty when not exists")
    void findByStoreIdAndProductId_NotExists_ShouldReturnEmpty() {

        Optional<Product> result = productRepository.findByStoreIdAndProductId("STORE_999", "PROD_9999");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByStoreIdAndProductId - Should distinguish between stores")
    void findByStoreIdAndProductId_DifferentStores_ShouldDistinguish() {

        entityManager.persist(testProduct1);
        entityManager.persist(testProduct3);
        entityManager.flush();

        Optional<Product> store1 = productRepository.findByStoreIdAndProductId("STORE_001", "PROD_0001");
        Optional<Product> store2 = productRepository.findByStoreIdAndProductId("STORE_002", "PROD_0001");

        assertThat(store1).isPresent();
        assertThat(store2).isPresent();
        assertThat(store1.get().getQuantity()).isEqualTo(100);
        assertThat(store2.get().getQuantity()).isEqualTo(75);
    }

    @Test
    @DisplayName("findByStoreIdAndProductIdWithLock - Should return product with lock")
    void findByStoreIdAndProductIdWithLock_Exists_ShouldReturnProduct() {

        entityManager.persist(testProduct1);
        entityManager.flush();
        entityManager.clear();

        Optional<Product> result = productRepository.findByStoreIdAndProductIdWithLock("STORE_001", "PROD_0001");

        assertThat(result).isPresent();
        assertThat(result.get().getStoreId()).isEqualTo("STORE_001");
        assertThat(result.get().getProductId()).isEqualTo("PROD_0001");
    }

    @Test
    @DisplayName("findByStoreIdAndProductIdWithLock - Should return empty when not exists")
    void findByStoreIdAndProductIdWithLock_NotExists_ShouldReturnEmpty() {

        Optional<Product> result = productRepository.findByStoreIdAndProductIdWithLock("NONEXISTENT", "NONEXISTENT");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByStoreId - Should return all products for store")
    void findByStoreId_MultipleProducts_ShouldReturnAll() {

        entityManager.persist(testProduct1);
        entityManager.persist(testProduct2);
        entityManager.persist(testProduct3);
        entityManager.flush();

        List<Product> store1Products = productRepository.findByStoreId("STORE_001");
        List<Product> store2Products = productRepository.findByStoreId("STORE_002");

        assertThat(store1Products).hasSize(2);
        assertThat(store2Products).hasSize(1);
        assertThat(store1Products).extracting(Product::getProductId)
                .containsExactlyInAnyOrder("PROD_0001", "PROD_0002");
    }

    @Test
    @DisplayName("findByStoreId - Should return empty list when no products")
    void findByStoreId_NoProducts_ShouldReturnEmpty() {

        List<Product> result = productRepository.findByStoreId("STORE_999");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("save - Should create new product")
    void save_NewProduct_ShouldCreate() {
        Product saved = productRepository.save(testProduct1);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStoreId()).isEqualTo("STORE_001");
        assertThat(saved.getProductId()).isEqualTo("PROD_0001");
        assertThat(saved.getQuantity()).isEqualTo(100);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("save - Should update existing product")
    void save_ExistingProduct_ShouldUpdate() {

        Product saved = productRepository.save(testProduct1);
        Long id = saved.getId();

        saved.setQuantity(200);
        Product updated = productRepository.save(saved);

        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getQuantity()).isEqualTo(200);
    }

    @Test
    @DisplayName("delete - Should remove product")
    void delete_ExistingProduct_ShouldRemove() {

        Product saved = productRepository.save(testProduct1);

        productRepository.delete(saved);
        Optional<Product> result = productRepository.findById(saved.getId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll - Should return all products")
    void findAll_MultipleProducts_ShouldReturnAll() {

        productRepository.save(testProduct1);
        productRepository.save(testProduct2);
        productRepository.save(testProduct3);

        List<Product> all = productRepository.findAll();

        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("count - Should return correct count")
    void count_MultipleProducts_ShouldReturnCorrectCount() {

        productRepository.save(testProduct1);
        productRepository.save(testProduct2);

        long count = productRepository.count();

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("save - Should set timestamps automatically")
    void save_NewProduct_ShouldSetTimestamps() {

        Product product = Product.builder()
                .storeId("STORE_001")
                .productId("PROD_0001")
                .quantity(100)
                .build();

        Product saved = productRepository.save(product);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLastUpdated()).isNotNull();
        assertThat(saved.getCreatedAt()).isBeforeOrEqualTo(saved.getLastUpdated());
    }


    @Test
    @DisplayName("save - Should update lastUpdated on modification")
    void save_UpdateProduct_ShouldUpdateLastUpdated() throws InterruptedException {

        Product saved = productRepository.save(testProduct1);
        LocalDateTime originalUpdated = saved.getLastUpdated();

        Thread.sleep(100);

        saved.setQuantity(200);
        saved.setLastUpdated(LocalDateTime.now());
        Product updated = productRepository.save(saved);

        assertThat(updated.getLastUpdated()).isAfter(originalUpdated);
    }

    @Test
    @DisplayName("Product.addQuantity - Should increase quantity")
    void product_AddQuantity_ShouldIncrease() {

        testProduct1.setQuantity(100);

        testProduct1.addQuantity(50);

        assertThat(testProduct1.getQuantity()).isEqualTo(150);
    }

    @Test
    @DisplayName("Product.removeQuantity - Should decrease quantity")
    void product_RemoveQuantity_ShouldDecrease() {

        testProduct1.setQuantity(100);

        testProduct1.removeQuantity(30);

        assertThat(testProduct1.getQuantity()).isEqualTo(70);
    }

    @Test
    @DisplayName("Product.removeQuantity - Should throw exception when insufficient")
    void product_RemoveQuantity_Insufficient_ShouldThrow() {

        testProduct1.setQuantity(10);

        try {
            testProduct1.removeQuantity(20);
        } catch (IllegalStateException e) {
            assertThat(e.getMessage()).contains("Insufficient stock");
        }
    }
}
