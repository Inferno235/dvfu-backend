package app.controller;

import com.sun.net.httpserver.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

import app.model.*;
import app.service.ShopService;
import app.exception.ShopException;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.*;

public class ShopController implements HttpHandler {

    private final ShopService service;

    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();


    public ShopController(ShopService service) {
        this.service = service;
    }


    private record Result(int code, Object body) {
    }


    private static class HttpError extends RuntimeException {

        final int code;

        HttpError(int code, String message) {
            super(message);
            this.code = code;
        }
    }


    private static HttpError bad(String message) {
        return new HttpError(400, message);
    }


    private static Result ok(Object object) {
        return new Result(200, object);
    }



    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        try {

            Result result;

            try {

                result = route(exchange);

            } catch (ShopException e) {

                int code = switch (e.kind()) {
                    case INVALID -> 400;
                    case MISSING -> 404;
                    case CONFLICT -> 409;
                };

                result = new Result(
                        code,
                        Map.of("error", e.getMessage())
                );

            } catch (HttpError e) {

                result = new Result(
                        e.code,
                        Map.of("error", e.getMessage())
                );

            } catch (
                    IllegalArgumentException |
                    tools.jackson.core.JacksonException e
            ) {

                result = new Result(
                        400,
                        Map.of("error", "Invalid request")
                );

            } catch (Exception e) {

                result = new Result(
                        500,
                        Map.of("error", "Internal error")
                );
            }


            if (result.code() == 204) {

                exchange.sendResponseHeaders(204, -1);

                return;
            }


            byte[] data = json.writeValueAsBytes(
                    result.body()
            );

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "application/json; charset=utf-8"
            );

            exchange.sendResponseHeaders(
                    result.code(),
                    data.length
            );

