#!/usr/bin/env bash
#
# End-to-end tests for the vendor-api CRUD endpoints, including the database rules
# behind them (stock triggers, status triggers, foreign keys).
#
# Install the database objects (db/vendor_api.sql), start the API (mvn spring-boot:run), then run:
#
#   ./test.sh
#
# Configuration (environment variables):
#   BASE_URL          API base url                                (default http://localhost:8080/api)
#   OTHER_ADDRESS_ID  an existing customer address (not ours)     (default 1)
#   SHOW_RESPONSES=0  only print PASS/FAIL lines, not the commands and responses
#   LIST_LIMIT=N      how many items of a list response to print  (default 3, 0 = all)
#
# The script creates its own supplier (+ address), category, product, customer (+ address)
# and orders, and deletes them again at the end (also when a test fails), so the sample
# data stays as it was.
#
# Requires: bash, curl, jq (brew install jq). Exit code is 0 when all tests pass, 1 otherwise.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
OTHER_ADDRESS_ID="${OTHER_ADDRESS_ID:-1}"
SHOW_RESPONSES="${SHOW_RESPONSES:-1}"
LIST_LIMIT="${LIST_LIMIT:-3}"

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required (macOS: brew install jq, Debian/Ubuntu: apt install jq)"
    exit 1
fi

if [ -t 1 ]; then
    GREEN=$'\033[32m'; RED=$'\033[31m'; YELLOW=$'\033[33m'; BOLD=$'\033[1m'; DIM=$'\033[2m'; RESET=$'\033[0m'
    JQ_COLOR="-C"
else
    GREEN=""; RED=""; YELLOW=""; BOLD=""; DIM=""; RESET=""
    JQ_COLOR="-M"
fi

PASSED=0
FAILED=0
FAILED_TESTS=()

STATUS=""
BODY=""

# rows created by this run (deleted again by cleanup)
SUPPLIER_ID=""
SUPPLIER_ADDRESS=""
CATEGORY_ID=""
PRODUCT_ID=""
CUSTOMER_ID=""
ADDRESS_ID=""
ORDER_ID=""
ORDER2_ID=""

# ---------------------------------------------------------------------------
# helpers
# ---------------------------------------------------------------------------

JSON='Content-Type: application/json'
LAST_CMD=""

# readable, copy-pasteable version of a command (single-quotes args that need it)
format_cmd() {
    local out="" arg
    for arg in "$@"; do
        if [[ "$arg" =~ ^[A-Za-z0-9_./:=?\&%-]+$ ]]; then
            out+="$arg "
        else
            out+="'${arg//\'/\'\\\'\'}' "
        fi
    done
    echo "${out% }"
}

# call curl ARGS...  -> runs the curl command as written, sets STATUS (HTTP code) and BODY
call() {
    local tmp
    tmp="$(mktemp)"
    LAST_CMD="$(format_cmd "$@")"
    STATUS="$("$@" -o "$tmp" -w '%{http_code}')"
    BODY="$(cat "$tmp")"
    rm -f "$tmp"
}

# prints the last curl command and its response, pretty-printed with jq
# (long list responses are cut to LIST_LIMIT items)
show_response() {
    echo "       ${DIM}\$ $LAST_CMD${RESET}"
    echo "       ${DIM}HTTP $STATUS${RESET}"
    if [ -z "$BODY" ]; then
        echo "       ${DIM}(empty body)${RESET}"
    elif echo "$BODY" | jq -e . >/dev/null 2>&1; then
        echo "$BODY" | jq $JQ_COLOR --argjson limit "$LIST_LIMIT" '
            if type == "array" and $limit > 0 and length > $limit
            then .[:$limit] + ["... \(length - $limit) more items"]
            else . end' | sed 's/^/       /'
    else
        echo "$BODY" | sed 's/^/       /'
    fi
    echo
}

