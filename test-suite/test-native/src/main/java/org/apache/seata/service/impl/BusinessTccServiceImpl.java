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

import org.apache.seata.core.context.RootContext;
import org.apache.seata.dao.OrderDAO;
import org.apache.seata.entity.Order;
import org.apache.seata.service.BusinessTccService;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.apache.seata.tcc.AccountTccAction;
import org.apache.seata.tcc.StorageTccAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Business service implementation using TCC branch participants.
 *
 * <p>The purchase flow:</p>
 * <ol>
 *   <li>Storage TCC Try: deduct storage</li>
 *   <li>Account TCC Try: debit account</li>
 *   <li>Create order record</li>
 * </ol>
 *
 * <p>On rollback, TCC Cancel methods restore the deducted storage and debited amount.</p>
 */
@Service
public class BusinessTccServiceImpl implements BusinessTccService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessTccServiceImpl.class);

    private StorageTccAction storageTccAction;

    private AccountTccAction accountTccAction;

    private OrderDAO orderDAO;

    @Autowired
    public void setStorageTccAction(StorageTccAction storageTccAction) {
        this.storageTccAction = storageTccAction;
    }

    @Autowired
    public void setAccountTccAction(AccountTccAction accountTccAction) {
        this.accountTccAction = accountTccAction;
    }

    @Autowired
    public void setOrderDAO(OrderDAO orderDAO) {
        this.orderDAO = orderDAO;
    }

    @Override
    @GlobalTransactional
    public void purchase(String userId, String commodityCode, int orderCount) {
        String xid = RootContext.getXID();
        LOGGER.info("TCC purchase — xid: {}", xid);

        // TCC Try: deduct storage
        storageTccAction.prepareDeduct(null, commodityCode, orderCount);

        // Calculate order money
        int orderMoney = orderCount * 100;

        // TCC Try: debit account
        accountTccAction.prepareDebit(null, userId, orderMoney);

        // Create order record
        Order order = new Order();
        order.setUserId(userId);
        order.setCommodityCode(commodityCode);
        order.setCount(orderCount);
        order.setMoney(orderMoney);
        orderDAO.insert(order);

        LOGGER.info(
                "TCC purchase completed — userId={}, commodityCode={}, orderCount={}, money={}",
                userId,
                commodityCode,
                orderCount,
                orderMoney);
    }
}
