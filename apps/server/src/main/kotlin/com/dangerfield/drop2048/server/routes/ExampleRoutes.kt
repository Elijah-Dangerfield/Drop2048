package com.dangerfield.drop2048.server.routes

import com.dangerfield.drop2048.server.domain.ExampleSource
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * `GET /v1/example` — the reference resource. The repeatable shape for a new
 * endpoint:
 *  - one `fun Route.xRoutes(deps)` per resource, dependencies passed in,
 *  - real API paths versioned under `/v1`,
 *  - respond with a DTO (see [ExampleResponse]), never a domain type.
 *
 * Copy this for new endpoints.
 */
fun Route.exampleRoutes(source: ExampleSource) {
    get("/v1/example") {
        call.respond(source.get().toResponse())
    }
}
