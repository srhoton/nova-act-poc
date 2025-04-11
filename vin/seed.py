from nova_act.nova_act import NovaAct
import boto3
import uuid
import requests
import json

vin_outputs = []

for i in range(1, 2):

    #with NovaAct(starting_page="https://www.truckpaper.com") as nova:
    with NovaAct(starting_page="https://www.truckpaper.com", headless=True) as nova:
        nova.act("close the signin prompt")
        nova.act("close the cookie prompt")
        nova.act("click on the 'Box Trucks' link")
        nova.act(f"click on the text description of the {i} truck on the page. It should have a year in the text.")
        result = nova.act("find the table on the new page. find the value corresponding to the key VIN. Return it.")
        vin_output = result.response
        print(vin_output)
        vin_outputs.append(vin_output)


dynamodb = boto3.client('dynamodb')
table_name = 'unt-units-svc'



try:
    with open('map.json', 'r') as f:
        mapping = json.load(f)
except FileNotFoundError:
    print("map.json file not found.")
    mapping = {}
except json.JSONDecodeError:
    print("map.json is not a valid JSON file.")
    mapping = {}

# Function to get value from nested dictionary using dot notation
def get_nested_value(dictionary, key_path):
    keys = key_path.split('.')
    value = dictionary
    for key in keys:
        if isinstance(value, dict) and key in value:
            value = value[key]
        else:
            return None
    return value

# Function to convert Python value to DynamoDB AttributeValue
def to_dynamodb_item(item):
    dynamodb_item = {}
    for key, value in item.items():
        if isinstance(value, str):
            dynamodb_item[key] = {'S': value}
        elif isinstance(value, int):
            dynamodb_item[key] = {'N': str(value)}
        elif isinstance(value, float):
            dynamodb_item[key] = {'N': str(value)}
        elif isinstance(value, bool):
            dynamodb_item[key] = {'BOOL': value}
        elif value is None:
            dynamodb_item[key] = {'NULL': True}
        # Add more type conversions as needed
    return dynamodb_item

