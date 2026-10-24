package transit

import (
	"context"
	"fmt"
	"strings"
	"time"
)

// Timetable represents upcoming train/transit departures from a station.
type Timetable struct {
	Station    string      `json:"station"`
	StationEn  string      `json:"station_en,omitempty"`
	StationJa  string      `json:"station_ja,omitempty"`
	Lat        float64     `json:"lat"`
	Lng        float64     `json:"lng"`
	Departures []Departure `json:"departures"`
}

// Departure represents a single upcoming departure.
type Departure struct {
	Time         string `json:"time"`          // "15:04" in JST
	MinutesUntil int    `json:"minutes_until"` // minutes from now
	Line         string `json:"line"`          // e.g. "JC Chūō Rapid Line" or "JY Yamanote Line"
	LineCode     string `json:"line_code,omitempty"`
	LineColor    string `json:"line_color,omitempty"` // "#ff5c1e"
	Towards      string `json:"towards"`              // e.g. "Takao"
	Platform     string `json:"platform,omitempty"`   // "JC01" or "3"
	Operator     string `json:"operator,omitempty"`   // "JR East"
	Mode         string `json:"mode"`                 // "train", "subway", "bus", etc.
	RealTime     bool   `json:"real_time"`
}

type stoptimesResponse struct {
	Place struct {
		Name string  `json:"name"`
		Lat  float64 `json:"lat"`
		Lon  float64 `json:"lon"`
	} `json:"place"`
	StopTimes []struct {
		Place struct {
			Name               string  `json:"name"`
			StopID             string  `json:"stopId"`
			StopCode           string  `json:"stopCode"`
			Lat                float64 `json:"lat"`
			Lon                float64 `json:"lon"`
			Departure          string  `json:"departure"`
			ScheduledDeparture string  `json:"scheduledDeparture"`
			Cancelled          bool    `json:"cancelled"`
		} `json:"place"`
		Mode           string `json:"mode"`
		RealTime       bool   `json:"realTime"`
		Headsign       string `json:"headsign"`
		RouteShortName string `json:"routeShortName"`
		RouteLongName  string `json:"routeLongName"`
		RouteColor     string `json:"routeColor"`
		AgencyName     string `json:"agencyName"`
		TripTo         struct {
			Name string `json:"name"`
		} `json:"tripTo"`
	} `json:"stopTimes"`
}

// Timetable fetches the next n departures for a station by coordinates or name.
func (c *Client) Timetable(ctx context.Context, stationName string, lat, lng float64, city string, n int) (*Timetable, error) {
	if n <= 0 {
		n = 10
	}
	if n > 30 {
		n = 30
	}

	// Geocode if lat/lng not provided
	if lat == 0 && lng == 0 {
		if strings.TrimSpace(stationName) == "" {
			return nil, fmt.Errorf("%w: station name or coordinates must be provided", ErrNotFound)
		}
		place, err := c.Geocode(ctx, stationName, CityCentre(city))
		if err != nil {
			return nil, err
		}
		lat = place.Location.Lat
		lng = place.Location.Lng
		if stationName == "" {
			stationName = place.Name
		}
	}

	cacheKey := fmt.Sprintf("tt|%.4f,%.4f|%d", lat, lng, n)
	if v, ok := c.cached(cacheKey); ok {
		return v.(*Timetable), nil
	}

	base := strings.TrimSuffix(c.baseURL, "/v1")
	reqURL := fmt.Sprintf("%s/v6/stoptimes?center=%.5f,%.5f&radius=500&n=%d", base, lat, lng, n)

	var resp stoptimesResponse
	err := c.getURL(ctx, reqURL, &resp)
	if err != nil {
		return nil, err
	}

	// Fallback to radius 1000m if 500m returned no stop times
	if len(resp.StopTimes) == 0 {
		reqURL = fmt.Sprintf("%s/v6/stoptimes?center=%.5f,%.5f&radius=1000&n=%d", base, lat, lng, n)
		_ = c.getURL(ctx, reqURL, &resp)
	}

	enStation, jaStation := splitName(resp.Place.Name)
	if enStation == "" || strings.EqualFold(enStation, "center") {
		enStation = stationName
	}
	if enStation == "" {
		enStation = "Station"
	}

	nowJst := time.Now().In(japan)
	departures := make([]Departure, 0, len(resp.StopTimes))

	for _, st := range resp.StopTimes {
		if st.Place.Cancelled {
			continue
		}

		depStr := st.Place.Departure
		if depStr == "" {
			depStr = st.Place.ScheduledDeparture
		}
		if depStr == "" {
			continue
		}

		t, err := time.Parse(time.RFC3339, depStr)
		if err != nil {
			continue
		}
		jst := t.In(japan)
		diffMins := int(jst.Sub(nowJst).Minutes())
		if diffMins < 0 {
			diffMins = 0
		}

		line, _ := journeyLine(st.RouteShortName, st.RouteLongName)
		if line == "" {
			line = lineName(st.RouteShortName, st.RouteLongName)
		}
		if line == "" {
			line = operatorName(st.AgencyName, st.Mode)
		}
		if line == "" {
			line = "Train"
		}

		lineCodeVal := lineCode(st.RouteShortName)

		lineColorVal := ""
		if st.RouteColor != "" {
			lineColorVal = "#" + strings.ToLower(st.RouteColor)
		}

		towardsVal := towards(st.Headsign)
		if towardsVal == "" {
			towardsVal, _ = splitName(st.TripTo.Name)
		}
		if hasJapaneseRune(towardsVal) && c.Stations != nil {
			if en := c.Stations.EnglishWithin(towardsVal, lat, lng, 500); en != "" {
				towardsVal = en
			}
		}

		departures = append(departures, Departure{
			Time:         jst.Format("15:04"),
			MinutesUntil: diffMins,
			Line:         line,
			LineCode:     lineCodeVal,
			LineColor:    lineColorVal,
			Towards:      towardsVal,
			Platform:     st.Place.StopCode,
			Operator:     operatorName(st.AgencyName, st.Mode),
			Mode:         modeName(st.Mode),
			RealTime:     st.RealTime,
		})
	}

	tt := &Timetable{
		Station:    enStation,
		StationEn:  enStation,
		StationJa:  jaStation,
		Lat:        lat,
		Lng:        lng,
		Departures: departures,
	}

	c.store(cacheKey, tt, 45*time.Second)
	return tt, nil
}
