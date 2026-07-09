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
package org.apache.seata.service.impl;

import org.apache.seata.dao.AccountDAO;
import org.apache.seata.entity.Account;
import org.apache.seata.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountServiceImpl.class);

    private AccountDAO accountDAO;

    @Autowired
    public void setAccountDAO(AccountDAO accountDAO) {
        this.accountDAO = accountDAO;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void debit(String userId, int money) {
        LOGGER.info("Debiting account: userId={}, money={}", userId, money);

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

        LOGGER.info("Account debited successfully: userId={}, money={}", userId, money);
    }
}
