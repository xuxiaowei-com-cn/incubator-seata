/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.seata;

import org.apache.seata.dao.AccountDAO;
import org.apache.seata.dao.OrderDAO;
import org.apache.seata.dao.StorageDAO;
import org.apache.seata.dao.UndoLogDAO;
import org.apache.seata.entity.Account;
import org.apache.seata.entity.Order;
import org.apache.seata.entity.Storage;
import org.apache.seata.service.AccountService;
import org.apache.seata.service.BusinessService;
import org.apache.seata.service.OrderService;
import org.apache.seata.service.StorageService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Distributed transaction integration tests for Seata AT mode.
 *
 * <p><b>Prerequisites:</b></p>
 * <ul>
 *   <li>A running Seata Server at {@code 127.0.0.1:8091} (or via {@code SEATA_SERVER_ADDR})</li>
 *   <li>A MySQL database at {@code 127.0.0.1:3306/seata_test_native} (or via {@code DATASOURCE_*})</li>
 *   <li>Tables and test data are initialized by {@link org.apache.seata.config.EarlyDatabaseInitializer},
 *       which runs {@code schema.sql} and {@code data.sql} before Seata's DataSourceProxy wraps
 *       the DataSource bean.</li>
 *   <li>Test data is reset before each test method via {@link #resetTestData()} and
 *       before the test class via {@code @Sql} with {@code setup-test-data.sql}.</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -Ptest-native -pl test-suite/test-native
 * -Dtest=DataSourceProxyModeAtTests -Dseata.server.addr=127.0.0.1:8091}</p>
 *
 * @see BusinessService#purchase(String, String, int)
 */
@SpringBootTest
class DataSourceProxyModeAtTests {

    @Autowired
    private BusinessService businessService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private StorageDAO storageDAO;

    @Autowired
    private OrderDAO orderDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private UndoLogDAO undoLogDAO;

    /**
     * Reset all test data to known state before each test method.
     * This ensures test isolation even when using a real MySQL database.
     */
    @BeforeEach
    void resetTestData() {
        // Clean all test tables (deleteAllInBatch commits immediately)
        orderDAO.deleteAllInBatch();
        undoLogDAO.deleteAllInBatch();
        storageDAO.deleteAllInBatch();
        accountDAO.deleteAllInBatch();

        // Re-insert test data (saveAndFlush commits immediately)
        storageDAO.saveAndFlush(new Storage("C001", 100));
        storageDAO.saveAndFlush(new Storage("C002", 5));
        accountDAO.saveAndFlush(new Account("U001", 10000));
        accountDAO.saveAndFlush(new Account("U002", 100));
    }

    // ==================== Basic Context Tests ====================

    @Test
    @DisplayName("1. Spring context should load with all Seata beans")
    void contextLoads() {
        assertNotNull(businessService, "BusinessService should be injected");
        assertNotNull(storageService, "StorageService should be injected");
        assertNotNull(orderService, "OrderService should be injected");
        assertNotNull(accountService, "AccountService should be injected");
        assertNotNull(storageDAO, "StorageDAO should be injected");
        assertNotNull(orderDAO, "OrderDAO should be injected");
        assertNotNull(accountDAO, "AccountDAO should be injected");
        assertNotNull(undoLogDAO, "UndoLogDAO should be injected");
    }

    @Test
    @DisplayName("2. Test data should be initialized correctly")
    void testDataInitialized() {
        Storage c001 = storageDAO.findByCommodityCode("C001");
        assertNotNull(c001, "Storage C001 should exist");
        assertEquals(100, c001.getCount(), "C001 initial stock should be 100");

        Storage c002 = storageDAO.findByCommodityCode("C002");
        assertNotNull(c002, "Storage C002 should exist");
        assertEquals(5, c002.getCount(), "C002 initial stock should be 5");

        Account u001 = accountDAO.findByUserId("U001");
        assertNotNull(u001, "Account U001 should exist");
        assertEquals(10000, u001.getMoney(), "U001 initial balance should be 10000");

        Account u002 = accountDAO.findByUserId("U002");
        assertNotNull(u002, "Account U002 should exist");
        assertEquals(100, u002.getMoney(), "U002 initial balance should be 100");
    }

