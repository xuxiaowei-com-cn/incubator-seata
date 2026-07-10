package org.apache.seata.dto;

/**
 * Stock modification request parameters
 *
 */
public class StorageRequest {

    /**
     * Commodity code
     */
    private String commodityCode;

    /**
     * Quantity: positive value to increase, negative value to decrease, throws exception when insufficient
     */
    private Long count;

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
}