# Process each VIN in the array
for vin_output in vin_outputs:
    print(f"Going to lookup VIN decode for vin {vin_output}")

    # Create a new UUID dictionary for each VIN
    uuid_dict = {}

    # Make API request for the current VIN
    vin_response = requests.get(f"https://vpic.nhtsa.dot.gov/api/vehicles/decodevinvaluesextended/{vin_output}?format=json")
    #print(json.dumps(vin_response.json(), indent=4))

    # Get the vin_response data
    vin_data = vin_response.json()

    # Check if 'Results' is in vin_data (common in NHTSA API responses)
    if 'Results' in vin_data and isinstance(vin_data['Results'], list) and vin_data['Results']:
        vin_details = vin_data['Results'][0]  # Take the first result
    else:
        vin_details = vin_data

    # Extract the required fields for commercialVehicleType based on the mapping
    commercial_vehicle = {}

    # Process each top-level key in the mapping
    for top_level_key in mapping:
        # Create a dictionary for this top-level key
        result_dict = {}

        # Process each field in this top-level mapping
        for target_field, source_info in mapping[top_level_key].items():
            if isinstance(source_info, dict):
                if 'field' in source_info:
                    source_field = source_info['field']
                    value = get_nested_value(vin_details, source_field)
                    if value is not None:
                        result_dict[target_field] = value
                elif 'value' in source_info:
                    result_dict[target_field] = source_info['value']
            elif isinstance(source_info, str):  # Simple mapping field -> field
                value = get_nested_value(vin_details, source_info)
                if value is not None:
                    result_dict[target_field] = value

        # Print the results for this top-level key
        print(f"\n{top_level_key.capitalize()}:")
        print(json.dumps(result_dict, indent=4))

        # Save each top level key data to DynamoDB
        # Generate a UUID for the PK
        # Create or update UUID dictionary where top_level_key is the key
        if 'uuid_dict' not in locals():
            uuid_dict = {}

        # Get existing UUID or create a new one for this top_level_key
        if top_level_key not in uuid_dict:
            uuid_dict[top_level_key] = str(uuid.uuid4())

        pk = uuid_dict[top_level_key]

        # Generate a random customer ID between 5 and 50000
        customer_id = 5 + (int(uuid.uuid4().hex, 16) % 49996)

        # Create the item to be inserted
        item = {
            'PK': pk,
            'SK': top_level_key,
            'status': 'active',
            'customerId': str(customer_id)
        }

        # Add all attributes from the result_dict
        for key, value in result_dict.items():
            item[key] = value

        # Convert item to DynamoDB format and insert into the DynamoDB table
        try:
            dynamodb_item = to_dynamodb_item(item)
            response = dynamodb.put_item(
                TableName=table_name,
                Item=dynamodb_item
            )
            print(f"Successfully wrote to DynamoDB. PK: {pk}, SK: {top_level_key}")
        except Exception as e:
            print(f"Error writing to DynamoDB: {e}")

    # Create relationship records between commercialVehicleType and related types
    # Check and create relationships for each required type
    relationship_types = [
        'activeSafetySystemType',
        'engineType',
        'exteriorBodyType',
        'exteriorBusType',
        'exteriorDimensionType',
        'exteriorMotorcycleType',
        'exteriorTrailerType',
        'exteriorTruckType',
        'exteriorWheelType',
        'generalType',
        'interiorSeatType',
        'interiorType',
        'mechanicalBatteryType',
        'mechanicalBrakeType',
        'mechanicalDriveTrainType',
        'mechanicalTransmissionType'
    ]

    for related_type in relationship_types:
        if 'commercialVehicleType' in uuid_dict and related_type in uuid_dict:
            relationship_pk = f"{uuid_dict['commercialVehicleType']}#1"
            relationship_sk = f"{uuid_dict['commercialVehicleType']}|{uuid_dict[related_type]}"


            relationship_item = {
                'PK': relationship_pk,
                'SK': relationship_sk
            }

            try:
                dynamodb_item = to_dynamodb_item(relationship_item)
                response = dynamodb.put_item(
                    TableName=table_name,
                    Item=dynamodb_item
                )
                print(f"Successfully wrote relationship to DynamoDB. PK: {relationship_pk}, SK: {relationship_sk}")
            except Exception as e:
                print(f"Error writing relationship to DynamoDB: {e}")
        else:
            print(f"Could not create relationship record between commercialVehicleType and {related_type}: missing required UUIDs")

    # Additional relationships for specific active safety system types
    safety_system_types = [
        'activeSafetySystemSafeDistanceType',
        'activeSafetySystem911Type',
        'activeSafetySystemForwardCollisonType',
        'activeSafetySystemLaneAndSideType',
        'activeSafetySystemLightingType'
    ]

    for safety_type in safety_system_types:
        if 'commercialVehicleType' in uuid_dict and 'activeSafetySystemType' in uuid_dict and safety_type in uuid_dict:
            relationship_pk = f"{uuid_dict['commercialVehicleType']}#1"
            relationship_sk = f"{uuid_dict['commercialVehicleType']}|{uuid_dict['activeSafetySystemType']}|{uuid_dict[safety_type]}"

            relationship_item = {
                'PK': relationship_pk,
                'SK': relationship_sk
            }

            try:
                dynamodb_item = to_dynamodb_item(relationship_item)
                response = dynamodb.put_item(
                    TableName=table_name,
                    Item=dynamodb_item
                )
                print(f"Successfully wrote safety system relationship to DynamoDB. PK: {relationship_pk}, SK: {relationship_sk}")
            except Exception as e:
                print(f"Error writing safety system relationship to DynamoDB: {e}")
        else:
            print(f"Could not create relationship record for {safety_type}: missing required UUIDs")

    # Add relationship for mechanicalBatteryChargerType with mechanicalBatteryType as the middle UUID
    if 'commercialVehicleType' in uuid_dict and 'mechanicalBatteryType' in uuid_dict and 'mechanicalBatteryChargerType' in uuid_dict:
        relationship_pk = f"{uuid_dict['commercialVehicleType']}#1"
        relationship_sk = f"{uuid_dict['commercialVehicleType']}|{uuid_dict['mechanicalBatteryType']}|{uuid_dict['mechanicalBatteryChargerType']}"

        relationship_item = {
            'PK': relationship_pk,
            'SK': relationship_sk
        }

        try:
            dynamodb_item = to_dynamodb_item(relationship_item)
            response = dynamodb.put_item(
                TableName=table_name,
                Item=dynamodb_item
            )
            print(f"Successfully wrote battery charger relationship to DynamoDB. PK: {relationship_pk}, SK: {relationship_sk}")
        except Exception as e:
            print(f"Error writing battery charger relationship to DynamoDB: {e}")
    else:
        print("Could not create relationship record for mechanicalBatteryChargerType: missing required UUIDs")

    # Add relationship for passiveSafetySystemAirBagLocation with passiveSafetySystemType as the middle UUID
    if 'commercialVehicleType' in uuid_dict and 'passiveSafetySystemType' in uuid_dict and 'passiveSafetySystemAirBagLocation' in uuid_dict:
        relationship_pk = f"{uuid_dict['commercialVehicleType']}#1"
        relationship_sk = f"{uuid_dict['commercialVehicleType']}|{uuid_dict['passiveSafetySystemType']}|{uuid_dict['passiveSafetySystemAirBagLocation']}"

        relationship_item = {
            'PK': relationship_pk,
            'SK': relationship_sk
        }

        try:
            dynamodb_item = to_dynamodb_item(relationship_item)
            response = dynamodb.put_item(
                TableName=table_name,
                Item=dynamodb_item
            )
            print(f"Successfully wrote air bag location relationship to DynamoDB. PK: {relationship_pk}, SK: {relationship_sk}")
        except Exception as e:
            print(f"Error writing air bag location relationship to DynamoDB: {e}")
    else:
        print("Could not create relationship record for passiveSafetySystemAirBagLocation: missing required UUIDs")
