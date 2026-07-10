package org.apache.seata.dto;

/**
 * Account balance modification request parameters
 *
 */
public class AccountMoneyRequest {

    /**
     * User ID
     */
    private String userId;

    /**
     * Amount: positive value to increase, negative value to decrease, throws exception when insufficient
     */
    private Long money;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getMoney() {
        return money;
    }

    public void setMoney(Long money) {
        this.money = money;
    }
}
