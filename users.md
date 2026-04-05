LOGIN=$(curl -s -X POST http://localhost:8080/v1/manage/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@retainiq.com","password":"admin123"}')
echo "$LOGIN" | python3 -m json.tool

MGMT_TOKEN=$(echo "$LOGIN" | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")

# Test list tenants
echo -e "\n=== TENANTS ==="
curl -s http://localhost:8080/v1/manage/tenants \
  -H "Authorization: Bearer $MGMT_TOKEN" | python3 -m json.tool

# Test list users
echo -e "\n=== USERS ==="
curl -s http://localhost:8080/v1/manage/users \
  -H "Authorization: Bearer $MGMT_TOKEN" | python3 -m json.tool

# Test dashboard stats
echo -e "\n=== DASHBOARD STATS ==="
curl -s http://localhost:8080/v1/manage/dashboard/stats \
  -H "Authorization: Bearer $MGMT_TOKEN" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f'Decisions today: {d[\"total_decisions_today\"]}  |  Avg latency: {d[\"avg_latency_ms\"]}ms  |  Attach rate: {d[\"offer_attach_rate\"]*100}%')"

# Test Swagger UI
echo -e "\n=== SWAGGER ==="
curl -s -o /dev/null -w "Swagger UI: HTTP %{http_code}" http://localhost:8080/webjars/swagger-ui/index.html
echo ""
curl -s -o /dev/null -w "OpenAPI docs: HTTP %{http_code}" http://localhost:8080