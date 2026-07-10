package org.apache.seata.service;

import org.apache.seata.dto.AccountMoneyRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AccountServiceTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceTests.class);

    @Autowired
    private AccountService accountService;

    @Test
    void getMoney() {
        String userId = "U003";
        Long money = accountService.getMoney(userId);
        assertNotNull(money);
    }

    @Test
    void money_1() {
        String userId = "U003";

        Long money1 = accountService.getMoney(userId);
        assertNotNull(money1);

        long money = 1;
        AccountMoneyRequest request = new AccountMoneyRequest();
        request.setUserId(userId);
        request.setMoney(money);

        accountService.money(request);

        Long money2 = accountService.getMoney(userId);
        assertNotNull(money2);

        assertEquals(money1 + money, money2);
    }

    @Test
    void money_2() {
        String userId = "U003";

        Long money1 = accountService.getMoney(userId);
        assertNotNull(money1);

        long money = -1;
        AccountMoneyRequest request = new AccountMoneyRequest();
        request.setUserId(userId);
        request.setMoney(money);
        accountService.money(request);

        Long money2 = accountService.getMoney(userId);
        assertNotNull(money2);

        assertEquals(money1 + money, money2);
    }

    @Test
    void money_3() {
        String userId = "U003";
        long money = -10000000;
        AccountMoneyRequest request = new AccountMoneyRequest();
        request.setUserId(userId);
        request.setMoney(money);
        assertThrows(RuntimeException.class, () -> {
            accountService.money(request);
        });
    }
}
