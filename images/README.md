# Tips for Creating Image Resources in CommCare

To get started:

- `mkvirtualenv make_drawables`
- `pip install -r requirements.txt`

Test: `python make_drawables.py --test`

## How to create resources

Put the src-images in `./src-images`. They must be `.png` at this time.
(`.svg` and `.9.png` support coming soon)

Put the spec as `.yaml` file for each source image, indicating the sizes
for each destination folder.

## Screen Densities and Scale of Images

Read the Android Developer's article on
(Providing Alternative Resources)[http://developer.android.com/guide/topics/resources/providing-resources.html#AlternativeResources]
to understand what the different dpis and screen sizes mean.

## Stick to the HQ Color Palette When Possible

Read the (HQ Style Guide Article on the Palette)[https://www.commcarehq.org/styleguide/colors/#palette].
