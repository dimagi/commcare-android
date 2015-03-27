#!/usr/bin/python
btn_list = [["start", "@color/cc_attention_positive_color"],
            ["sync", "@color/cc_brand_color"],
            ["savedforms", "@color/cc_light_cool_accent_color"],
            ["incompleteforms", "@color/solid_dark_orange"],
            ["disconnect", "@color/cc_neutral_color"]]

for button in btn_list:
    with open("round_button_template.xml","r") as rbt:
        with open("res/drawable/round_button_" + button[0] + ".xml", "w") as newbutton:
            for line in rbt:
                newbutton.write(line.replace("COLORHERE",button[1]))