pass() {
    PASSED=$((PASSED + 1))
    echo "  ${GREEN}PASS${RESET} $1"
    [ "$SHOW_RESPONSES" = "1" ] && show_response
    return 0
}

fail() {
    FAILED=$((FAILED + 1))
    FAILED_TESTS+=("$1")
    echo "  ${RED}FAIL${RESET} $1"
    echo "       ${RED}$2${RESET}"
    show_response
}

# check NAME EXPECTED_STATUS [STRING_THAT_BODY_MUST_CONTAIN...]
check() {
    local name="$1" expected="$2"
    shift 2
    if [ "$STATUS" != "$expected" ]; then
        fail "$name" "expected HTTP $expected, got $STATUS"
        return 1
    fi
    local needle
    for needle in "$@"; do
        if [[ "$BODY" != *"$needle"* ]]; then
            fail "$name" "response does not contain: $needle"
            return 1
        fi
    done
    pass "$name"
}

# first top-level "id" of a JSON object response
extract_id() {
    echo "$1" | jq -r '.id // empty' 2>/dev/null
}

# value of a jq expression on the last response body
jqv() {
    echo "$BODY" | jq -r "$1" 2>/dev/null
}

# check_jq NAME EXPECTED_STATUS JQ_EXPRESSION_THAT_MUST_BE_TRUE [jq args...]
check_jq() {
    local name="$1" expected="$2" expr="$3"
    shift 3
    if [ "$STATUS" != "$expected" ]; then
        fail "$name" "expected HTTP $expected, got $STATUS"
        return 1
    fi
    if ! echo "$BODY" | jq -e "$@" "$expr" >/dev/null 2>&1; then
        fail "$name" "expected: $expr"
        return 1
    fi
    pass "$name"
}

# current stock of a product
stock_of() {
    curl -s "$BASE_URL/products/$1" | jq -r '.stockQuantity'
}

section() {
    echo
    echo "${BOLD}$1${RESET}"
}

