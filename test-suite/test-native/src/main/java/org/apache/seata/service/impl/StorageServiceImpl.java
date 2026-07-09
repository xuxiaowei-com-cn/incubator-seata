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

import org.apache.seata.dao.StorageDAO;
import org.apache.seata.entity.Storage;
import org.apache.seata.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageServiceImpl implements StorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageServiceImpl.class);

    private StorageDAO storageDAO;

    @Autowired
    public void setStorageDAO(StorageDAO storageDAO) {
        this.storageDAO = storageDAO;
    }

    @Override
    @Transactional(rollbackFor = RuntimeException.class)
    public void deduct(String commodityCode, int count) {
        LOGGER.info("Deducting storage: commodityCode={}, count={}", commodityCode, count);

        Storage storage = storageDAO.findByCommodityCode(commodityCode);
        if (storage == null) {
            throw new RuntimeException("Storage not found for commodityCode: " + commodityCode);
        }
        if (storage.getCount() < count) {
            throw new RuntimeException("Insufficient storage: commodityCode=" + commodityCode + ", required=" + count
                    + ", available=" + storage.getCount());
        }

        storage.setCount(storage.getCount() - count);
        storageDAO.saveAndFlush(storage);

        LOGGER.info("Storage deducted successfully: commodityCode={}, count={}", commodityCode, count);
    }
}