            exchange.getResponseBody().write(data);

        } finally {

            exchange.close();
        }
    }



    private Result route(HttpExchange exchange)
            throws IOException {

        String[] path = exchange.getRequestURI()
                .getPath()
                .split("/", -1);

        String method = exchange.getRequestMethod();

        if (path.length < 2 ||
                (!path[1].equals("products") &&
                        !path[1].equals("orders"))) {

            throw new HttpError(
                    404,
                    "Route not found"
            );
        }


        boolean products = path[1].equals("products");

        boolean collection = path.length == 2;

        boolean checkout =
                !products &&
                        path.length == 4 &&
                        path[3].equals("checkout");


        if (!collection &&
                path.length != 3 &&
                !checkout) {

            throw new HttpError(
                    404,
                    "Route not found"
            );
        }


        int id = collection ? 0 : positive(path[2]);


        String allow = checkout
                ? "POST"
                : collection
                ? "GET, POST"
                : "GET, PUT, DELETE";


        if (!Arrays.asList(allow.split(", "))
                .contains(method)) {

            exchange.getResponseHeaders().set(
                    "Allow",
                    allow
            );

            throw new HttpError(
                    405,
                    "Method not allowed"
            );
        }

        Map<String, String> query = query(
                exchange.getRequestURI().getRawQuery()
        );


        Set<String> allowed =
                collection && method.equals("GET")
                        ? products
                        ? Set.of("name", "inStock")
                        : Set.of("type", "status", "productId")
                        : Set.of();


        if (!allowed.containsAll(query.keySet())) {

            throw bad("Unknown query parameter");
        }



        JsonNode body =
                (method.equals("PUT") ||
                        (method.equals("POST") && !checkout))
                        ? body(exchange)
                        : null;



        synchronized (service) {


            if (checkout) {
                return ok(
                        snapshot(service.checkout(id))
                );
            }



            if (products) {

                return switch (method) {

                    case "GET" -> ok(
                            collection
                                    ? service.products(
                                    query.get("name"),
                                    bool(query.get("inStock"))
                            )
                                    : service.product(id)
                    );

                    case "POST" -> new Result(
                            201,
                            service.createProduct(
                                    str(body, "name"),
                                    decimal(body, "price"),
                                    integer(body, "stock")
                            )
                    );

                    case "PUT" -> ok(
                            service.updateProduct(
                                    id,
                                    str(body, "name"),
                                    decimal(body, "price"),
                                    integer(body, "stock")
                            )
                    );

                    default -> {

                        service.deleteProduct(id);

                        yield new Result(204, null);
                    }
                };
            }



            return switch (method) {

                case "GET" -> ok(
                        collection
                                ? service.orders(
                                        en(
                                                OrderType.class,
                                                query.get("type")
                                        ),
                                        en(
                                                OrderStatus.class,
                                                query.get("status")
                                        ),
                                        query.containsKey("productId")
                                                ? positive(
                                                query.get("productId")
                                        )
                                                : null
                                )
                                .stream()
                                .map(this::snapshot)
                                .toList()

                                : snapshot(service.order(id))
                );

                case "POST" -> new Result(
                        201,
                        snapshot(
                                service.createOrder(
                                        integer(body, "productId"),
                                        integer(body, "quantity"),
                                        OrderType.valueOf(
                                                str(body, "type")
                                        )
                                )
                        )
                );

                case "PUT" -> ok(
                        snapshot(
                                service.updateOrder(
                                        id,
                                        integer(body, "productId"),
                                        integer(body, "quantity"),
                                        OrderType.valueOf(
                                                str(body, "type")
                                        )
                                )
                        )
                );

                default -> {

                    service.deleteOrder(id);

                    yield new Result(204, null);
                }
            };
        }
    }

    private Map<String, Object> snapshot(Order order) {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("id", order.getId());
        result.put("productId", order.getProductId());
        result.put("quantity", order.getQuantity());
        result.put("type", order.getType());
        result.put("status", order.getStatus());
        result.put("total", order.getTotal());

        return result;
    }

    private JsonNode body(HttpExchange exchange)
            throws IOException {

        byte[] bytes = exchange.getRequestBody()
                .readNBytes(65537);

        if (bytes.length > 65536) {
            throw new HttpError(
                    413,
                    "Body too large"
            );
        }

        JsonNode body = json.readTree(bytes);

        if (body == null || !body.isObject()) {
            throw bad("JSON object required");
        }

        return body;
    }

    private static JsonNode field(
            JsonNode body,
            String key
    ) {

        JsonNode value = body.get(key);

        if (value == null || value.isNull()) {
            throw bad("Missing field: " + key);
        }

        return value;
    }

    private static String str(
            JsonNode body,
            String key
    ) {

        JsonNode value = field(body, key);

        if (!value.isTextual()) {
            throw bad("Expected string: " + key);
        }

        return value.asString();
    }

    private static int integer(
            JsonNode body,
            String key
    ) {

        JsonNode value = field(body, key);

        if (!value.isIntegralNumber() ||
                !value.canConvertToInt()) {

            throw bad("Expected integer: " + key);
        }

        return value.intValue();
    }

    private static BigDecimal decimal(
            JsonNode body,
            String key
    ) {

        JsonNode value = field(body, key);

        if (!value.isNumber()) {
            throw bad("Expected number: " + key);
        }

        return value.decimalValue();
    }

    private static int positive(String value) {

        int number = Integer.parseInt(value);

        if (number <= 0) {
            throw bad("Positive id required");
        }

        return number;
    }


    private static Boolean bool(String value) {

        if (value == null) {
            return null;
        }

        if (!value.equals("true") &&
                !value.equals("false")) {

            throw bad("Expected true or false");
        }

        return Boolean.valueOf(value);
    }


    private static <T extends Enum<T>> T en(
            Class<T> type,
            String value
    ) {

        return value == null
                ? null
                : Enum.valueOf(type, value);
    }

    private static Map<String, String> query(
            String raw
    ) {

        Map<String, String> map = new HashMap<>();

        if (raw == null || raw.isEmpty()) {
            return map;
        }

        for (String part : raw.split("&", -1)) {

            String[] pair = part.split("=", 2);

            if (pair.length != 2) {
                throw bad("Invalid query");
            }

            String key = URLDecoder.decode(
                    pair[0],
                    StandardCharsets.UTF_8
            );

            String value = URLDecoder.decode(
                    pair[1],
                    StandardCharsets.UTF_8
            );

            if (map.putIfAbsent(key, value) != null) {
                throw bad("Duplicate query parameter");
            }
        }

        return map;
    }
}