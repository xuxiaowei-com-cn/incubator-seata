--
-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- Clean all test data for test isolation
DELETE FROM `order_tbl`;
DELETE FROM `undo_log`;
DELETE FROM `storage_tbl`;
DELETE FROM `account_tbl`;

-- Commodity C001: initial stock 100
INSERT INTO `storage_tbl` (`id`, `commodity_code`, `count`) VALUES (1, 'C001', 100);

-- Commodity C002: initial stock 5 (for testing insufficient stock scenario)
INSERT INTO `storage_tbl` (`id`, `commodity_code`, `count`) VALUES (2, 'C002', 5);

-- User U001: initial balance 10000
INSERT INTO `account_tbl` (`id`, `user_id`, `money`) VALUES (1, 'U001', 10000);

-- User U002: low balance 100 (for testing insufficient balance scenario)
INSERT INTO `account_tbl` (`id`, `user_id`, `money`) VALUES (2, 'U002', 100);
