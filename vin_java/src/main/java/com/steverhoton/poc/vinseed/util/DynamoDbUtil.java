package com.steverhoton.poc.vinseed.util;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for DynamoDB operations
 */
public class DynamoDbUtil {

    /**
     * Converts a Java Map to DynamoDB AttributeValue map
     * 
     * @param item The item to convert
     * @return A map of attribute values suitable for DynamoDB
     */
    public static Map<String, AttributeValue> toDynamoDbItem(Map<String, Object> item) {
        Map<String, AttributeValue> dynamoDbItem = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : item.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String) {
                dynamoDbItem.put(key, AttributeValue.builder().s((String) value).build());
            } else if (value instanceof Integer) {
                dynamoDbItem.put(key, AttributeValue.builder().n(value.toString()).build());
            } else if (value instanceof Long) {
                dynamoDbItem.put(key, AttributeValue.builder().n(value.toString()).build());
            } else if (value instanceof Float || value instanceof Double) {
                dynamoDbItem.put(key, AttributeValue.builder().n(value.toString()).build());
            } else if (value instanceof Boolean) {
                dynamoDbItem.put(key, AttributeValue.builder().bool((Boolean) value).build());
            } else if (value == null) {
                dynamoDbItem.put(key, AttributeValue.builder().nul(true).build());
            }
            // Add more type conversions as needed
        }
        
        return dynamoDbItem;
    }
}
