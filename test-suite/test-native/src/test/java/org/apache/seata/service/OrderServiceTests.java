package org.apache.seata.service;

import org.apache.seata.dto.OrderRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class OrderServiceTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceTests.class);

    @Autowired
    private OrderService orderService;

    @Test
    void order() {
        String commodityCode = "A001";
        String userId = "U003";
        long count1 = orderService.count(commodityCode);

        long count = 1;
        long money = 1;
        OrderRequest request = new OrderRequest();
        request.setUserId(userId);
        request.setCommodityCode(commodityCode);
        request.setCount(count);
        request.setMoney(money);

        orderService.order(request);

        long count2 = orderService.count(commodityCode);
        assertEquals(count1 + 1, count2);
    }
}