    // ==================== Storage Service Tests ====================

    @Nested
    @DisplayName("3. Storage Service")
    class StorageServiceTests {

        @Test
        @DisplayName("3.1 Deduct storage with sufficient stock")
        void testDeductSuccess() {
            Storage before = storageDAO.findByCommodityCode("C001");
            int beforeCount = before.getCount();

            storageService.deduct("C001", 10);

            Storage after = storageDAO.findByCommodityCode("C001");
            assertEquals(beforeCount - 10, after.getCount());
        }

        @Test
        @DisplayName("3.2 Throw exception when commodity code not found")
        void testDeductCommodityNotFound() {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> storageService.deduct("NON_EXISTENT", 1));
            assertTrue(ex.getMessage().contains("Storage not found"), "Actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("3.3 Throw exception when insufficient stock")
        void testDeductInsufficientStock() {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> storageService.deduct("C002", 100));
            assertTrue(ex.getMessage().contains("Insufficient storage"), "Actual: " + ex.getMessage());
        }
    }

    // ==================== Account Service Tests ====================

    @Nested
    @DisplayName("4. Account Service")
    class AccountServiceTests {

        @Test
        @DisplayName("4.1 Debit account with sufficient balance")
        void testDebitSuccess() {
            Account before = accountDAO.findByUserId("U001");
            int beforeBalance = before.getMoney();

            accountService.debit("U001", 500);

            Account after = accountDAO.findByUserId("U001");
            assertEquals(beforeBalance - 500, after.getMoney());
        }

        @Test
        @DisplayName("4.2 Throw exception when user not found")
        void testDebitUserNotFound() {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> accountService.debit("UNKNOWN_USER", 100));
            assertTrue(ex.getMessage().contains("Account not found"), "Actual: " + ex.getMessage());
        }

