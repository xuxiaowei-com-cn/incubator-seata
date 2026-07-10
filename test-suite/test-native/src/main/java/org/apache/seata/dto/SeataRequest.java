package org.apache.seata.dto;

/**
 * Seata distributed transaction request parameters
 *
 */
public class SeataRequest {

    /**
     * Commodity code
     */
    private String commodityCode;

    /**
     * User ID
     */
    private String userId;

    /**
     * Total count
     */
    private Long count;

    /**
     * Total amount
     */
    private Long money;

    public String getCommodityCode() {
        return commodityCode;
    }

    public void setCommodityCode(String commodityCode) {
        this.commodityCode = commodityCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public Long getMoney() {
        return money;
    }

    public void setMoney(Long money) {
        this.money = money;
    }
}
