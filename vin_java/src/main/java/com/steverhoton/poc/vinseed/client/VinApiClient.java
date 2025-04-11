package com.steverhoton.poc.vinseed.client;

import com.steverhoton.poc.vinseed.model.VinResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST client for the NHTSA VIN API
 */
@Path("/api/vehicles")
@RegisterRestClient(configKey = "vin-api")
public interface VinApiClient {

    @GET
    @Path("/decodevinvaluesextended/{vin}")
    @Produces(MediaType.APPLICATION_JSON)
    VinResponse decodeVin(@PathParam("vin") String vin);
}
