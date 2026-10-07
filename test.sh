#!/usr/bin/env bash
#
# Smoke tests for the vendor-api REST endpoints.
#
# Start the API first (mvn spring-boot:run), then run:
#
#   ./test.sh
#
# Configuration (environment variables):
#   BASE_URL      API base url                          (default http://localhost:8080/api)
#   CUSTOMER_ID   existing customer used for orders     (default 1)
#   ADDRESS_ID    existing customer address for orders  (default 1)
#   SUPPLIER_ID   existing supplier used for products   (default 1)
#   PRODUCT_ID    existing product added to test order  (default 1)
#   TEST_LIST_ORDERS=1   also run GET /api/orders (slow: returns every order in the database)
#   SHOW_RESPONSES=0     only print PASS/FAIL lines, not the commands and responses
#   LIST_LIMIT=N         how many items of a list response to print (default 3, 0 = all)
#
# The script creates its own category, product and order, and deletes them again at the end
# (also when a test fails). Existing data is only read, except for the cancel-old and
# delete-logs endpoints, which are called with a cutoff in 1900 so they change nothing.
#
# Requires: bash, curl, jq (brew install jq). Exit code is 0 when all tests pass, 1 otherwise.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080/api}"
CUSTOMER_ID="${CUSTOMER_ID:-1}"
ADDRESS_ID="${ADDRESS_ID:-1}"
SUPPLIER_ID="${SUPPLIER_ID:-1}"
PRODUCT_ID="${PRODUCT_ID:-1}"
TEST_LIST_ORDERS="${TEST_LIST_ORDERS:-0}"
SHOW_RESPONSES="${SHOW_RESPONSES:-1}"
LIST_LIMIT="${LIST_LIMIT:-3}"

if ! command -v jq >/dev/null 2>&1; then
    echo "jq is required to display the responses (macOS: brew install jq, Debian/Ubuntu: apt install jq)"
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

CATEGORY_ID=""
NEW_PRODUCT_ID=""
ORDER_ID=""

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

section() {
    echo
    echo "${BOLD}$1${RESET}"
}

cleanup() {
    # delete whatever this run created, in FK-safe order
    [ -n "$ORDER_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/orders/$ORDER_ID"
    [ -n "$NEW_PRODUCT_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/products/$NEW_PRODUCT_ID"
    [ -n "$CATEGORY_ID" ] && curl -s -o /dev/null -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
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

PRODUCT_JSON="{\"name\":\"$RUN_TAG product\",\"description\":\"Created by test.sh\",\"price\":39.90,\"stockQuantity\":25,\"categoryId\":${CATEGORY_ID:-null},\"supplierId\":$SUPPLIER_ID}"
call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d "$PRODUCT_JSON"
if check "POST /products creates a product" 201 "\"name\":\"$RUN_TAG product\"" "\"stockQuantity\":25" "\"supplierId\":$SUPPLIER_ID"; then
    NEW_PRODUCT_ID="$(extract_id "$BODY")"
fi

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"","price":1,"stockQuantity":1}'
check "POST /products with blank name -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":-1,"stockQuantity":1}'
check "POST /products with negative price -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":1,"stockQuantity":1,"categoryId":999999999}'
check "POST /products with unknown category -> 400" 400

call curl -s -X POST "$BASE_URL/products" -H "$JSON" -d '{"name":"x","price":1,"stockQuantity":1,"supplierId":999999999}'
check "POST /products with unknown supplier -> 400" 400

call curl -s -X GET "$BASE_URL/products"
check "GET /products lists products" 200 "\"name\":\"$RUN_TAG product\""

if [ -n "$NEW_PRODUCT_ID" ]; then
    call curl -s -X GET "$BASE_URL/products/$NEW_PRODUCT_ID"
    check "GET /products/{id} returns the product" 200 "\"id\":$NEW_PRODUCT_ID" "\"category\":{\"id\":$CATEGORY_ID"

    call curl -s -X PUT "$BASE_URL/products/$NEW_PRODUCT_ID" -H "$JSON" -d "{\"name\":\"$RUN_TAG product upd\",\"description\":\"Updated\",\"price\":59.90,\"stockQuantity\":20,\"categoryId\":${CATEGORY_ID:-null},\"supplierId\":$SUPPLIER_ID}"
    check "PUT /products/{id} updates the product" 200 "\"name\":\"$RUN_TAG product upd\"" "\"stockQuantity\":20"
fi

call curl -s -X GET "$BASE_URL/products/999999999"
check "GET /products/{unknown id} -> 404" 404

call curl -s -X DELETE "$BASE_URL/products/999999999"
check "DELETE /products/{unknown id} -> 404" 404

