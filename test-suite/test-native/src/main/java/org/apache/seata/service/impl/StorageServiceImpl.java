package org.apache.seata.service.impl;

import org.apache.seata.service.StorageService;
import org.springframework.stereotype.Service;

@Service
public class StorageServiceImpl implements StorageService {

    @Override
    public void deduct(String commodityCode, int count) {}
}
