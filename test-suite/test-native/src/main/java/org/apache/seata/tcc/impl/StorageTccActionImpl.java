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

import org.apache.seata.dao.StorageDAO;
import org.apache.seata.entity.Storage;
import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.tcc.StorageTccAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TCC action implementation for storage operations.
 *
 * <p>Try: deduct storage immediately.
 * Confirm: no-op.
 * Cancel: restore the deducted count using context parameters.</p>
 */
@Service
public class StorageTccActionImpl implements StorageTccAction {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageTccActionImpl.class);

    private StorageDAO storageDAO;

    @Autowired
    public void setStorageDAO(StorageDAO storageDAO) {
        this.storageDAO = storageDAO;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean prepareDeduct(BusinessActionContext context, String commodityCode, int count) {
        LOGGER.info("TCC Try: Deducting storage — commodityCode={}, count={}", commodityCode, count);

        int affected = storageDAO.deduct(commodityCode, count);
        if (affected == 0) {
            // Determine whether commodity not found or insufficient stock
            Storage storage = storageDAO.findByCommodityCode(commodityCode);
            if (storage == null) {
                throw new RuntimeException("Storage not found for commodityCode: " + commodityCode);
            }
            throw new RuntimeException("Insufficient storage: commodityCode=" + commodityCode + ", required=" + count
                    + ", available=" + storage.getCount());
        }

        // Store parameters in action context ONLY after successful deduction,
        // so that rollback can correctly restore the deducted count.
        if (context != null) {
            context.addActionContext("commodityCode", commodityCode);
            context.addActionContext("count", count);
        }

        LOGGER.info("TCC Try: Storage deducted successfully — commodityCode={}, count={}", commodityCode, count);
        return true;
    }

    @Override
    public boolean commit(BusinessActionContext context) {
        LOGGER.info(
                "TCC Confirm: Storage action confirmed — xid={}, branchId={}", context.getXid(), context.getBranchId());
        return true;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public boolean rollback(BusinessActionContext context) {
        String commodityCode = (String) context.getActionContext("commodityCode");
        Integer count = (Integer) context.getActionContext("count");

        // If Try phase didn't store context parameters (e.g., because the deduction failed),
        // there's nothing to rollback — return successfully.
        if (commodityCode == null || count == null) {
            LOGGER.info("TCC Cancel: No action context parameters found, skip rollback for xid={}", context.getXid());
            return true;
        }

        LOGGER.info("TCC Cancel: Restoring storage — commodityCode={}, count={}", commodityCode, count);
        storageDAO.restore(commodityCode, count);
        LOGGER.info("TCC Cancel: Storage restored successfully — commodityCode={}, count={}", commodityCode, count);
        return true;
    }
}
