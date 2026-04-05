.PHONY: help build test run docker-up docker-down clean curl-decide curl-token

# Default
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

# Build & Test
build: ## Build the application
	./gradlew build -x test

test: ## Run all tests
	./gradlew test

clean: ## Clean build artifacts
	./gradlew clean

# Run
run: ## Run locally (requires PostgreSQL, Redis)
	./gradlew bootRun --args='--spring.profiles.active=local'

# Docker
docker-up: ## Start full stack (app + PostgreSQL + Redis + Kafka)
	docker compose up -d --build

docker-down: ## Stop and remove containers
	docker compose down

docker-logs: ## Tail application logs
	docker compose logs -f app

# Quick test commands
TENANT_ID := 00000000-0000-0000-0000-000000000001
BASE_URL := http://localhost:8080

curl-token: ## Get a JWT token for the demo tenant
	@curl -s -X POST $(BASE_URL)/v1/auth/token \
		-H "Content-Type: application/json" \
		-d '{"grant_type":"client_credentials","client_id":"$(TENANT_ID)","client_secret":"demo"}' | python3 -m json.tool

curl-decide: ## Make a test decision call (set TOKEN env var first)
	@curl -s -X POST $(BASE_URL)/v1/decide \
		-H "Authorization: Bearer $(TOKEN)" \
		-H "Content-Type: application/json" \
		-H "X-Tenant-ID: $(TENANT_ID)" \
		-d '{"subscriber_id":"sub_12345","channel":"app","signals":{"frustration_score":0.7,"intent":"cancel","prior_contacts_30d":4},"context":{"reason_code":"billing","market":"AE"},"options":{"max_offers":3}}' | python3 -m json.tool

curl-decide-high-risk: ## Decision for high-risk subscriber
	@curl -s -X POST $(BASE_URL)/v1/decide \
		-H "Authorization: Bearer $(TOKEN)" \
		-H "Content-Type: application/json" \
		-H "X-Tenant-ID: $(TENANT_ID)" \
		-d '{"subscriber_id":"sub_99999","channel":"agentforce","signals":{"frustration_score":0.95,"intent":"cancel","prior_contacts_30d":8},"context":{"reason_code":"competitor","market":"SA"},"options":{"max_offers":5}}' | python3 -m json.tool

curl-outcome: ## Post an outcome (set TOKEN and DECISION_ID env vars)
	@curl -s -X POST $(BASE_URL)/v1/outcome \
		-H "Authorization: Bearer $(TOKEN)" \
		-H "Content-Type: application/json" \
		-H "X-Tenant-ID: $(TENANT_ID)" \
		-d '{"decision_id":"$(DECISION_ID)","offer_sku":"VAS-STREAM-PLUS","outcome":"accepted","churn_prevented":true}' -w "\nHTTP %{http_code}\n"

curl-health: ## Check health endpoint
	@curl -s $(BASE_URL)/health | python3 -m json.tool

curl-metrics: ## Check Prometheus metrics
	@curl -s $(BASE_URL)/actuator/prometheus | head -50

# Load Testing
load-test: ## Run full load test (requires k6: brew install k6)
	k6 run loadtest/k6-decide.js

load-smoke: ## Run quick smoke test (10s)
	k6 run loadtest/k6-smoke.js

load-test-report: ## Run load test with HTML report
	k6 run --out json=loadtest/results/raw.json loadtest/k6-decide.js

# Documentation
docs: ## Generate Dokka API docs
	./gradlew docs

# Console
console-dev: ## Start React console dev server
	cd console && npm run dev

console-build: ## Build React console
	cd console && npm run build
