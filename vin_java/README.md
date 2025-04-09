# VIN Seed Application

This is a Java 21 / Quarkus implementation of the VIN seed data generator. It processes Vehicle Identification Numbers (VINs) by:

1. Fetching vehicle details from the NHTSA API
2. Mapping the API response to various vehicle component types
3. Storing the data in DynamoDB
4. Creating relationship records between different vehicle components

## Requirements

- Java 21
- Gradle
- AWS credentials configured for DynamoDB access

## Building the application

```bash
./gradlew build
```

## Running the application

```bash
./gradlew quarkusDev
```

## Project Structure

- `VinSeedApp.java` - Main application class
- `VinApiClient.java` - REST client for the NHTSA VIN API
- `VinResponse.java` - Model class for the API response
- `DynamoDbUtil.java` - Utility class for DynamoDB operations
- `map.json` - Mapping configuration file

## Configuration

The application uses the following configuration:
- DynamoDB table name: `unt-units-svc-srhoton`
- NHTSA API URL: `https://vpic.nhtsa.dot.gov`

These can be modified in the application code or configuration files as needed.
