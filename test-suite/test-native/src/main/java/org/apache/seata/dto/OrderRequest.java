package org.apache.seata.dto;

/**
 * Order creation request parameters
 *
 */
public class OrderRequest {

    /**
     * User ID
     */
    private String userId;

    /**
     * Commodity code
     */
    private String commodityCode;

    /**
     * Total count
     */
    private Long count;

    /**
     * Total amount
     */
    private Long money;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCommodityCode() {
        return commodityCode;
    }

    public void setCommodityCode(String commodityCode) {
        this.commodityCode = commodityCode;
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
