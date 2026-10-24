include .envrc

# ==================================================================================== #
# HELPERS
# ==================================================================================== #

## help: print this help message
.PHONY: help
help:
	@echo 'Usage:'
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | column -t -s ':' | sed -e 's/^/ /'

.PHONY: confirm
confirm:
	@echo -n 'Are you sure? [y/N] ' && read ans && [ $${ans:-N} = y ]

# ==================================================================================== #
# DEVELOPMENT
# ==================================================================================== #

port ?= 4000

## run/api port=$1: run the cmd/api application with local Ollama LLM (port defaults to 4000)
.PHONY: run/api
run/api:
	go run ./cmd/api -port=${port} -db-dsn=${KONNICHIWAH_DB_DSN} -llm=local -local-llm-model=gemma4:12b

## run/api/cloud port=$1: run the cmd/api application with cloud LLMs (Gemini + Groq)
.PHONY: run/api/cloud
run/api/cloud:
	go run ./cmd/api -port=${port} -db-dsn=${KONNICHIWAH_DB_DSN} -llm=cloud -gemini-api-key=${GEMINI_API_KEY} -groq-api-key=${GROQ_API_KEY}

## db/psql: connect to the database using psql
.PHONY: db/psql
db/psql:
	psql ${KONNICHIWAH_DB_DSN}

## db/migrations/new name=$1: create a new database migration
.PHONY: db/migrations/new
db/migrations/new:
	@echo 'Creating migration files for ${name}...'
	migrate create -seq -ext=.sql -dir=./migrations ${name}

## db/migrations/up: apply all up database migrations
.PHONY: db/migrations/up
db/migrations/up: confirm
	@echo 'Running up migrations...'
	migrate -path ./migrations -database ${KONNICHIWAH_DB_DSN} up

## db/seed: load the etiquette rulebook and the OpenStreetMap places into the database
.PHONY: db/seed
db/seed:
	@echo 'Seeding etiquette rules...'
	go run ./cmd/seed -db-dsn=${KONNICHIWAH_DB_DSN}

## osm/fetch: refresh osmdata/places.json from OpenStreetMap (slow: a few minutes)
.PHONY: osm/fetch
osm/fetch:
	go run ./cmd/osmfetch

# ==================================================================================== #
# QUALITY CONTROL
# ==================================================================================== #

## tidy: tidy module dependencies and format all .go files
.PHONY: tidy
tidy:
	@echo 'Tidying module dependencies...'
	go mod tidy
	@echo 'Formatting .go files...'
	go fmt ./...

## audit: run quality control checks
.PHONY: audit
audit:
	@echo 'Checking module dependencies...'
	go mod tidy -diff
	go mod verify
	@echo 'Vetting code...'
	go vet ./...
	@echo 'Running tests...'
	go test -race -vet=off ./...

# ==================================================================================== #
# BUILD
# ==================================================================================== #

## build/api: build the cmd/api application
.PHONY: build/api
build/api:
	@echo 'Building cmd/api...'
	go build -ldflags='-s' -o=./bin/api ./cmd/api
	GOOS=linux GOARCH=amd64 go build -ldflags='-s' -o=./bin/linux_amd64/api ./cmd/api