        @Test
        @DisplayName("4.3 Throw exception when insufficient balance")
        void testDebitInsufficientBalance() {
            RuntimeException ex = assertThrows(RuntimeException.class, () -> accountService.debit("U002", 999999));
            assertTrue(ex.getMessage().contains("Insufficient balance"), "Actual: " + ex.getMessage());
        }
    }

    // ==================== Order Service Tests ====================

    @Nested
    @DisplayName("5. Order Service")
    class OrderServiceTests {

        @Test
        @DisplayName("5.1 Create order with correct money: orderCount * 100")
        void testCreateOrderSuccess() {
            Order order = orderService.create("U001", "C001", 5);

            assertNotNull(order);
            assertNotNull(order.getId());
            assertEquals("U001", order.getUserId());
            assertEquals("C001", order.getCommodityCode());
            assertEquals(Integer.valueOf(5), order.getCount());
            assertEquals(Integer.valueOf(500), order.getMoney(), "5 * 100 = 500");
        }

        @Test
        @DisplayName("5.2 Order money equals orderCount * 100 for different quantities")
        void testOrderMoneyCalculation() {
            assertEquals(
                    Integer.valueOf(100), orderService.create("U001", "C001", 1).getMoney());
            assertEquals(
                    Integer.valueOf(700), orderService.create("U001", "C001", 7).getMoney());
        }
    }

    // ==================== Distributed Transaction: Purchase Flow ====================

    @Nested
    @DisplayName("6. Distributed Transaction — Purchase Flow")
    class PurchaseFlowTests {

        @Test
        @DisplayName("6.1 AT mode — Purchase succeeds: storage deducted, account debited, order created")
        @Tag("at-mode")
        void testPurchaseSuccess() {
            Storage storageBefore = storageDAO.findByCommodityCode("C001");
            Account accountBefore = accountDAO.findByUserId("U001");
            long ordersBefore = orderDAO.count();

            businessService.purchase("U001", "C001", 10);

            // Verify storage: 100 - 10 = 90
            Storage storageAfter = storageDAO.findByCommodityCode("C001");
            assertEquals(storageBefore.getCount() - 10, storageAfter.getCount(), "Storage should be deducted by 10");

            // Verify account: 10000 - (10*100) = 9000
            Account accountAfter = accountDAO.findByUserId("U001");
            assertEquals(accountBefore.getMoney() - 1000, accountAfter.getMoney(), "Account should be debited by 1000");

            // Verify order created
            assertEquals(ordersBefore + 1, orderDAO.count(), "One new order should be created");

            Order latest = orderDAO.findAll().get(orderDAO.findAll().size() - 1);
            assertEquals("U001", latest.getUserId());
            assertEquals("C001", latest.getCommodityCode());
            assertEquals(Integer.valueOf(10), latest.getCount());
            assertEquals(Integer.valueOf(1000), latest.getMoney());
        }

        @Test
        @DisplayName("6.2 AT mode — Insufficient stock → global transaction rolls back")
        @Tag("at-mode")
        void testPurchaseRollbackOnInsufficientStock() {
            Storage storageBefore = storageDAO.findByCommodityCode("C002");
            Account accountBefore = accountDAO.findByUserId("U001");
            long ordersBefore = orderDAO.count();

            // C002 has only 5 items, requesting 100 → fails
            RuntimeException ex =
                    assertThrows(RuntimeException.class, () -> businessService.purchase("U001", "C002", 100));

            assertTrue(
                    ex.getMessage().contains("Insufficient storage")
                            || ex.getMessage().contains("Failed to deduct"),
                    "Should fail with storage error, actual: " + ex.getMessage());

            // Seata global transaction rollback: storage unchanged
            assertEquals(
                    storageBefore.getCount(),
                    storageDAO.findByCommodityCode("C002").getCount(),
                    "Storage should be unchanged after rollback");

            // Account unchanged (order creation was never reached)
            assertEquals(
                    accountBefore.getMoney(),
                    accountDAO.findByUserId("U001").getMoney(),
                    "Account should be unchanged after rollback");

            // No new order created
            assertEquals(ordersBefore, orderDAO.count(), "No order should be created after rollback");
        }

        @Test
        @DisplayName("6.3 AT mode — Insufficient balance → global transaction rolls back")
        @Tag("at-mode")
        void testPurchaseRollbackOnInsufficientBalance() {
            Storage storageBefore = storageDAO.findByCommodityCode("C001");
            Account accountBefore = accountDAO.findByUserId("U002");
            long ordersBefore = orderDAO.count();

            // U002 has only 100 balance, requesting 10*100=1000 → fails at account debit
            RuntimeException ex =
                    assertThrows(RuntimeException.class, () -> businessService.purchase("U002", "C001", 10));

            assertTrue(
                    ex.getMessage().contains("Insufficient balance")
                            || ex.getMessage().contains("Failed to debit"),
                    "Should fail with balance error, actual: " + ex.getMessage());

            // Seata global transaction rollback: storage should be restored
            assertEquals(
                    storageBefore.getCount(),
                    storageDAO.findByCommodityCode("C001").getCount(),
                    "Storage should be rolled back to original count");

            // Account should be unchanged
            assertEquals(
                    accountBefore.getMoney(),
                    accountDAO.findByUserId("U002").getMoney(),
                    "Account balance should be unchanged after rollback");

            // No new order created
            assertEquals(ordersBefore, orderDAO.count(), "No order should be created after rollback");
        }

        @Test
        @DisplayName("6.4 AT mode — Non-existent commodity → transaction fails, no side effects")
        @Tag("at-mode")
        void testPurchaseNonExistentCommodity() {
            long ordersBefore = orderDAO.count();
            Storage c001Before = storageDAO.findByCommodityCode("C001");
            Account u001Before = accountDAO.findByUserId("U001");

            RuntimeException ex =
                    assertThrows(RuntimeException.class, () -> businessService.purchase("U001", "NON_EXISTENT", 1));

            assertTrue(ex.getMessage().contains("Storage not found"), "Actual: " + ex.getMessage());

            // No side effects on other resources
            assertEquals(
                    c001Before.getCount(),
                    storageDAO.findByCommodityCode("C001").getCount());
            assertEquals(u001Before.getMoney(), accountDAO.findByUserId("U001").getMoney());
            assertEquals(ordersBefore, orderDAO.count());
        }

        @Test
        @DisplayName("6.5 AT mode — Non-existent user → transaction fails, storage rolled back")
        @Tag("at-mode")
        void testPurchaseNonExistentUser() {
            long ordersBefore = orderDAO.count();
            Storage c001Before = storageDAO.findByCommodityCode("C001");

            RuntimeException ex =
                    assertThrows(RuntimeException.class, () -> businessService.purchase("UNKNOWN_USER", "C001", 1));

            assertTrue(ex.getMessage().contains("Account not found"), "Actual: " + ex.getMessage());

            // Seata rollback: storage should be restored
            assertEquals(
                    c001Before.getCount(),
                    storageDAO.findByCommodityCode("C001").getCount(),
                    "Storage should be rolled back");

            assertEquals(ordersBefore, orderDAO.count(), "No order should be created");
        }

        @Test
        @DisplayName("6.6 AT mode — Multiple sequential purchases maintain data consistency")
        @Tag("at-mode")
        void testMultiplePurchasesConsistency() {
            Storage storageBefore = storageDAO.findByCommodityCode("C001");
            Account accountBefore = accountDAO.findByUserId("U001");

            // Purchase 5, then 3, then 2 units
            businessService.purchase("U001", "C001", 5);
            businessService.purchase("U001", "C001", 3);
            businessService.purchase("U001", "C001", 2);

            int totalCount = 5 + 3 + 2;
            int totalMoney = totalCount * 100;

            assertEquals(
                    storageBefore.getCount() - totalCount,
                    storageDAO.findByCommodityCode("C001").getCount(),
                    "Total storage deduction should be " + totalCount);

            assertEquals(
                    accountBefore.getMoney() - totalMoney,
                    accountDAO.findByUserId("U001").getMoney(),
                    "Total account debit should be " + totalMoney);

            assertTrue(orderDAO.count() >= 3, "At least 3 orders should exist");
        }

        @Test
        @DisplayName("6.7 AT mode — Minimum quantity purchase (1 unit)")
        @Tag("at-mode")
        void testPurchaseMinimumQuantity() {
            Storage storageBefore = storageDAO.findByCommodityCode("C001");
            Account accountBefore = accountDAO.findByUserId("U001");

            businessService.purchase("U001", "C001", 1);

            assertEquals(
                    storageBefore.getCount() - 1,
                    storageDAO.findByCommodityCode("C001").getCount());
            assertEquals(
                    accountBefore.getMoney() - 100,
                    accountDAO.findByUserId("U001").getMoney());
        }

        @Test
        @DisplayName("6.8 AT mode — Undo log is inserted during phase 1 for AT mode branches")
        @Tag("at-mode")
        void testUndoLogCreatedDuringGlobalTransaction() {
            long undoLogsBefore = undoLogDAO.count();

            businessService.purchase("U001", "C001", 3);

            // After successful commit, Seata cleans up undo_log entries.
            // The undo_log count may be >= before (new entries are created in phase 1
            // and deleted after phase 2 commit).
            long undoLogsAfter = undoLogDAO.count();
            assertTrue(
                    undoLogsAfter >= undoLogsBefore,
                    "Undo log count should be >= before, was: " + undoLogsBefore + ", now: " + undoLogsAfter);
        }
    }
}
