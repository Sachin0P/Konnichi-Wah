package main

import (
	"net/http"

	"github.com/julienschmidt/httprouter"
)

func (app *application) routes() http.Handler {
	router := httprouter.New()

	// Convert our own notFoundResponse() and methodNotAllowedResponse() helpers
	// to http.Handlers and set them as the custom error handlers for 404 and
	// 405 responses, so that those errors are sent as JSON too.
	router.NotFound = http.HandlerFunc(app.notFoundResponse)
	router.MethodNotAllowed = http.HandlerFunc(app.methodNotAllowedResponse)

	router.HandlerFunc(http.MethodGet, "/v1/healthcheck", app.healthcheckHandler)

	router.HandlerFunc(http.MethodGet, "/v1/etiquette", app.listEtiquetteRulesHandler)

	// recoverPanic must be the outermost middleware, so that it catches panics
	// in all the middleware and handlers below it.
	return app.recoverPanic(app.enableCORS(app.rateLimit(router)))
}
