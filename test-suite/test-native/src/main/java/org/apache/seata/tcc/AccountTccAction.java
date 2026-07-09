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
package org.apache.seata.tcc;

import org.apache.seata.rm.tcc.api.BusinessActionContext;
import org.apache.seata.rm.tcc.api.BusinessActionContextParameter;
import org.apache.seata.rm.tcc.api.LocalTCC;
import org.apache.seata.rm.tcc.api.TwoPhaseBusinessAction;

/**
 * TCC action interface for account operations.
 *
 * <p>Try phase debits account, Confirm phase is no-op,
 * Cancel phase restores the debited amount.</p>
 */
@LocalTCC
public interface AccountTccAction {

    /**
     * Try: debit account.
     *
     * @param context business action context
     * @param userId  user id
     * @param money   debit amount
     * @return true if debit successful
     */
    @TwoPhaseBusinessAction(name = "accountTccAction", commitMethod = "commit", rollbackMethod = "rollback")
    boolean prepareDebit(BusinessActionContext context,
                         @BusinessActionContextParameter("userId") String userId,
                         @BusinessActionContextParameter("money") int money);

    /**
     * Confirm: no-op (the try phase already committed the change).
     *
     * @param context business action context
     * @return true
     */
    boolean commit(BusinessActionContext context);

    /**
     * Cancel: restore the debited amount.
     *
     * @param context business action context
     * @return true if restoration successful
     */
    boolean rollback(BusinessActionContext context);
}