if [ -n "$CATEGORY_ID" ] && [ -n "$NEW_PRODUCT_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
    check "DELETE /productcategories/{id} with products in it -> 409" 409
fi

# ---------------------------------------------------------------------------
# orders
# ---------------------------------------------------------------------------

section "Orders"

ITEMS="{\"productId\":$PRODUCT_ID,\"quantity\":2,\"unitPrice\":29.90}"
if [ -n "$NEW_PRODUCT_ID" ] && [ "$NEW_PRODUCT_ID" != "$PRODUCT_ID" ]; then
    ITEMS="$ITEMS,{\"productId\":$NEW_PRODUCT_ID,\"quantity\":1,\"unitPrice\":39.90}"
fi
ORDER_JSON="{\"customerId\":$CUSTOMER_ID,\"orderDate\":\"2026-09-11T10:00:05\",\"deliveryDate\":null,\"shippingAddressId\":$ADDRESS_ID,\"status\":\"NEW\",\"orderItems\":[$ITEMS]}"

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "$ORDER_JSON"
if check "POST /orders creates an order with items" 201 "\"status\":\"NEW\"" "\"orderItems\":[" "\"productId\":$PRODUCT_ID"; then
    ORDER_ID="$(extract_id "$BODY")"
fi

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "{\"customerId\":999999999,\"orderDate\":\"2026-09-11T10:00:05\",\"shippingAddressId\":$ADDRESS_ID,\"status\":\"NEW\",\"orderItems\":[]}"
check "POST /orders with unknown customer -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "{\"customerId\":$CUSTOMER_ID,\"orderDate\":\"2026-09-11T10:00:05\",\"shippingAddressId\":$ADDRESS_ID,\"status\":\"NEW\",\"orderItems\":[{\"productId\":999999999,\"quantity\":1,\"unitPrice\":1.0}]}"
check "POST /orders with unknown product -> 400" 400

call curl -s -X POST "$BASE_URL/orders" -H "$JSON" -d "{\"customerId\":$CUSTOMER_ID,\"shippingAddressId\":$ADDRESS_ID,\"status\":\"NEW\"}"
check "POST /orders without orderDate -> 400" 400

if [ -n "$ORDER_ID" ]; then
    call curl -s -X GET "$BASE_URL/orders/$ORDER_ID"
    check "GET /orders/{id} returns the order with items" 200 "\"id\":$ORDER_ID" "\"orderItems\":[" "\"customer\":{\"id\":$CUSTOMER_ID"

    call curl -s -X PUT "$BASE_URL/orders/$ORDER_ID" -H "$JSON" -d '{"deliveryDate":"2026-09-14T12:00:05","status":"SHIPPED"}'
    check "PUT /orders/{id} partially updates the order" 200 "\"status\":\"SHIPPED\"" "\"deliveryDate\":\"2026-09-14T12:00:05\"" "\"orderDate\":\"2026-09-11T10:00:05\""

    call curl -s -X GET "$BASE_URL/orders/$ORDER_ID"
    check "GET /orders/{id} shows the update" 200 "\"status\":\"SHIPPED\""

    if [ -n "$NEW_PRODUCT_ID" ]; then
        call curl -s -X DELETE "$BASE_URL/products/$NEW_PRODUCT_ID"
        check "DELETE /products/{id} used in an order -> 409" 409
    fi
fi

call curl -s -X GET "$BASE_URL/orders/999999999"
check "GET /orders/{unknown id} -> 404" 404

call curl -s -X PUT "$BASE_URL/orders/999999999" -H "$JSON" -d '{"status":"SHIPPED"}'
check "PUT /orders/{unknown id} -> 404" 404

if [ "$TEST_LIST_ORDERS" = "1" ]; then
    call curl -s -X GET "$BASE_URL/orders"
    check "GET /orders lists orders" 200 "\"id\":"
else
    echo "  ${YELLOW}SKIP${RESET} GET /orders (set TEST_LIST_ORDERS=1, returns every order)"
fi

call curl -s -X POST "$BASE_URL/orders/cancel-old?cutoff=1900-01-01T00:00:00"
if [ "$BODY" = "0" ]; then check "POST /orders/cancel-old (cutoff 1900) cancels nothing" 200; else fail "POST /orders/cancel-old (cutoff 1900) cancels nothing" "expected body 0"; fi

call curl -s -X DELETE "$BASE_URL/orders/delete-logs?cutoff=1900-01-01T00:00:00"
if [ "$BODY" = "0" ]; then check "DELETE /orders/delete-logs (cutoff 1900) deletes nothing" 200; else fail "DELETE /orders/delete-logs (cutoff 1900) deletes nothing" "expected body 0"; fi

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

if [ -n "$NEW_PRODUCT_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/products/$NEW_PRODUCT_ID"
    if check "DELETE /products/{id} deletes the product" 200 "Product deleted successfully" "\"id\":$NEW_PRODUCT_ID"; then
        call curl -s -X GET "$BASE_URL/products/$NEW_PRODUCT_ID"
        check "GET deleted product -> 404" 404 && NEW_PRODUCT_ID=""
    fi
fi

if [ -n "$CATEGORY_ID" ]; then
    call curl -s -X DELETE "$BASE_URL/productcategories/$CATEGORY_ID"
    if check "DELETE /productcategories/{id} deletes the category" 200 "Product category deleted successfully" "\"id\":$CATEGORY_ID"; then
        call curl -s -X GET "$BASE_URL/productcategories/$CATEGORY_ID"
        check "GET deleted category -> 404" 404 && CATEGORY_ID=""
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
