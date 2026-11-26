#!/usr/bin/env bash

set -eu

BASE="http://localhost:8080"

RESPONSE=$(mktemp)
trap 'rm -f "$RESPONSE"' EXIT

req() {
    local expected=$1
    local method=$2
    local path=$3

    shift 3

    local actual

    actual=$(curl --max-time 15 -sS \
        -o "$RESPONSE" \
        -w '%{http_code}' \
        -X "$method" \
        "$BASE$path" \
        -H 'Content-Type: application/json' \
        "$@")

    echo "$method $path -> $actual"

    cat "$RESPONSE"
    echo

    test "$actual" = "$expected"
}


req 200 GET /ping

req 201 POST /products \
    -d '{"name":"Phone","price":100,"stock":20}'

req 201 POST /products \
    -d '{"name":"Cable","price":5,"stock":0}'

req 200 GET /products

req 200 GET /products/1

req 200 GET '/products?name=phone'

req 200 GET '/products?inStock=true'

req 200 GET '/products?inStock=false'

req 200 GET '/products?name=phone&inStock=true'

req 200 PUT /products/1 \
    -d '{"name":"Phone","price":200,"stock":20}'

req 204 DELETE /products/2

req 404 GET /products/2


req 400 POST /products \
    -d '{"name":"Bad","price":-1,"stock":0}'

req 400 POST /products \
    -d '{'


req 400 GET '/products?inStock=maybe'

req 405 PATCH /products/1

req 201 POST /orders \
    -d '{"productId":1,"quantity":2,"type":"RETAIL"}'

req 201 POST /orders \
    -d '{"productId":1,"quantity":10,"type":"WHOLESALE"}'

req 201 POST /orders \
    -d '{"productId":1,"quantity":1,"type":"RETAIL"}'

req 200 GET /orders

req 200 GET /orders/1

req 200 GET '/orders?type=WHOLESALE'

req 200 GET '/orders?status=NEW'

req 200 GET '/orders?productId=1'

req 200 PUT /orders/1 \
    -d '{"productId":1,"quantity":3,"type":"RETAIL"}'

req 204 DELETE /orders/3

req 404 GET /orders/3

req 409 DELETE /products/1

req 200 POST /orders/1/checkout

req 409 POST /orders/1/checkout

req 409 PUT /orders/1 \
    -d '{"productId":1,"quantity":1,"type":"RETAIL"}'

req 409 DELETE /orders/1

req 200 POST /orders/2/checkout

req 200 GET /products/1

req 400 POST /orders \
    -d '{"productId":1,"quantity":5,"type":"WHOLESALE"}'

req 400 POST /orders \
    -d '{"productId":1,"quantity":10,"type":"RETAIL"}'


req 404 POST /orders \
    -d '{"productId":999,"quantity":1,"type":"RETAIL"}'

req 201 POST /orders \
    -d '{"productId":1,"quantity":10,"type":"WHOLESALE"}'

id=$(sed -n 's/.*"id":\([0-9]*\).*/\1/p' "$RESPONSE")

req 409 POST "/orders/$id/checkout"

req 200 GET '/orders?status=COMPLETED'

echo "All tests passed!"