cleanup() {
    # delete whatever this run created, in FK-safe order
    [ -n "$ORDER_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/orders/$ORDER_ID"
    [ -n "$ORDER2_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/orders/$ORDER2_ID"
    [ -n "$PRODUCT_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/products/$PRODUCT_ID"
    [ -n "$CATEGORY_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
    [ -n "$ADDRESS_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/customers/$CUSTOMER_ID/addresses/$ADDRESS_ID"
    [ -n "$CUSTOMER_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/customers/$CUSTOMER_ID"
    [ -n "$SUPPLIER_ADDRESS" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID/address"
    [ -n "$SUPPLIER_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID"
}
trap cleanup EXIT

# ---------------------------------------------------------------------------
# preflight
# ---------------------------------------------------------------------------

echo "Testing $BASE_URL"
if ! curl -s -o /dev/null "$BASE_URL/productcategories"; then
    echo "${RED}Cannot connect to $BASE_URL. Is the API running? (mvn spring-boot:run)${RESET}"
    trap - EXIT
    exit 1
fi

RUN_TAG="test-$(date +%s)"
ORDER_DATE="2026-09-11T10:00:05"
DELIVERY_DATE="2026-09-14T12:00:05"
ADDRESS_JSON='{"streetAddress":"Testikatu 1","postalCode":"00100","city":"Helsinki","country":"Finland"}'

# ---------------------------------------------------------------------------
# suppliers (vendors)
# ---------------------------------------------------------------------------

section "Suppliers"

call curl -s -X POST "$BASE_URL/suppliers" -H "$JSON" -d '{"contactName":"No name"}'
check "POST /suppliers without a name -> 400" 400

call curl -s -X POST "$BASE_URL/suppliers" -H "$JSON" -d '{"name":"x","email":"not-an-email"}'
check "POST /suppliers with an invalid e-mail -> 400" 400

call curl -s -X POST "$BASE_URL/suppliers" -H "$JSON" -d "{\"name\":\"$RUN_TAG Oy\",\"contactName\":\"Testi Testaaja\",\"phone\":\"0401234567\",\"email\":\"$RUN_TAG@example.com\"}"
if check_jq "POST /suppliers creates a supplier" 201 '.name == $n and .contactName == "Testi Testaaja"' --arg n "$RUN_TAG Oy"; then
    SUPPLIER_ID="$(extract_id "$BODY")"
fi

call curl -s -X GET "$BASE_URL/suppliers"
check_jq "GET /suppliers lists suppliers" 200 'any(.[]; .id == $id)' --argjson id "${SUPPLIER_ID:-0}"

if [ -n "$SUPPLIER_ID" ]; then
    call curl -s -X GET "$BASE_URL/suppliers/$SUPPLIER_ID"
    check_jq "GET /suppliers/{id} returns the supplier" 200 '.id == $id' --argjson id "$SUPPLIER_ID"

    call curl -s -X PUT "$BASE_URL/suppliers/$SUPPLIER_ID" -H "$JSON" -d "{\"name\":\"$RUN_TAG Oy\",\"contactName\":\"Updated Contact\",\"phone\":\"0507654321\",\"email\":\"$RUN_TAG@example.com\"}"
    check_jq "PUT /suppliers/{id} updates the supplier" 200 '.contactName == "Updated Contact" and .phone == "0507654321"'

    call curl -s -X GET "$BASE_URL/suppliers/$SUPPLIER_ID/address"
    check "GET /suppliers/{id}/address without an address -> 404" 404

    call curl -s -X PUT "$BASE_URL/suppliers/$SUPPLIER_ID/address" -H "$JSON" -d "$ADDRESS_JSON"
    if check_jq "PUT /suppliers/{id}/address creates the address" 200 '.city == "Helsinki"'; then
        SUPPLIER_ADDRESS="$(extract_id "$BODY")"
    fi

    call curl -s -X PUT "$BASE_URL/suppliers/$SUPPLIER_ID/address" -H "$JSON" -d '{"streetAddress":"Uusikatu 2","city":"Tampere"}'
    check_jq "PUT /suppliers/{id}/address again replaces it (same row)" 200 \
        '.city == "Tampere" and .id == $id' --argjson id "${SUPPLIER_ADDRESS:-0}"

    call curl -s -X GET "$BASE_URL/suppliers/$SUPPLIER_ID/address"
    check_jq "GET /suppliers/{id}/address returns it" 200 '.streetAddress == "Uusikatu 2"'

    call curl -s -X PUT "$BASE_URL/suppliers/$SUPPLIER_ID/address" -H "$JSON" -d '{"streetAddress":"x"}'
    check "PUT /suppliers/{id}/address without a city -> 400" 400

    call curl -s -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID"
    check "DELETE /suppliers/{id} with an address -> 409 (foreign key)" 409
fi

call curl -s -X GET "$BASE_URL/suppliers/999999999"
check "GET /suppliers/{unknown id} -> 404" 404

call curl -s -X PUT "$BASE_URL/suppliers/999999999" -H "$JSON" -d '{"name":"x"}'
check "PUT /suppliers/{unknown id} -> 404" 404

# ---------------------------------------------------------------------------
# product categories
# ---------------------------------------------------------------------------

section "Product categories"

call curl -s -X POST "$BASE_URL/productcategories" -H "$JSON" -d "{\"name\":\"$RUN_TAG\",\"description\":\"Created by test.sh\"}"
if check "POST /productcategories creates a category" 201 "\"name\":\"$RUN_TAG\""; then
    CATEGORY_ID="$(extract_id "$BODY")"
fi

call curl -s -X POST "$BASE_URL/productcategories" -H "$JSON" -d '{"description":"no name"}'
check "POST /productcategories without name -> 400" 400

call curl -s -X GET "$BASE_URL/productcategories"
check "GET /productcategories lists categories" 200 "\"name\":\"$RUN_TAG\""

if [ -n "$CATEGORY_ID" ]; then
    call curl -s -X GET "$BASE_URL/productcategories/$CATEGORY_ID"
    check "GET /productcategories/{id} returns the category" 200 "\"id\":$CATEGORY_ID" "\"name\":\"$RUN_TAG\""

    call curl -s -X PUT "$BASE_URL/productcategories/$CATEGORY_ID" -H "$JSON" -d "{\"name\":\"$RUN_TAG-upd\",\"description\":\"Updated by test.sh\"}"
    check "PUT /productcategories/{id} updates the category" 200 "\"name\":\"$RUN_TAG-upd\"" "Updated by test.sh"
fi

call curl -s -X GET "$BASE_URL/productcategories/999999999"
check "GET /productcategories/{unknown id} -> 404" 404

call curl -s -X PUT "$BASE_URL/productcategories/999999999" -H "$JSON" -d '{"name":"x"}'
check "PUT /productcategories/{unknown id} -> 404" 404

# ---------------------------------------------------------------------------
# products
# ---------------------------------------------------------------------------

section "Products"

PRODUCT_JSON="{\"name\":\"$RUN_TAG product\",\"description\":\"Created by test.sh\",\"price\":39.90,\"stockQuantity\":25,\"categoryId\":${CATEGORY_ID:-null},\"supplierId\":${SUPPLIER_ID:-null}}"
call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d "$PRODUCT_JSON"
if check_jq "POST /products creates a product" 201 \
    '.name == $n and .stockQuantity == 25 and .supplierId == $s' --arg n "$RUN_TAG product" --argjson s "${SUPPLIER_ID:-0}"; then
    PRODUCT_ID="$(extract_id "$BODY")"
fi

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"","price":1,"stockQuantity":1}'
check "POST /products with blank name -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":-1,"stockQuantity":1}'
check "POST /products with negative price -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":1,"stockQuantity":-5}'
check "POST /products with negative stock -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":1,"stockQuantity":1,"categoryId":999999999}'
check "POST /products with unknown category -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":1,"stockQuantity":1,"supplierId":999999999}'
check "POST /products with unknown supplier -> 400" 400

call curl -s -X GET "$BASE_URL/products"
check_jq "GET /products lists all products" 200 'length > 1 and any(.[]; .id == $id)' --argjson id "${PRODUCT_ID:-0}"

if [ -n "$PRODUCT_ID" ]; then
    call curl -s -X GET "$BASE_URL/products?supplierId=$SUPPLIER_ID"
    check_jq "GET /products?supplierId= lists only that supplier's products" 200 \
        'length == 1 and .[0].id == $id' --argjson id "$PRODUCT_ID"

    call curl -s -X GET "$BASE_URL/products?categoryId=$CATEGORY_ID"
    check_jq "GET /products?categoryId= lists only that category's products" 200 \
        'length == 1 and .[0].category.id == $c' --argjson c "$CATEGORY_ID"

    call curl -s -X GET "$BASE_URL/products/$PRODUCT_ID"
    check_jq "GET /products/{id} returns the product with its category" 200 \
        '.id == $id and .category.name == $c' --argjson id "$PRODUCT_ID" --arg c "$RUN_TAG-upd"

    call curl -s -X PUT "$BASE_URL/products/$PRODUCT_ID" -H "$JSON" -d "{\"name\":\"$RUN_TAG product upd\",\"description\":\"Updated\",\"price\":59.90,\"stockQuantity\":20,\"categoryId\":$CATEGORY_ID,\"supplierId\":$SUPPLIER_ID}"
    check_jq "PUT /products/{id} updates the product" 200 '.price == 59.9 and .stockQuantity == 20'
fi

call curl -s -X GET "$BASE_URL/products/999999999"
check "GET /products/{unknown id} -> 404" 404

call curl -s -X DELETE "$BASE_URL/products/999999999"
check "DELETE /products/{unknown id} -> 404" 404

if [ -n "$CATEGORY_ID" ] && [ -n "$PRODUCT_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
    check "DELETE /productcategories/{id} with products in it -> 409 (foreign key)" 409
fi

if [ -n "$SUPPLIER_ID" ] && [ -n "$PRODUCT_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID"
    check "DELETE /suppliers/{id} with products -> 409 (foreign key)" 409
fi

# ---------------------------------------------------------------------------
# customers and addresses
# ---------------------------------------------------------------------------

section "Customers"

call curl -s -X POST "$BASE_URL/customers" -H "$JSON" -d '{"firstName":"Test","lastName":"User","email":"not-an-email"}'
check "POST /customers with an invalid e-mail -> 400" 400

EMAIL="$RUN_TAG@example.com"
call curl -s -X POST "$BASE_URL/customers" -H "$JSON" -d "{\"firstName\":\"Test\",\"lastName\":\"User\",\"email\":\"$EMAIL\",\"phone\":\"0401234567\"}"
if check_jq "POST /customers creates a customer" 201 '.email == $e' --arg e "$EMAIL"; then
    CUSTOMER_ID="$(extract_id "$BODY")"
fi

call curl -s -X GET "$BASE_URL/customers?page=0&size=3"
check_jq "GET /customers is paginated" 200 '(.content | length) == 3 and .size == 3 and .totalElements > 3'

if [ -n "$CUSTOMER_ID" ]; then
    call curl -s -X GET "$BASE_URL/customers/$CUSTOMER_ID"
    check_jq "GET /customers/{id} returns the customer" 200 '.id == $id' --argjson id "$CUSTOMER_ID"

    call curl -s -X PUT "$BASE_URL/customers/$CUSTOMER_ID" -H "$JSON" -d "{\"firstName\":\"Test\",\"lastName\":\"Updated\",\"email\":\"$EMAIL\",\"phone\":\"0507654321\"}"
    check_jq "PUT /customers/{id} updates the customer" 200 '.lastName == "Updated" and .phone == "0507654321"'

    call curl -s -X GET "$BASE_URL/customers?email=$EMAIL"
    check_jq "GET /customers?email= finds the customer" 200 '.totalElements == 1 and .content[0].id == $id' --argjson id "$CUSTOMER_ID"

    call curl -s -X POST "$BASE_URL/customers/$CUSTOMER_ID/addresses" -H "$JSON" -d "$ADDRESS_JSON"
    if check_jq "POST /customers/{id}/addresses adds an address" 201 '.city == "Helsinki"'; then
        ADDRESS_ID="$(extract_id "$BODY")"
    fi

    call curl -s -X POST "$BASE_URL/customers/$CUSTOMER_ID/addresses" -H "$JSON" -d '{"streetAddress":"x"}'
    check "POST /customers/{id}/addresses without a city -> 400" 400

    call curl -s -X GET "$BASE_URL/customers/$CUSTOMER_ID/addresses"
    check_jq "GET /customers/{id}/addresses lists it" 200 'length == 1'

    if [ -n "$ADDRESS_ID" ]; then
        call curl -s -X PUT "$BASE_URL/customers/$CUSTOMER_ID/addresses/$ADDRESS_ID" -H "$JSON" -d '{"streetAddress":"Testikatu 2","postalCode":"33100","city":"Tampere","country":"Finland"}'
        check_jq "PUT /customers/{id}/addresses/{addressId} updates it" 200 '.city == "Tampere" and .postalCode == "33100"'

        call curl -s -X GET "$BASE_URL/customers/$CUSTOMER_ID/addresses/$ADDRESS_ID"
        check_jq "GET /customers/{id}/addresses/{addressId} returns it" 200 '.streetAddress == "Testikatu 2"'
    fi

    call curl -s -X GET "$BASE_URL/customers/$CUSTOMER_ID/addresses/$OTHER_ADDRESS_ID"
    check "GET another customer's address through this customer -> 404" 404

    call curl -s -X DELETE "$BASE_URL/customers/$CUSTOMER_ID"
    check "DELETE /customers/{id} with an address -> 409 (foreign key)" 409
fi

call curl -s -X GET "$BASE_URL/customers/999999999"
check "GET /customers/{unknown id} -> 404" 404

# ---------------------------------------------------------------------------
# orders (transaction + stock and status triggers)
# ---------------------------------------------------------------------------

section "Orders"

order_json() { # customer address product quantity [status]
    local status=""
    [ -n "${5:-}" ] && status=",\"status\":\"$5\""
    echo "{\"customerId\":${1:-0},\"orderDate\":\"$ORDER_DATE\",\"shippingAddressId\":${2:-0}$status,\"orderItems\":[{\"productId\":${3:-0},\"quantity\":$4}]}"
}

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$ADDRESS_ID" "$PRODUCT_ID" 3)"
if check_jq "POST /orders creates an order (status defaults to NEW, unit price from product)" 201 \
    '.status == "NEW" and (.orderItems | length) == 1 and .orderItems[0].unitPrice == 59.9'; then
    ORDER_ID="$(extract_id "$BODY")"
fi

if [ -n "$PRODUCT_ID" ]; then
    STOCK="$(stock_of "$PRODUCT_ID")"
    if [ "$STOCK" = "17" ]; then pass "trigger reserved stock: 20 - 3 = 17"; else fail "trigger reserved stock: 20 - 3 = 17" "stock is $STOCK"; fi
fi

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$ADDRESS_ID" "$PRODUCT_ID" 1000)"
check "POST /orders with more than the stock -> 409" 409 "Insufficient stock"

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "{\"customerId\":${CUSTOMER_ID:-0},\"orderDate\":\"$ORDER_DATE\",\"shippingAddressId\":${ADDRESS_ID:-0},\"orderItems\":[{\"productId\":${PRODUCT_ID:-0},\"quantity\":1},{\"productId\":${PRODUCT_ID:-0},\"quantity\":1}]}"
check "POST /orders with the same product twice -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$OTHER_ADDRESS_ID" "$PRODUCT_ID" 1)"
check "POST /orders with another customer's address -> 400" 400 "does not belong"

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json 999999999 "$ADDRESS_ID" "$PRODUCT_ID" 1)"
check "POST /orders with unknown customer -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$ADDRESS_ID" 999999999 1)"
check "POST /orders with unknown product -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "{\"customerId\":${CUSTOMER_ID:-0},\"orderDate\":\"$ORDER_DATE\",\"shippingAddressId\":${ADDRESS_ID:-0},\"orderItems\":[]}"
check "POST /orders without items -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$ADDRESS_ID" "$PRODUCT_ID" 1 LOST)"
check "POST /orders with an invalid status -> 400" 400

if [ -n "$ORDER_ID" ]; then
    call curl -s -X GET "$BASE_URL/orders/$ORDER_ID"
    check_jq "GET /orders/{id} returns the order with items" 200 \
        '.id == $id and (.orderItems | length) == 1 and .customer.id == $cid and .shippingAddress.id == $aid' \
        --argjson id "$ORDER_ID" --argjson cid "$CUSTOMER_ID" --argjson aid "$ADDRESS_ID"

    call curl -s -X GET "$BASE_URL/orders?customerId=$CUSTOMER_ID"
    check_jq "GET /orders?customerId= lists the customer's orders" 200 \
        '.totalElements == 1 and .content[0].id == $id' --argjson id "$ORDER_ID"

    call curl -s -X GET "$BASE_URL/orders?supplierId=$SUPPLIER_ID"
    check_jq "GET /orders?supplierId= lists orders containing the supplier's products" 200 \
        '.totalElements == 1 and .content[0].id == $id' --argjson id "$ORDER_ID"

    call curl -s -X PUT "$BASE_URL/orders/$ORDER_ID" -H "$JSON" -d "{\"deliveryDate\":\"$DELIVERY_DATE\",\"status\":\"SHIPPED\"}"
    check_jq "PUT /orders/{id} ships the order (partial update)" 200 \
        '.status == "SHIPPED" and .deliveryDate == $d and .orderDate == $o' --arg d "$DELIVERY_DATE" --arg o "$ORDER_DATE"

    call curl -s -X PUT "$BASE_URL/orders/$ORDER_ID" -H "$JSON" -d '{"status":"NEW"}'
    check "PUT SHIPPED -> NEW is rejected by the status trigger -> 409" 409 "cannot change"

    call curl -s -X PUT "$BASE_URL/orders/$ORDER_ID" -H "$JSON" -d '{"deliveryDate":"2000-01-01T00:00:00"}'
    check "PUT a delivery date before the order date -> 400" 400 "deliveryDate"

    call curl -s -X PUT "$BASE_URL/orders/$ORDER_ID" -H "$JSON" -d '{"status":"LOST"}'
    check "PUT an invalid status -> 400" 400

    if [ -n "$PRODUCT_ID" ]; then
        call curl -s -X DELETE "$BASE_URL/products/$PRODUCT_ID"
        check "DELETE /products/{id} used in an order -> 409 (foreign key)" 409
    fi

    if [ -n "$ADDRESS_ID" ]; then
        call curl -s -X DELETE "$BASE_URL/customers/$CUSTOMER_ID/addresses/$ADDRESS_ID"
        check "DELETE an address that orders ship to -> 409 (foreign key)" 409
    fi
fi

# second order, cancelled with PUT -> the trigger puts the items back in stock
call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$(order_json "$CUSTOMER_ID" "$ADDRESS_ID" "$PRODUCT_ID" 5)"
if check_jq "POST /orders creates a second order" 201 '.status == "NEW"'; then
    ORDER2_ID="$(extract_id "$BODY")"
fi

if [ -n "$ORDER2_ID" ] && [ -n "$PRODUCT_ID" ]; then
    STOCK="$(stock_of "$PRODUCT_ID")"
    if [ "$STOCK" = "12" ]; then pass "trigger reserved stock: 17 - 5 = 12"; else fail "trigger reserved stock: 17 - 5 = 12" "stock is $STOCK"; fi

    call curl -s -X PUT "$BASE_URL/orders/$ORDER2_ID" -H "$JSON" -d '{"status":"CANCELLED"}'
    check_jq "PUT /orders/{id} with status CANCELLED cancels it" 200 '.status == "CANCELLED"'

    STOCK="$(stock_of "$PRODUCT_ID")"
    if [ "$STOCK" = "17" ]; then pass "cancel trigger restocked: 12 + 5 = 17"; else fail "cancel trigger restocked: 12 + 5 = 17" "stock is $STOCK"; fi

    call curl -s -X PUT "$BASE_URL/orders/$ORDER2_ID" -H "$JSON" -d '{"status":"SHIPPED"}'
    check "PUT CANCELLED -> SHIPPED is rejected by the status trigger -> 409" 409 "cannot change"
fi

call curl -s -X GET "$BASE_URL/orders?page=0&size=5"
check_jq "GET /orders is paginated, newest first" 200 \
    '(.content | length) == 5 and .size == 5 and .totalElements > 5
     and ([.content[].orderDate] == ([.content[].orderDate] | sort | reverse))'

call curl -s -X GET "$BASE_URL/orders?status=CANCELLED&size=3"
check_jq "GET /orders?status=CANCELLED filters by status" 200 \
    '(.content | length) > 0 and all(.content[]; .status == "CANCELLED")'

call curl -s -X GET "$BASE_URL/orders/999999999"
check "GET /orders/{unknown id} -> 404" 404

call curl -s -X PUT "$BASE_URL/orders/999999999" -H "$JSON" -d '{"status":"SHIPPED"}'
check "PUT /orders/{unknown id} -> 404" 404

if [ -n "$CUSTOMER_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/customers/$CUSTOMER_ID"
    check "DELETE /customers/{id} with orders -> 409 (foreign key)" 409
fi

# ---------------------------------------------------------------------------
# deletes (in FK-safe order)
# ---------------------------------------------------------------------------

section "Deletes"

if [ -n "$ORDER_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/orders/$ORDER_ID"
    if check "DELETE /orders/{id} deletes the order" 200 "Order deleted successfully" "\"id\":$ORDER_ID"; then
        call curl -s -X GET "$BASE_URL/orders/$ORDER_ID"
        check "GET deleted order -> 404" 404 && ORDER_ID=""
    fi
fi

if [ -n "$ORDER2_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/orders/$ORDER2_ID"
    check "DELETE /orders/{id} deletes the cancelled order" 200 "Order deleted successfully" && ORDER2_ID=""
fi

if [ -n "$PRODUCT_ID" ]; then
    STOCK="$(stock_of "$PRODUCT_ID")"
    if [ "$STOCK" = "17" ]; then pass "deleting shipped/cancelled orders leaves stock at 17"; else fail "deleting shipped/cancelled orders leaves stock at 17" "stock is $STOCK"; fi

    call curl -s -X DELETE "$BASE_URL/products/$PRODUCT_ID"
    if check "DELETE /products/{id} deletes the product" 200 "Product deleted successfully" "\"id\":$PRODUCT_ID"; then
        call curl -s -X GET "$BASE_URL/products/$PRODUCT_ID"
        check "GET deleted product -> 404" 404 && PRODUCT_ID=""
    fi
fi

if [ -n "$CATEGORY_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
    if check "DELETE /productcategories/{id} deletes the category" 200 "Product category deleted successfully" "\"id\":$CATEGORY_ID"; then
        call curl -s -X GET "$BASE_URL/productcategories/$CATEGORY_ID"
        check "GET deleted category -> 404" 404 && CATEGORY_ID=""
    fi
fi

if [ -n "$ADDRESS_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/customers/$CUSTOMER_ID/addresses/$ADDRESS_ID"
    check "DELETE /customers/{id}/addresses/{addressId} deletes the address" 200 "Address deleted successfully" && ADDRESS_ID=""
fi

if [ -n "$CUSTOMER_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/customers/$CUSTOMER_ID"
    if check "DELETE /customers/{id} deletes the customer" 200 "Customer deleted successfully"; then
        call curl -s -X GET "$BASE_URL/customers/$CUSTOMER_ID"
        check "GET deleted customer -> 404" 404 && CUSTOMER_ID=""
    fi
fi

if [ -n "$SUPPLIER_ADDRESS" ]; then
    call curl -s -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID/address"
    if check "DELETE /suppliers/{id}/address deletes the address" 200 "Supplier address deleted successfully"; then
        SUPPLIER_ADDRESS=""
        call curl -s -X GET "$BASE_URL/suppliers/$SUPPLIER_ID/address"
        check "GET deleted supplier address -> 404" 404
    fi
fi

if [ -n "$SUPPLIER_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/suppliers/$SUPPLIER_ID"
    if check "DELETE /suppliers/{id} deletes the supplier" 200 "Supplier deleted successfully"; then
        call curl -s -X GET "$BASE_URL/suppliers/$SUPPLIER_ID"
        check "GET deleted supplier -> 404" 404 && SUPPLIER_ID=""
    fi
fi

call curl -s -X DELETE "$BASE_URL/orders/999999999"
check "DELETE /orders/{unknown id} -> 404" 404

# ---------------------------------------------------------------------------
# summary
# ---------------------------------------------------------------------------

echo
echo "${BOLD}Result:${RESET} ${GREEN}$PASSED passed${RESET}, ${RED}$FAILED failed${RESET}"
if [ "$FAILED" -gt 0 ]; then
    for t in "${FAILED_TESTS[@]}"; do
        echo "  - $t"
    done
    exit 1
fi
exit 0
