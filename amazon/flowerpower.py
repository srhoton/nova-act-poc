from typing import List
from heapq import heapify, heappop


class Flower:

    def flowersPeopleSee (self, flowers: List[List[int]], people: List[int]) -> List[int]:
        result = []
        start = []
        end = []

        sortedPeople = sorted(people)
        for flower in flowers:
            start.append(flower[0])
            end.append(flower[1])
        heapify(start)
        heapify(end)
        count=0
        for person in sortedPeople:
            while start and start[0] <= person:
                count += 1
                heappop(start)
            while end and end[0] < person:
                count -= 1
                heappop(end)
            result.append(count)
        #for person in sortedPeople:
        #    count=0
        #    for flower in flowers:
        #       if (flower[0] <= person and person <= flower[1]):
        #           count += 1
        #    result.append(count)
        return result


runFlower = Flower()
flowers = [[1,4], [2,6], [3,7], [2,6]]
people = [2,3,4,6,8]

print (runFlower.flowersPeopleSee(flowers, people))
