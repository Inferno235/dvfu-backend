package app.service;

import app.model.*;
import app.exception.ShopException;

import java.math.BigDecimal;
import java.util.*;

import static app.exception.ShopException.Kind.*;

public class ShopService {

    private final Map<Integer, Product> products =
            new HashMap<>();

    private final Map<Integer, Order> orders =
            new HashMap<>();

    private int nextProduct = 1;
    private int nextOrder = 1;


    // Получение товара по ID
    public Product product(int id) {

        Product product = products.get(id);

        if (product == null) {
            throw new ShopException(
                    MISSING,
                    "Product not found"
            );
        }

        return product;
    }


    // Получение заказа по ID
    public Order order(int id) {

        Order order = orders.get(id);

        if (order == null) {
            throw new ShopException(
                    MISSING,
                    "Order not found"
            );
        }

        return order;
    }


    // Получение списка товаров с фильтрацией
    public List<Product> products(
            String name,
            Boolean inStock
    ) {

        return products.values().stream()
                .filter(p -> name == null ||
                        p.name().toLowerCase(Locale.ROOT)
                                .contains(name.toLowerCase(Locale.ROOT)))
                .filter(p -> inStock == null ||
                        (p.stock() > 0) == inStock)
                .sorted(Comparator.comparingInt(Product::id))
                .toList();
    }


    // Получение списка заказов с фильтрацией
    public List<Order> orders(
            OrderType type,
            OrderStatus status,
            Integer productId
    ) {

        return orders.values().stream()
                .filter(o -> type == null ||
                        o.getType() == type)
                .filter(o -> status == null ||
                        o.getStatus() == status)
                .filter(o -> productId == null ||
                        o.getProductId() == productId)
                .sorted(Comparator.comparingInt(Order::getId))
                .toList();
    }


    // Сохранение товара
    private Product saveProduct(
            int id,
            String name,
            BigDecimal price,
            int stock
    ) {

        if (name == null ||
                name.isBlank() ||
                name.length() > 200 ||
                price == null ||
                price.signum() <= 0 ||
                price.scale() > 2 ||
                price.compareTo(
                        new BigDecimal("1000000000")
                ) > 0 ||
                stock < 0) {

            throw new ShopException(
                    INVALID,
                    "Invalid product fields"
            );
        }

        Product product = new Product(
                id,
                name,
                price,
                stock
        );

        products.put(id, product);

        return product;
    }


    // Создание товара
    public Product createProduct(
            String name,
            BigDecimal price,
            int stock
    ) {

        return saveProduct(
                nextProduct++,
                name,
                price,
                stock
        );
    }


    // Изменение товара
    public Product updateProduct(
            int id,
            String name,
            BigDecimal price,
            int stock
    ) {

        product(id);

        return saveProduct(id, name, price, stock);
    }


    // Удаление товара
    public void deleteProduct(int id) {

        product(id);

        boolean hasOrders = orders.values().stream()
                .anyMatch(o -> o.getProductId() == id);

        if (hasOrders) {
            throw new ShopException(
                    CONFLICT,
                    "Product has orders"
            );
        }

        products.remove(id);
    }


    // Сохранение заказа
    private Order saveOrder(
            int id,
            int productId,
            int quantity,
            OrderType type
    ) {

        product(productId);

        if (type == null) {
            throw new ShopException(
                    INVALID,
                    "Type required"
            );
        }

        Order order = switch (type) {

            case RETAIL ->
                    new RetailOrder(
                            id,
                            productId,
                            quantity
                    );

            case WHOLESALE ->
                    new WholesaleOrder(
                            id,
                            productId,
                            quantity
                    );
        };

        order.validateQuantity();

        orders.put(id, order);

        return order;
    }


    // Создание заказа
    public Order createOrder(
            int productId,
            int quantity,
            OrderType type
    ) {

        return saveOrder(
                nextOrder++,
                productId,
                quantity,
                type
        );
    }


    // Проверка статуса заказа
    private void requireNew(Order order) {

        if (order.getStatus() != OrderStatus.NEW) {
            throw new ShopException(
                    CONFLICT,
                    "Completed order is immutable"
            );
        }
    }


    // Изменение заказа
    public Order updateOrder(
            int id,
            int productId,
            int quantity,
            OrderType type
    ) {

        requireNew(order(id));

        return saveOrder(
                id,
                productId,
                quantity,
                type
        );
    }


    // Удаление заказа
    public void deleteOrder(int id) {

        requireNew(order(id));

        orders.remove(id);
    }


    // Оформление заказа
    public Order checkout(int id) {

        Order order = order(id);

        requireNew(order);

        Product product = product(order.getProductId());

        if (product.stock() < order.getQuantity()) {

            throw new ShopException(
                    CONFLICT,
                    "Not enough stock"
            );
        }

        order.checkout(product.price());

        Product updatedProduct = new Product(
                product.id(),
                product.name(),
                product.price(),
                product.stock() - order.getQuantity()
        );

        products.put(product.id(), updatedProduct);

        return order;
    }
}