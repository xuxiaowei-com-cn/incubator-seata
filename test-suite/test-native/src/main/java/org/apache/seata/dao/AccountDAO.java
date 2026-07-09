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

import org.apache.seata.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDAO extends JpaRepository<Account, Integer> {

    Account findByUserId(String userId);

    /**
     * Atomically debit money from account. Uses database-level atomicity to
     * prevent race conditions in concurrent debit scenarios.
     *
     * @param userId the user id
     * @param money  the amount to debit
     * @return number of rows affected (1 if debit succeeded, 0 if insufficient balance or user not found)
     */
    @Modifying
    @Query("UPDATE Account a SET a.money = a.money - :money WHERE a.userId = :userId AND a.money >= :money")
    int debit(@Param("userId") String userId, @Param("money") int money);

    /**
     * Atomically credit money to account. Used for TCC rollback operations.
     *
     * @param userId the user id
     * @param money  the amount to credit
     * @return number of rows affected (1 if user found and credited, 0 if user not found)
     */
    @Modifying
    @Query("UPDATE Account a SET a.money = a.money + :money WHERE a.userId = :userId")
    int credit(@Param("userId") String userId, @Param("money") int money);
}
