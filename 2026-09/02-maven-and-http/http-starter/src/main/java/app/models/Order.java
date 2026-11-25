package shop.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import shop.exception.ShopException;

import static shop.exception.ShopException.Kind.*;

public abstract class Order {

    private final int id;
    private final int productId;
    private final int quantity;

    private OrderStatus status = OrderStatus.NEW;
    private BigDecimal total;

    protected Order(int id, int productId, int quantity) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
    }

    public int getId() {
        return id;
    }

    public int getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public abstract OrderType getType();

    public abstract void validateQuantity();

    protected abstract BigDecimal multiplier();

    public void checkout(BigDecimal price) {
        if (status != OrderStatus.NEW) {
            throw new ShopException(
                    CONFLICT, "Order already completed"
            );
        }

        validateQuantity();

        total = price
                .multiply(BigDecimal.valueOf(quantity))
                .multiply(multiplier())
                .setScale(2, RoundingMode.HALF_UP);

        status = OrderStatus.COMPLETED;
    }
}