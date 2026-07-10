package org.apache.seata.controller;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.dto.StorageRequest;
import org.apache.seata.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/storage")
public class StorageRestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageRestController.class);

    private final StorageService storageService;

    public StorageRestController(StorageService storageService) {
        this.storageService = storageService;
    }

    /**
     * Modify stock quantity
     * @param request request parameters
     * @param keyXid distributed transaction ID
     * @return
     */
    @PostMapping
    public Map<String, Object> storage(
            @RequestBody StorageRequest request,
            @RequestHeader(value = RootContext.KEY_XID, required = false) String keyXid) {
        LOGGER.info("Distributed transaction {}: {}", RootContext.KEY_XID, keyXid);

        storageService.storage(request);
        return Map.of("code", 200);
    }

    @GetMapping("/{commodityCode}")
    public long count(@PathVariable String commodityCode) {
        return storageService.count(commodityCode);
    }
}
