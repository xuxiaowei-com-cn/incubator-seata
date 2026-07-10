package org.apache.seata.controller;

import org.apache.seata.core.context.RootContext;
import org.apache.seata.dto.AccountMoneyRequest;
import org.apache.seata.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/account")
public class AccountRestController {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountRestController.class);

    private AccountService accountService;

    @Autowired
    public void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Modify user balance
     *
     * @param request request parameters
     * @param keyXid  distributed transaction ID
     */
    @PostMapping("/money")
    public Map<String, Object> money(
            @RequestBody AccountMoneyRequest request,
            @RequestHeader(value = RootContext.KEY_XID, required = false) String keyXid) {
        LOGGER.info("Distributed transaction {}: {}", RootContext.KEY_XID, keyXid);

        accountService.money(request);
        return Map.of("code", 200);
    }

    @GetMapping("/money/{userId}")
    public long money(@PathVariable String userId) {
        return accountService.getMoney(userId);
    }
}
