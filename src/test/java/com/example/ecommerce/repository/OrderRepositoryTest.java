package com.example.ecommerce.repository;

import com.example.ecommerce.config.JpaAuditingConfig;
import com.example.ecommerce.domain.*;
import com.example.ecommerce.domain.enums.OrderStatus;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)  // Order uses @CreatedDate
@TestPropertySource(properties = {
    "spring.jpa.properties.hibernate.generate_statistics=true"
})
class OrderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Long orderId;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("Jane", "Doe",
            "jane-" + System.nanoTime() + "@test.com", "hash");
        entityManager.persist(customer);

        Category category = new Category("Electronics");
        entityManager.persist(category);

        Product widget = new Product("Test Widget", "desc",
            new BigDecimal("29.99"), 100, category, "SKU-A-" + System.nanoTime());
        Product cable = new Product("USB Cable", "desc",
            new BigDecimal("9.99"), 100, category, "SKU-B-" + System.nanoTime());
        entityManager.persist(widget);
        entityManager.persist(cable);

        Address address = new Address(customer,
            "1 Test St", "Sydney", "NSW", "2000", "AU");
        entityManager.persist(address);

        Order order = new Order(customer, address,
            new BigDecimal("69.97"), "idem-" + System.nanoTime());
        order.getItems().add(new OrderItem(order, widget, 2));
        order.getItems().add(new OrderItem(order, cable, 1));
        entityManager.persist(order);
        entityManager.flush();

        orderId = order.getId();
        entityManager.clear();  // detach — forces real DB round-trips
    }

    @Test
    @DisplayName("findByIdWithItems loads graph with minimal SQL (vs naive N+1)")
    void findByIdWithItems_shouldFetchGraphInSingleQuery() {
        Statistics stats = statistics();
        stats.clear();

        Order order = orderRepository.findByIdWithItems(orderId).orElseThrow();
        assertThat(order.getItems()).hasSize(2);
        order.getItems().forEach(item ->
            assertThat(item.getProduct().getName()).isNotBlank());

        assertThat(stats.getQueryExecutionCount())
            .as("One JPQL fetch-join query")
            .isEqualTo(1);
        assertThat(countSqlStatements(stats))
            .as("JDBC count is small (Hibernate may use 2 statements for collection fetch)")
            .isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("findById causes N+1 when loading items and products")
    void findById_withoutFetchJoin_triggersNPlusOneQueries() {
        Statistics stats = statistics();
        stats.clear();

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getItems()).hasSize(2);
        order.getItems().forEach(item ->
            assertThat(item.getProduct().getName()).isNotBlank());

        // 1 (order) + 1 (items) + 2 (products) = 4 JDBC statements
        assertThat(countSqlStatements(stats))
            .as("Naive load should issue multiple SQL statements (N+1)")
            .isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("fetch-join uses fewer SQL statements than naive findById")
    void findByIdWithItems_shouldUseFewerQueriesThanFindById() {
        Statistics stats = statistics();

        stats.clear();
        Order fetched = orderRepository.findByIdWithItems(orderId).orElseThrow();
        fetched.getItems().forEach(i -> i.getProduct().getName());
        long fetchJoinSql = countSqlStatements(stats);

        entityManager.clear();

        stats.clear();
        Order naive = orderRepository.findById(orderId).orElseThrow();
        naive.getItems().forEach(i -> i.getProduct().getName());
        long naiveSql = countSqlStatements(stats);

        assertThat(fetchJoinSql)
            .as("fetch-join path")
            .isLessThan(naiveSql);
        assertThat(naiveSql)
            .as("naive path")
            .isGreaterThanOrEqualTo(4);
    }

    private Statistics statistics() {
        SessionFactory sessionFactory =
            entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        return stats;
    }

    private long countSqlStatements(Statistics stats) {
        return stats.getPrepareStatementCount();
    }
}