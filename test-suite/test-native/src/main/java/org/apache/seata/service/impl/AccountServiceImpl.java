package org.apache.seata.service.impl;

import org.apache.seata.service.AccountService;
import org.springframework.stereotype.Service;

@Service
public class AccountServiceImpl implements AccountService {
    @Override
    public void debit(String userId, int money) {}
}
