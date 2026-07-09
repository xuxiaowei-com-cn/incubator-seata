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
package org.apache.seata.tcc.impl;

import org.apache.seata.dao.AccountDAO;
import org.apache.seata.entity.Account;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.tcc.AccountTccAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TCC action implementation for account operations.
 *
 * <p>Try: debit account immediately.
 * Confirm: no-op.
 * Cancel: restore the debited amount using context parameters.</p>
 */
@Service
public class AccountTccActionImpl implements AccountTccAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountTccActionImpl.class);

    private AccountDAO accountDAO;

    @Autowired
    public void setAccountDAO(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean prepareDebit(BusinessActionContext context, String userId, int money) {
        LOGGER.info("TCC Try: Debiting account — userId={}, money={}", userId, money);

        int affected = accountDAO.debit(userId, money);
        if (affected == 0) {
            // Determine whether user not found or insufficient balance
            Account account = accountDAO.findByUserId(userId);
            if (account == null) {
                throw new RuntimeException("Account not found for userId: " + userId);
            }
            throw new RuntimeException("Insufficient balance: userId=" + userId + ", required=" + money + ", available="
                    + account.getMoney());
        }

        // Store parameters in action context ONLY after successful debit,
        // so that rollback can correctly restore the debited amount.
        if (context != null) {
            context.addActionContext("userId", userId);
            context.addActionContext("money", money);
        }

        LOGGER.info("TCC Try: Account debited successfully — userId={}, money={}", userId, money);
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext context) {
        LOGGER.info(
                "TCC Confirm: Account action confirmed — xid={}, branchId={}", context.getXid(), context.getBranchId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean rollback(BusinessActionContext context) {
        String userId = (String) context.getActionContext("userId");
        Integer money = (Integer) context.getActionContext("money");

        // If Try phase didn't store context parameters (e.g., because the debit failed),
        // there's nothing to rollback — return successfully.
        if (userId == null || money == null) {
            LOGGER.info("TCC Cancel: No action context parameters found, skip rollback for xid={}", context.getXid());
            return true;
        }

        LOGGER.info("TCC Cancel: Restoring account balance — userId={}, money={}", userId, money);
        accountDAO.credit(userId, money);
        LOGGER.info("TCC Cancel: Account balance restored successfully — userId={}, money={}", userId, money);
        return true;
    }
}
