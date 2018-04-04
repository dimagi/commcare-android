import xml.etree.ElementTree as eTree
import re


class VectorDrawable:

    def __init__(self, path_to_drawable_file):
        self.filename = path_to_drawable_file
        self.coordinates_list = []

    def create_list_of_absolute_coordinates(self):
        root = eTree.parse(self.filename).getroot()
        for path in root.findall("path"):
            path_data = path.get("{http://schemas.android.com/apk/res/android}pathData")
            self.coordinates_list.append(get_coords_from_path_data(path_data))

    def find_drawable_bounds(self):
        file = open("test_drawable.txt", "r")
        max_x = -1
        max_y = -1
        min_x = 10000000
        min_y = 10000000
        for line in file:
            without_letter = line[1:].strip('\n')
            coord_pairs = without_letter.split(" ")
            for pair in coord_pairs:
                xy = pair.split(",")
                if (len(xy) != 2):
                    continue
                x = float(xy[0])
                y = float(xy[1])
                # print(x + ", " + y)
                if x > max_x:
                    max_x = x
                if x < min_x:
                    min_x = x
                if y > max_y:
                    max_y = y
                if y < min_y:
                    min_y = y
        print("X bounds: (" + str(min_x) + ", " + str(max_x) + ")")
        print("Y bounds: (" + str(min_y) + ", " + str(max_y) + ")")


delimiters = ['M', 'm', 'Z', 'z', 'L', 'l', 'H', 'h', 'V', 'v', 'C', 'c']


def get_coords_from_path_data(path_data):
    regex_pattern = '|'.join(map(re.escape, delimiters))
    print(re.split(regex_pattern, path_data))
    return []


#drawable = VectorDrawable("sample_drawable.xml")
#drawable.create_list_of_absolute_coordinates()

def find_drawable_bounds():
        file = open("test_drawable.txt", "r")
        max_x = -1
        max_y = -1
        min_x = 10000000
        min_y = 10000000
        for line in file:
            without_letter = line[1:].strip('\n')
            coord_pairs = without_letter.split(" ")
            for pair in coord_pairs:
                xy = pair.split(",")
                if (len(xy) != 2):
                    continue
                x = float(xy[0])
                y = float(xy[1])
                # print(x + ", " + y)
                if x > max_x:
                    max_x = x
                if x < min_x:
                    min_x = x
                if y > max_y:
                    max_y = y
                if y < min_y:
                    min_y = y
        print("X bounds: (" + str(min_x) + ", " + str(max_x) + ")")
        print("Y bounds: (" + str(min_y) + ", " + str(max_y) + ")")

find_drawable_bounds()