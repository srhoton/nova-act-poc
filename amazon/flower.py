from heapq import heapify
from typing import List
class Flower:
    def flowersInBloom(self, flowers: List[List[int]], people: List[int]) -> List[int]:
        # Sort the flowers based on their start and end times
        flowers.sort(key=lambda x: (x[0], x[1]))
        # Create a list to store the number of flowers in bloom for each person
        result = []
        # Iterate through each person
        for person in people:
            count = 0
            # Check how many flowers are in bloom for the current person
            for flower in flowers:
                if flower[0] <= person <= flower[1]:
                    count += 1
            result.append(count)
        return result

    def steveFlowersInBloom(self, flowers: List[List[int]], people: List[int]) -> List[int]:
        maxLength = 0
        for i in flowers:
            if i[0] > maxLength:
                maxLength = i[0]
            if i[1] > maxLength:
                maxLength = i[1]
        for i in people:
            if i > maxLength:
                maxLength = i
        #print (f"maxLength is {maxLength}")
        sortedPeople = sorted(people)
        flowerCount = [0] * (maxLength + 1)
        result = []
        for i in flowers:
           # print (f"flower {i}")
            j = 0
            while j < len(flowerCount):
                #print (f"i 0 is {i[0]} and i 1 is {i[1]}. j is {j}")
                if i[0] <= j <= i[1]:
                    flowerCount[j] += 1
                j += 1
            #print (f"flowerCount {flowerCount}")
        i = 0
        while i < len(sortedPeople):
            print (f"sortedPeople {sortedPeople}")
            result.append(flowerCount[sortedPeople[i]])
            i += 1
        return result

    def steveFlowersInBloomInAHeap(self, flowers: List[List[int]], people: List[int]) -> List[int]:
        result = []
        sortedPeople = sorted(people)
        start = []
        end = []
        for i in flowers:
            start.append(i[0])
            end.append(i[1])
        heapify(start)
        heapify(end)
        count = 0
        for i in sortedPeople:
            while start and start[0] <= i:
                count += 1
                start.pop(0)
            while end and end[0] < i:
                count -= 1
                end.pop(0)
            result.append(count)

        return result

    def steveFlowersInBloomInALoop(self, flowers: List[List[int]], people: List[int]) -> List[int]:
        result = []
        sortedPeople = sorted(people)
        for person in sortedPeople:
            count = 0
            for flower in flowers:
                if (flower[0] <= person and flower[1] >= person):
                    count += 1
            result.append(count)

        return result

# Example usage
flower = Flower()
flowers = [[1, 4], [2, 3], [4, 6]]
people = [1, 2, 3, 4, 5]
result = flower.flowersInBloom(flowers, people)
print(f"Number of flowers in bloom for each person: {result}")
#steveResult = flower.steveFlowersInBloom(flowers, people)
#print(f"Number of flowers in bloom for each Steve person: {steveResult}")
heapResult = flower.steveFlowersInBloomInAHeap(flowers, people)
print(f"Number of flowers in bloom for each heap person: {heapResult}")
loopResult = flower.steveFlowersInBloomInALoop(flowers, people)
print(f"Number of flowers in bloom for each loop person: {loopResult}")
