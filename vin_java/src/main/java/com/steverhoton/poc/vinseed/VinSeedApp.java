package com.steverhoton.poc.vinseed;

import com.steverhoton.poc.vinseed.model.VinResponse;
import com.steverhoton.poc.vinseed.util.DynamoDbUtil;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Main application class for VIN seed data generation
 */
@QuarkusMain
public class VinSeedApp implements QuarkusApplication {

    private static final String TABLE_NAME = "unt-units-svc-srhoton";
    private static final String VIN_API_URL = "https://vpic.nhtsa.dot.gov";
    
    @Override
    public int run(String... args) {
        System.out.println("Starting VIN seed data generation...");
        
        // Create DynamoDB client
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        
        // Array of 10 VINs for testing
        List<String> vinOutputs = Arrays.asList(
            "1FDXE45P3YHA12345",
            "1FDXE45P3YHA23456",
            "1FDXE45P3YHA34567",
            "1FDXE45P3YHA45678",
            "1FDXE45P3YHA56789",
            "1FDXE45P3YHA67890",
            "1FDXE45P3YHA78901",
            "1FDXE45P3YHA89012",
            "1FDXE45P3YHA90123",
            "1FDXE45P3YHA01234"
        );
        
        // Read mapping file
        Map<String, Map<String, Object>> mapping = readMappingFile();
        
        // Process each VIN
        for (String vin : vinOutputs) {
            processVin(vin, mapping, dynamoDbClient);
        }
        
        System.out.println("VIN seed data generation completed.");
        return 0;
    }
    
