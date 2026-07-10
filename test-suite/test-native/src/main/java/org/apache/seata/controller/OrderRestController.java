package org.apache.seata.controller;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.dto.OrderRequest;
import org.apache.seata.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderRestController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderRestController.class);

    private OrderService orderService;

    @Autowired
    public void setOrderService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create order
     *
     * @param request request parameters
     * @param keyXid  distributed transaction ID
     */
    @PostMapping
    public Map<String, Object> order(
            @RequestBody OrderRequest request,
            @RequestHeader(value = RootContext.KEY_XID, required = false) String keyXid) {
        LOGGER.info("Distributed transaction {}: {}", RootContext.KEY_XID, keyXid);

        orderService.order(request);
        return Map.of("code", 200);
    }

    @GetMapping("/{commodityCode}")
    public long count(@PathVariable String commodityCode) {
        return orderService.count(commodityCode);
    }
}
