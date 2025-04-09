from nova_act.nova_act import NovaAct
import boto3
import uuid
import requests
import json

vin_outputs = []

for i in range(1, 2):

    with NovaAct(starting_page="https://www.truckpaper.com") as nova:
        nova.act("close the signin prompt")
        nova.act("close the cookie prompt")
        nova.act("click on the 'Box Trucks' link")
        nova.act("click on the text description of a random truck on the page. It should have a year in the text.")
        result = nova.act("find the table on the new page. find the value corresponding to the key VIN. Return it.")
        vin_output = result.response
        print(vin_output)
        vin_outputs.append(vin_output)

print(vin_outputs)
