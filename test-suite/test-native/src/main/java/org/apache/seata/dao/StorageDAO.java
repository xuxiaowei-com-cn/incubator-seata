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
package org.apache.seata.dao;

import org.apache.seata.entity.Storage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageDAO extends JpaRepository<Storage, Integer> {

    Storage findByCommodityCode(String commodityCode);

    /**
     * Atomically deduct storage count. Uses database-level atomicity to
     * prevent race conditions in concurrent deduction scenarios.
     *
     * @param commodityCode the commodity code
     * @param count         the count to deduct
     * @return number of rows affected (1 if deduction succeeded, 0 if insufficient stock or commodity not found)
     */
    @Modifying
    @Query(
            "UPDATE Storage s SET s.count = s.count - :count WHERE s.commodityCode = :commodityCode AND s.count >= :count")
    int deduct(@Param("commodityCode") String commodityCode, @Param("count") int count);

    /**
     * Atomically restore storage count. Used for TCC rollback operations.
     *
     * @param commodityCode the commodity code
     * @param count         the count to restore
     * @return number of rows affected (1 if commodity found and restored, 0 if not found)
     */
    @Modifying
    @Query("UPDATE Storage s SET s.count = s.count + :count WHERE s.commodityCode = :commodityCode")
    int restore(@Param("commodityCode") String commodityCode, @Param("count") int count);
}
