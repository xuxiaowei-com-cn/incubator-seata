package org.apache.seata.service;

import org.apache.seata.dto.StorageRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StorageServiceTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(StorageServiceTests.class);

    @Autowired
    private StorageService storageService;

    @Test
    void count() {
        String commodityCode = "A001";
        Long count = storageService.count(commodityCode);
        assertNotNull(count);
        assertNotEquals(0, count);
        assertNotEquals(1, count);
    }

    @Test
    void storage_1() {
        String commodityCode = "A001";

        long count1 = storageService.count(commodityCode);
        assertNotEquals(0, count1);

        long count = 1;
        StorageRequest request = new StorageRequest();
        request.setCommodityCode(commodityCode);
        request.setCount(count);
        storageService.storage(request);

        long count2 = storageService.count(commodityCode);
        assertNotEquals(0, count2);
        assertEquals(count1 + count, count2);
    }

    @Test
    void storage_2() {
        String commodityCode = "A001";

        long count1 = storageService.count(commodityCode);
        assertNotEquals(0, count1);

        long count = -1;
        StorageRequest request = new StorageRequest();
        request.setCommodityCode(commodityCode);
        request.setCount(count);
        storageService.storage(request);

        long count2 = storageService.count(commodityCode);
        assertNotEquals(0, count2);
        assertEquals(count1 + count, count2);
    }

    @Test
    void storage_3() {
        String commodityCode = "A001";
        Long count = -10000000L;
        StorageRequest request = new StorageRequest();
        request.setCommodityCode(commodityCode);
        request.setCount(count);
        assertThrows(RuntimeException.class, () -> {
            storageService.storage(request);
        });
    }
}