    /**
     * Reads the mapping file
     * 
     * @return The mapping as a nested Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> readMappingFile() {
        try {
            String content = Files.readString(Paths.get("src/main/resources/map.json"));
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(
                content, 
                HashMap.class
            );
        } catch (IOException e) {
            System.err.println("Error reading mapping file: " + e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Process a single VIN
     * 
     * @param vin The VIN to process
     * @param mapping The mapping configuration
     * @param dynamoDbClient The DynamoDB client
     */
    private void processVin(String vin, Map<String, Map<String, Object>> mapping, DynamoDbClient dynamoDbClient) {
        System.out.println("Going to lookup VIN decode for vin " + vin);
        
        // Create a new UUID dictionary for each VIN
        Map<String, String> uuidDict = new HashMap<>();
        
        try {
            // Create REST client for VIN API
            var vinApiClient = RestClientBuilder.newBuilder()
                .baseUri(URI.create(VIN_API_URL))
                .build(com.steverhoton.poc.vinseed.client.VinApiClient.class);
            
            // Make API request for the current VIN
            VinResponse vinResponse = vinApiClient.decodeVin(vin);
            
            // Get the vin_response data
            Map<String, String> vinDetails;
            if (vinResponse.getResults() != null && !vinResponse.getResults().isEmpty()) {
                vinDetails = vinResponse.getResults().get(0);
            } else {
                System.err.println("No results found for VIN: " + vin);
                return;
            }
            
            // Process each top-level key in the mapping
            for (String topLevelKey : mapping.keySet()) {
                Map<String, Object> resultDict = new HashMap<>();
                Map<String, Object> topLevelMapping = mapping.get(topLevelKey);
                
                // Process each field in this top-level mapping
                for (Map.Entry<String, Object> entry : topLevelMapping.entrySet()) {
                    String targetField = entry.getKey();
                    Object sourceInfo = entry.getValue();
                    
                    if (sourceInfo instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sourceInfoMap = (Map<String, Object>) sourceInfo;
                        
                        if (sourceInfoMap.containsKey("field")) {
                            String sourceField = (String) sourceInfoMap.get("field");
                            String value = getNestedValue(vinDetails, sourceField);
                            if (value != null) {
                                resultDict.put(targetField, value);
                            }
                        } else if (sourceInfoMap.containsKey("value")) {
                            resultDict.put(targetField, sourceInfoMap.get("value"));
                        }
                    } else if (sourceInfo instanceof String) {
                        String sourceField = (String) sourceInfo;
                        String value = getNestedValue(vinDetails, sourceField);
                        if (value != null) {
                            resultDict.put(targetField, value);
                        }
                    }
                }
                
                // Print the results for this top-level key
                System.out.println("\n" + topLevelKey.substring(0, 1).toUpperCase() + topLevelKey.substring(1) + ":");
                System.out.println(resultDict);
                
                // Generate a UUID for the PK
                if (!uuidDict.containsKey(topLevelKey)) {
                    uuidDict.put(topLevelKey, UUID.randomUUID().toString());
                }
                
                String pk = uuidDict.get(topLevelKey);
                
                // Create the item to be inserted
                Map<String, Object> item = new HashMap<>();
                item.put("PK", pk);
                item.put("SK", topLevelKey);
                
                // Add all attributes from the resultDict
                item.putAll(resultDict);
                
                // Convert item to DynamoDB format and insert into the DynamoDB table
                try {
                    var dynamoDbItem = DynamoDbUtil.toDynamoDbItem(item);
                    var putItemRequest = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(dynamoDbItem)
                        .build();
                    
                    dynamoDbClient.putItem(putItemRequest);
                    System.out.println("Successfully wrote to DynamoDB. PK: " + pk + ", SK: " + topLevelKey);
                } catch (Exception e) {
                    System.err.println("Error writing to DynamoDB: " + e.getMessage());
                }
            }
            
            // Create relationship records
            createRelationshipRecords(uuidDict, dynamoDbClient);
            
        } catch (Exception e) {
            System.err.println("Error processing VIN " + vin + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates relationship records in DynamoDB
     * 
     * @param uuidDict The UUID dictionary
     * @param dynamoDbClient The DynamoDB client
     */
    private void createRelationshipRecords(Map<String, String> uuidDict, DynamoDbClient dynamoDbClient) {
        // Create relationship records between commercialVehicleType and related types
        List<String> relationshipTypes = Arrays.asList(
            "activeSafetySystemType",
            "engineType",
            "exteriorBodyType",
            "exteriorBusType",
            "exteriorDimensionType",
            "exteriorMotorcycleType",
            "exteriorTrailerType",
            "exteriorTruckType",
            "exteriorWheelType",
            "generalType",
            "interiorSeatType",
            "interiorType",
            "mechanicalBatteryType",
            "mechanicalBrakeType",
            "mechanicalDriveTrainType",
            "mechanicalTransmissionType"
        );
        
        for (String relatedType : relationshipTypes) {
            if (uuidDict.containsKey("commercialVehicleType") && uuidDict.containsKey(relatedType)) {
                String relationshipPk = uuidDict.get("commercialVehicleType") + "#1";
                String relationshipSk = uuidDict.get("commercialVehicleType") + "|" + uuidDict.get(relatedType);
                
                // Generate a random customer ID between 5 and 50000
                int customerId = 5 + new Random().nextInt(49996);
                
                Map<String, Object> relationshipItem = new HashMap<>();
                relationshipItem.put("PK", relationshipPk);
                relationshipItem.put("SK", relationshipSk);
                relationshipItem.put("status", "active");
                relationshipItem.put("customerId", String.valueOf(customerId));
                
                try {
                    var dynamoDbItem = DynamoDbUtil.toDynamoDbItem(relationshipItem);
                    var putItemRequest = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(dynamoDbItem)
                        .build();
                    
                    dynamoDbClient.putItem(putItemRequest);
                    System.out.println("Successfully wrote relationship to DynamoDB. PK: " + relationshipPk + ", SK: " + relationshipSk);
                } catch (Exception e) {
                    System.err.println("Error writing relationship to DynamoDB: " + e.getMessage());
                }
            } else {
                System.out.println("Could not create relationship record between commercialVehicleType and " + relatedType + ": missing required UUIDs");
            }
        }
        
        // Additional relationships for specific active safety system types
        createSafetySystemRelationships(uuidDict, dynamoDbClient);
        
        // Add relationship for mechanicalBatteryChargerType
        createBatteryChargerRelationship(uuidDict, dynamoDbClient);
        
        // Add relationship for passiveSafetySystemAirBagLocation
        createAirBagLocationRelationship(uuidDict, dynamoDbClient);
    }
    
    /**
     * Creates safety system relationship records
     * 
     * @param uuidDict The UUID dictionary
     * @param dynamoDbClient The DynamoDB client
     */
    private void createSafetySystemRelationships(Map<String, String> uuidDict, DynamoDbClient dynamoDbClient) {
        List<String> safetySystemTypes = Arrays.asList(
            "activeSafetySystemSafeDistanceType",
            "activeSafetySystem911Type",
            "activeSafetySystemForwardCollisonType",
            "activeSafetySystemLaneAndSideType",
            "activeSafetySystemLightingType"
        );
        
        for (String safetyType : safetySystemTypes) {
            if (uuidDict.containsKey("commercialVehicleType") && 
                uuidDict.containsKey("activeSafetySystemType") && 
                uuidDict.containsKey(safetyType)) {
                
                String relationshipPk = uuidDict.get("commercialVehicleType") + "#1";
                String relationshipSk = uuidDict.get("commercialVehicleType") + "|" + 
                                       uuidDict.get("activeSafetySystemType") + "|" + 
                                       uuidDict.get(safetyType);
                
                Map<String, Object> relationshipItem = new HashMap<>();
                relationshipItem.put("PK", relationshipPk);
                relationshipItem.put("SK", relationshipSk);
                
                try {
                    var dynamoDbItem = DynamoDbUtil.toDynamoDbItem(relationshipItem);
                    var putItemRequest = PutItemRequest.builder()
                        .tableName(TABLE_NAME)
                        .item(dynamoDbItem)
                        .build();
                    
                    dynamoDbClient.putItem(putItemRequest);
                    System.out.println("Successfully wrote safety system relationship to DynamoDB. PK: " + relationshipPk + ", SK: " + relationshipSk);
                } catch (Exception e) {
                    System.err.println("Error writing safety system relationship to DynamoDB: " + e.getMessage());
                }
            } else {
                System.out.println("Could not create relationship record for " + safetyType + ": missing required UUIDs");
            }
        }
    }
    
    /**
     * Creates battery charger relationship record
     * 
     * @param uuidDict The UUID dictionary
     * @param dynamoDbClient The DynamoDB client
     */
    private void createBatteryChargerRelationship(Map<String, String> uuidDict, DynamoDbClient dynamoDbClient) {
        if (uuidDict.containsKey("commercialVehicleType") && 
            uuidDict.containsKey("mechanicalBatteryType") && 
            uuidDict.containsKey("mechanicalBatteryChargerType")) {
            
            String relationshipPk = uuidDict.get("commercialVehicleType") + "#1";
            String relationshipSk = uuidDict.get("commercialVehicleType") + "|" + 
                                   uuidDict.get("mechanicalBatteryType") + "|" + 
                                   uuidDict.get("mechanicalBatteryChargerType");
            
            Map<String, Object> relationshipItem = new HashMap<>();
            relationshipItem.put("PK", relationshipPk);
            relationshipItem.put("SK", relationshipSk);
            
            try {
                var dynamoDbItem = DynamoDbUtil.toDynamoDbItem(relationshipItem);
                var putItemRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(dynamoDbItem)
                    .build();
                
                dynamoDbClient.putItem(putItemRequest);
                System.out.println("Successfully wrote battery charger relationship to DynamoDB. PK: " + relationshipPk + ", SK: " + relationshipSk);
            } catch (Exception e) {
                System.err.println("Error writing battery charger relationship to DynamoDB: " + e.getMessage());
            }
        } else {
            System.out.println("Could not create relationship record for mechanicalBatteryChargerType: missing required UUIDs");
        }
    }
    
    /**
     * Creates air bag location relationship record
     * 
     * @param uuidDict The UUID dictionary
     * @param dynamoDbClient The DynamoDB client
     */
    private void createAirBagLocationRelationship(Map<String, String> uuidDict, DynamoDbClient dynamoDbClient) {
        if (uuidDict.containsKey("commercialVehicleType") && 
            uuidDict.containsKey("passiveSafetySystemType") && 
            uuidDict.containsKey("passiveSafetySystemAirBagLocation")) {
            
            String relationshipPk = uuidDict.get("commercialVehicleType") + "#1";
            String relationshipSk = uuidDict.get("commercialVehicleType") + "|" + 
                                   uuidDict.get("passiveSafetySystemType") + "|" + 
                                   uuidDict.get("passiveSafetySystemAirBagLocation");
            
            Map<String, Object> relationshipItem = new HashMap<>();
            relationshipItem.put("PK", relationshipPk);
            relationshipItem.put("SK", relationshipSk);
            
            try {
                var dynamoDbItem = DynamoDbUtil.toDynamoDbItem(relationshipItem);
                var putItemRequest = PutItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .item(dynamoDbItem)
                    .build();
                
                dynamoDbClient.putItem(putItemRequest);
                System.out.println("Successfully wrote air bag location relationship to DynamoDB. PK: " + relationshipPk + ", SK: " + relationshipSk);
            } catch (Exception e) {
                System.err.println("Error writing air bag location relationship to DynamoDB: " + e.getMessage());
            }
        } else {
            System.out.println("Could not create relationship record for passiveSafetySystemAirBagLocation: missing required UUIDs");
        }
    }
    
    /**
     * Gets a value from a nested dictionary using dot notation
     * 
     * @param dictionary The dictionary to search in
     * @param keyPath The key path in dot notation
     * @return The value or null if not found
     */
    private String getNestedValue(Map<String, String> dictionary, String keyPath) {
        String[] keys = keyPath.split("\\.");
        Object value = dictionary;
        
        for (String key : keys) {
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                if (map.containsKey(key)) {
                    value = map.get(key);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        }
        
        return value != null ? value.toString() : null;
    }
    
    /**
     * Main entry point
     */
    public static void main(String[] args) {
        io.quarkus.runtime.Quarkus.run(VinSeedApp.class, args);
    }
}
