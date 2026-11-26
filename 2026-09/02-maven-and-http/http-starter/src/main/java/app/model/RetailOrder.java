package app.model;

import java.math.BigDecimal;
import app.exception.ShopException;

import static app.exception.ShopException.Kind.INVALID;

public class RetailOrder extends Order {

    public RetailOrder(int id, int productId, int quantity) {
        super(id, productId, quantity);
    }

    @Override
    public OrderType getType() {
        return OrderType.RETAIL;
    }

    @Override
    public void validateQuantity() {
        if (getQuantity() < 1 || getQuantity() > 9) {
            throw new ShopException(
                    INVALID, "Invalid quantity for RETAIL"
            );
        }
    }

    @Override
    protected BigDecimal multiplier() {
        return BigDecimal.ONE;
    }
}