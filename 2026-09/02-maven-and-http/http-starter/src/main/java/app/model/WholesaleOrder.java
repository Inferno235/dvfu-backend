package app.model;

import java.math.BigDecimal;
import app.exception.ShopException;

import static app.exception.ShopException.Kind.INVALID;

public class WholesaleOrder extends Order {

    public WholesaleOrder(int id, int productId, int quantity) {
        super(id, productId, quantity);
    }

    @Override
    public OrderType getType() {
        return OrderType.WHOLESALE;
    }

    @Override
    public void validateQuantity() {
        if (getQuantity() < 10) {
            throw new ShopException(
                    INVALID, "Invalid quantity for WHOLESALE"
            );
        }
    }

    @Override
    protected BigDecimal multiplier() {
        return new BigDecimal("0.85");
    }
}