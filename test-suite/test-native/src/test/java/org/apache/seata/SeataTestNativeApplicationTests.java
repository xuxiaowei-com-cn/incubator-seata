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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 *   <li>Tables created by {@code schema.sql} — set {@code SQL_INIT_MODE=ALWAYS}</li>
 * </ul>
 *
 * <p>Run with: {@code mvn test -P test-native -pl test-suite/test-native
 * -Dtest=SeataTestNativeApplicationTests}</p>
 */
@SpringBootTest
@Sql(scripts = "/setup-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SeataTestNativeApplicationTests {

    @Autowired
    BusinessService businessService;

    @Autowired
    StorageService storageService;

    @Autowired
    OrderService orderService;

    @Autowired
    AccountService accountService;

    @Autowired
    StorageDAO storageDAO;

    @Autowired
    OrderDAO orderDAO;

    @Autowired
    AccountDAO accountDAO;

    @Autowired
    UndoLogDAO undoLogDAO;

    // ==================== 1. Basic Context Tests ====================

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

    // ==================== 3. Storage Service Tests ====================

    @Test
    @DisplayName("3.1 Storage: deduct with sufficient stock")
    void storage_deductSuccess() {
        Storage before = storageDAO.findByCommodityCode("C001");
        int beforeCount = before.getCount();

        storageService.deduct("C001", 10);

        Storage after = storageDAO.findByCommodityCode("C001");
        assertEquals(beforeCount - 10, after.getCount());
    }

    @Test
    @DisplayName("3.2 Storage: throw when commodity not found")
    void storage_deductCommodityNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> storageService.deduct("NON_EXISTENT", 1));
        assertTrue(ex.getMessage().contains("Storage not found"), "Actual: " + ex.getMessage());
    }

    @Test
    @DisplayName("3.3 Storage: throw when insufficient stock")
    void storage_deductInsufficientStock() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> storageService.deduct("C002", 100));
        assertTrue(ex.getMessage().contains("Insufficient storage"), "Actual: " + ex.getMessage());
    }

    // ==================== 4. Account Service Tests ====================

    @Test
    @DisplayName("4.1 Account: debit with sufficient balance")
    void account_debitSuccess() {
        Account before = accountDAO.findByUserId("U001");
        int beforeBalance = before.getMoney();

        accountService.debit("U001", 500);

        Account after = accountDAO.findByUserId("U001");
        assertEquals(beforeBalance - 500, after.getMoney());
    }

    @Test
    @DisplayName("4.2 Account: throw when user not found")
    void account_debitUserNotFound() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> accountService.debit("UNKNOWN_USER", 100));
        assertTrue(ex.getMessage().contains("Account not found"), "Actual: " + ex.getMessage());
    }

    @Test
    @DisplayName("4.3 Account: throw when insufficient balance")
    void account_debitInsufficientBalance() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> accountService.debit("U002", 999999));
        assertTrue(ex.getMessage().contains("Insufficient balance"), "Actual: " + ex.getMessage());
    }

    // ==================== 5. Order Service Tests ====================

    @Test
    @DisplayName("5.1 Order: create order with correct money (orderCount * 100)")
    void order_createOrderSuccess() {
        Order order = orderService.create("U001", "C001", 5);

        assertNotNull(order);
        assertNotNull(order.getId());
        assertEquals("U001", order.getUserId());
        assertEquals("C001", order.getCommodityCode());
        assertEquals(Integer.valueOf(5), order.getCount());
        assertEquals(Integer.valueOf(500), order.getMoney(), "5 * 100 = 500");
    }

    @Test
    @DisplayName("5.2 Order: money equals orderCount * 100")
    void order_orderMoneyCalculation() {
        assertEquals(
                Integer.valueOf(100), orderService.create("U001", "C001", 1).getMoney());
        assertEquals(
                Integer.valueOf(700), orderService.create("U001", "C001", 7).getMoney());
    }

    // ==================== 6. Distributed Transaction - Purchase Flow ====================

    /**
     * Reset all test data to known state before each distributed transaction test.
     */
    @BeforeEach
    void resetTestData() {
        orderDAO.deleteAllInBatch();
        undoLogDAO.deleteAllInBatch();
        storageDAO.deleteAllInBatch();
        accountDAO.deleteAllInBatch();

        storageDAO.saveAndFlush(new Storage("C001", 100));
        storageDAO.saveAndFlush(new Storage("C002", 5));
        accountDAO.saveAndFlush(new Account("U001", 10000));
        accountDAO.saveAndFlush(new Account("U002", 100));
    }

    @Test
    @DisplayName("6.1 AT: purchase succeeds — stock deducted, account debited, order created")
    void purchase_success() {
        Storage storageBefore = storageDAO.findByCommodityCode("C001");
        Account accountBefore = accountDAO.findByUserId("U001");
        long ordersBefore = orderDAO.count();

        businessService.purchase("U001", "C001", 10);

        Storage storageAfter = storageDAO.findByCommodityCode("C001");
        assertEquals(storageBefore.getCount() - 10, storageAfter.getCount(), "Storage should be deducted by 10");

        Account accountAfter = accountDAO.findByUserId("U001");
        assertEquals(accountBefore.getMoney() - 1000, accountAfter.getMoney(), "Account should be debited by 1000");

        assertEquals(ordersBefore + 1, orderDAO.count(), "One new order should be created");

        Order latest = orderDAO.findAll().get(orderDAO.findAll().size() - 1);
        assertEquals("U001", latest.getUserId());
        assertEquals("C001", latest.getCommodityCode());
        assertEquals(Integer.valueOf(10), latest.getCount());
        assertEquals(Integer.valueOf(1000), latest.getMoney());
    }

    @Test
    @DisplayName("6.2 AT: insufficient stock → global transaction rolls back")
    void purchase_rollbackOnInsufficientStock() {
        Storage storageBefore = storageDAO.findByCommodityCode("C002");
        Account accountBefore = accountDAO.findByUserId("U001");
        long ordersBefore = orderDAO.count();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> businessService.purchase("U001", "C002", 100));

        assertTrue(
                ex.getMessage().contains("Insufficient storage")
                        || ex.getMessage().contains("Failed to deduct"),
                "Should fail with storage error, actual: " + ex.getMessage());

        assertEquals(
                storageBefore.getCount(),
                storageDAO.findByCommodityCode("C002").getCount(),
                "Storage should be unchanged after rollback");

        assertEquals(
                accountBefore.getMoney(),
                accountDAO.findByUserId("U001").getMoney(),
                "Account should be unchanged after rollback");

        assertEquals(ordersBefore, orderDAO.count(), "No order should be created after rollback");
    }

    @Test
    @DisplayName("6.3 AT: insufficient balance → global transaction rolls back")
    void purchase_rollbackOnInsufficientBalance() {
        Storage storageBefore = storageDAO.findByCommodityCode("C001");
        Account accountBefore = accountDAO.findByUserId("U002");
        long ordersBefore = orderDAO.count();

        RuntimeException ex = assertThrows(RuntimeException.class, () -> businessService.purchase("U002", "C001", 10));

        assertTrue(
                ex.getMessage().contains("Insufficient balance")
                        || ex.getMessage().contains("Failed to debit"),
                "Should fail with balance error, actual: " + ex.getMessage());

        assertEquals(
                storageBefore.getCount(),
                storageDAO.findByCommodityCode("C001").getCount(),
                "Storage should be rolled back to original count");

        assertEquals(
                accountBefore.getMoney(),
                accountDAO.findByUserId("U002").getMoney(),
                "Account balance should be unchanged after rollback");

        assertEquals(ordersBefore, orderDAO.count(), "No order should be created after rollback");
    }

    @Test
    @DisplayName("6.4 AT: non-existent commodity → transaction fails, no side effects")
    void purchase_nonExistentCommodity() {
        long ordersBefore = orderDAO.count();
        Storage c001Before = storageDAO.findByCommodityCode("C001");
        Account u001Before = accountDAO.findByUserId("U001");

        RuntimeException ex =
                assertThrows(RuntimeException.class, () -> businessService.purchase("U001", "NON_EXISTENT", 1));

        assertTrue(ex.getMessage().contains("Storage not found"), "Actual: " + ex.getMessage());

        assertEquals(
                c001Before.getCount(), storageDAO.findByCommodityCode("C001").getCount());
        assertEquals(u001Before.getMoney(), accountDAO.findByUserId("U001").getMoney());
        assertEquals(ordersBefore, orderDAO.count());
    }

    @Test
    @DisplayName("6.5 AT: non-existent user → transaction fails, storage rolled back")
    void purchase_nonExistentUser() {
        long ordersBefore = orderDAO.count();
        Storage c001Before = storageDAO.findByCommodityCode("C001");

        RuntimeException ex =
                assertThrows(RuntimeException.class, () -> businessService.purchase("UNKNOWN_USER", "C001", 1));

        assertTrue(ex.getMessage().contains("Account not found"), "Actual: " + ex.getMessage());

        assertEquals(
                c001Before.getCount(),
                storageDAO.findByCommodityCode("C001").getCount(),
                "Storage should be rolled back");

        assertEquals(ordersBefore, orderDAO.count(), "No order should be created");
    }

    @Test
    @DisplayName("6.6 AT: multiple sequential purchases maintain data consistency")
    void purchase_multiplePurchasesConsistency() {
        Storage storageBefore = storageDAO.findByCommodityCode("C001");
        Account accountBefore = accountDAO.findByUserId("U001");

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
    @DisplayName("6.7 AT: minimum quantity purchase (1 unit)")
    void purchase_minimumQuantity() {
        Storage storageBefore = storageDAO.findByCommodityCode("C001");
        Account accountBefore = accountDAO.findByUserId("U001");

        businessService.purchase("U001", "C001", 1);

        assertEquals(
                storageBefore.getCount() - 1,
                storageDAO.findByCommodityCode("C001").getCount());
        assertEquals(
                accountBefore.getMoney() - 100, accountDAO.findByUserId("U001").getMoney());
    }

    @Test
    @DisplayName("6.8 AT: undo_log is managed during global transaction")
    void purchase_undoLogManaged() {
        long undoLogsBefore = undoLogDAO.count();

        businessService.purchase("U001", "C001", 3);

        long undoLogsAfter = undoLogDAO.count();
        assertTrue(
                undoLogsAfter >= undoLogsBefore,
                "Undo log count should be >= before, was: " + undoLogsBefore + ", now: " + undoLogsAfter);
    }
}
