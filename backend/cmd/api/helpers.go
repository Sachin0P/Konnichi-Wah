package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"

	"github.com/Sachin0P/Konnichi-Wah/backend/internal/validator"
	"github.com/julienschmidt/httprouter"
)

// Define an envelope type. Wrapping every response in a top-level key (like
// {"itinerary": {...}}) makes the JSON self-documenting and lets us add
// metadata alongside the data later without breaking clients.
type envelope map[string]any

// Retrieve the "id" URL parameter from the current request context, then
// convert it to an integer and return it. If the operation isn't successful,
// return 0 and an error.
func (app *application) readIDParam(r *http.Request) (int64, error) {
	params := httprouter.ParamsFromContext(r.Context())

	id, err := strconv.ParseInt(params.ByName("id"), 10, 64)
	if err != nil || id < 1 {
		return 0, errors.New("invalid id parameter")
	}

	return id, nil
}

// Define a writeJSON() helper for sending responses. This takes the destination
// http.ResponseWriter, the HTTP status code to send, the data to encode to JSON,
// and a header map containing any additional HTTP headers we want to include.
func (app *application) writeJSON(w http.ResponseWriter, status int, data envelope, headers http.Header) error {
	// Use json.MarshalIndent() so that the output is easy to read in a terminal
	// while we're developing. The performance cost is small.
	js, err := json.MarshalIndent(data, "", "\t")
	if err != nil {
		return err
	}

	// Append a newline to make it easier to view in terminal applications.
	js = append(js, '\n')

	// Loop through the header map and add each header to the
	// http.ResponseWriter header map. It's OK if the map is nil, because Go
	// doesn't throw an error if you range over a nil map.
	for key, value := range headers {
		w.Header()[key] = value
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	w.Write(js)

	return nil
}

// The readJSON() helper decodes a JSON request body into dst, and turns any
// decoding errors into clear, client-friendly messages.
func (app *application) readJSON(w http.ResponseWriter, r *http.Request, dst any) error {
	// Use http.MaxBytesReader() to limit the size of the request body to 1MB.
	maxBytes := 1_048_576
	r.Body = http.MaxBytesReader(w, r.Body, int64(maxBytes))

	// Initialize the json.Decoder and call DisallowUnknownFields() on it before
	// decoding. This means that if the JSON from the client includes any field
	// which cannot be mapped to the target destination, the decoder returns an
	// error instead of silently ignoring the field.
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()

	err := dec.Decode(dst)
	if err != nil {
		var syntaxError *json.SyntaxError
		var unmarshalTypeError *json.UnmarshalTypeError
		var invalidUnmarshalError *json.InvalidUnmarshalError
		var maxBytesError *http.MaxBytesError

		switch {
		case errors.As(err, &syntaxError):
			return fmt.Errorf("body contains badly-formed JSON (at character %d)", syntaxError.Offset)

		// In some circumstances Decode() may also return an io.ErrUnexpectedEOF
		// error for syntax errors in the JSON.
		case errors.Is(err, io.ErrUnexpectedEOF):
			return errors.New("body contains badly-formed JSON")

		// A json.UnmarshalTypeError occurs when the JSON value is the wrong type
		// for the target destination. If the error relates to a specific field,
		// we include that in the message.
		case errors.As(err, &unmarshalTypeError):
			if unmarshalTypeError.Field != "" {
				return fmt.Errorf("body contains incorrect JSON type for field %q", unmarshalTypeError.Field)
			}
			return fmt.Errorf("body contains incorrect JSON type (at character %d)", unmarshalTypeError.Offset)

		// An io.EOF error is returned if the request body is empty.
		case errors.Is(err, io.EOF):
			return errors.New("body must not be empty")

		// If the JSON contains a field which cannot be mapped to the target
		// destination, Decode() returns an error message in the format "json:
		// unknown field "<name>"". There is no distinct error type for this, so
		// we have to check the message text.
		case strings.HasPrefix(err.Error(), "json: unknown field "):
			fieldName := strings.TrimPrefix(err.Error(), "json: unknown field ")
			return fmt.Errorf("body contains unknown key %s", fieldName)

		// A http.MaxBytesError is returned if the request body exceeded our
		// size limit of 1MB.
		case errors.As(err, &maxBytesError):
			return fmt.Errorf("body must not be larger than %d bytes", maxBytesError.Limit)

		// A json.InvalidUnmarshalError is returned if we pass something that is
		// not a non-nil pointer to Decode(). That's a bug in our code rather
		// than a client error, so we panic.
		case errors.As(err, &invalidUnmarshalError):
			panic(err)

		default:
			return err
		}
	}

	// Call Decode() again, using a pointer to an empty anonymous struct as the
	// destination. If the request body only contained a single JSON value this
	// returns an io.EOF error. If we get anything else, there is additional
	// data in the request body and we return our own custom error message.
	err = dec.Decode(&struct{}{})
	if !errors.Is(err, io.EOF) {
		return errors.New("body must only contain a single JSON value")
	}

	return nil
}

// The readCSV() helper reads a string value from the query string and then
// splits it into a slice on the comma character. If no matching key could be
// found, it returns the provided default value.
func (app *application) readCSV(qs url.Values, key string, defaultValue []string) []string {
	csv := qs.Get(key)

	if csv == "" {
		return defaultValue
	}

	return strings.Split(csv, ",")
}

// The readFloat() helper reads a number from the query string. If the key
// isn't there it returns the default; if the value isn't a number, it records
// an error message in the provided Validator instance.
func (app *application) readFloat(qs url.Values, key string, defaultValue float64, v *validator.Validator) float64 {
	s := qs.Get(key)

	if s == "" {
		return defaultValue
	}

	f, err := strconv.ParseFloat(s, 64)
	if err != nil {
		v.AddError(key, "must be a number")
		return defaultValue
	}

	return f
}

// The readString() helper returns a string value from the query string, or the
// provided default value if no matching key could be found.
func (app *application) readString(qs url.Values, key string, defaultValue string) string {
	// Extract the value for a given key from the query string. If no key exists
	// this will return the empty string "".
	s := qs.Get(key)

	if s == "" {
		return defaultValue
	}

	return s
}
